package com.x.cloudnext.android.data.transfer.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.x.cloudnext.android.data.transfer.ConflictStrategy
import com.x.cloudnext.android.data.transfer.TransferNotification
import com.x.cloudnext.android.data.transfer.TransferStatus
import com.x.cloudnext.android.data.transfer.TransferTask
import com.x.cloudnext.android.data.transfer.TransferType
import com.x.cloudnext.android.database.dao.TransferTaskDao
import com.x.cloudnext.android.database.entity.TransferTaskEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException

/**
 * 下载 Worker。从 Room 取任务 → 流式 GET /api/drive/items/{id}/download → MediaStore 落盘。
 *
 * 输入：inputData["taskId"] = task 主键
 * 落盘：Downloads/CloudNext/{name}（API 29+；< 29 直接 FAILED）
 * 完成通知：ACTION_VIEW outputUri 让系统选 App 打开
 *
 * 镜像 UploadWorker 的结构。
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val transferDao: TransferTaskDao,
    private val okHttpClient: OkHttpClient,
    private val config: DownloadWorkerConfig,
    private val downloadSemaphore: DownloadSemaphore,
    private val mediaStoreWriter: MediaStoreWriter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.failure()
        val entity = transferDao.byId(taskId) ?: return@withContext Result.failure()
        if (entity.status == TransferStatus.CANCELED.name) return@withContext Result.success()

        if (!mediaStoreWriter.isSupported) {
            transferDao.updateStatus(
                taskId,
                TransferStatus.FAILED.name,
                "需要 Android 10 (API 29) 及以上",
                System.currentTimeMillis(),
            )
            return@withContext Result.failure()
        }

        val now = System.currentTimeMillis()
        transferDao.updateStatus(taskId, TransferStatus.RUNNING.name, null, now)
        TransferNotification.ensureChannel(applicationContext)
        runCatching {
            val task = transferDao.byId(taskId)!!.toUi()
            setForeground(
                ForegroundInfo(
                    NOTIF_ID_BASE + taskId.hashCode(),
                    TransferNotification.buildProgress(applicationContext, task, config.smallIconRes),
                    // Android 14 强制 FGS 类型；dataSync 对应文件下载场景
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                ),
            )
        }.onFailure { Timber.w(it, "setForeground failed for $taskId") }

        return@withContext try {
            downloadSemaphore.gate.withPermit { performDownload(taskId) }
        } catch (e: IOException) {
            Timber.w(e, "download IO failure for $taskId")
            transferDao.updateStatus(
                taskId,
                TransferStatus.FAILED.name,
                e.message ?: "网络错误",
                System.currentTimeMillis(),
            )
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } catch (e: Throwable) {
            Timber.e(e, "download unexpected failure for $taskId")
            transferDao.updateStatus(
                taskId,
                TransferStatus.FAILED.name,
                e.message ?: e::class.simpleName ?: "未知错误",
                System.currentTimeMillis(),
            )
            Result.failure()
        }
    }

    private suspend fun performDownload(taskId: String): Result {
        val task = transferDao.byId(taskId) ?: return Result.failure()
        // sourceUri 字段对下载任务存的是 itemId
        val itemId = task.sourceUri

        val url = config.baseUrl.trimEnd('/') + "/api/drive/items/" + itemId + "/download"
        val request = Request.Builder().url(url).get().build()

        var lastUpdate = 0L
        var pendingUri: Uri? = null
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = "下载失败（${response.code}）"
                    val now = System.currentTimeMillis()
                    transferDao.updateStatus(taskId, TransferStatus.FAILED.name, msg, now)
                    return if (response.code in 500..599 && runAttemptCount < MAX_RETRIES) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }

                val rawBody = response.body ?: error("响应体为空")
                val contentType = response.header("Content-Type") ?: "application/octet-stream"
                val contentLength = rawBody.contentLength()

                // 客户端预知大小：若 task.totalBytes <= 0 用 Content-Length
                if (task.totalBytes <= 0 && contentLength > 0) {
                    transferDao.updateProgress(taskId, 0L, System.currentTimeMillis())
                }

                // MediaStore 创建 pending URI；失败抛
                val uri = mediaStoreWriter.createPendingUri(task.displayName, contentType)
                pendingUri = uri
                transferDao.updateOutputUri(taskId, uri.toString(), System.currentTimeMillis())

                val progressBody = ProgressResponseBody(rawBody) { transferred, _ ->
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 500L) {
                        lastUpdate = now
                        runCatching {
                            runBlocking {
                                transferDao.updateProgress(taskId, transferred, now)
                            }
                        }
                    }
                }
                // 流式拷贝；不一次性读入内存
                mediaStoreWriter.openOutputStream(uri).use { output ->
                    progressBody.byteStream().use { input ->
                        input.copyTo(output, bufferSize = 8 * 1024)
                    }
                }
                mediaStoreWriter.finish(uri)
            }

            // 成功落盘
            val now = System.currentTimeMillis()
            val refreshed = transferDao.byId(taskId)!!
            val totalForProgress = refreshed.totalBytes.takeIf { it > 0 } ?: refreshed.transferredBytes
            transferDao.updateProgress(taskId, totalForProgress, now)
            transferDao.updateStatus(taskId, TransferStatus.SUCCEEDED.name, null, now)

            // 完成通知：ACTION_VIEW outputUri
            val finalTask = transferDao.byId(taskId)!!.toUi()
            runCatching {
                setForeground(
                    ForegroundInfo(
                        NOTIF_ID_BASE + taskId.hashCode(),
                        TransferNotification.buildDone(
                            applicationContext, finalTask, config.smallIconRes, pendingUri,
                        ),
                        // Android 14 强制 FGS 类型；保持与 progress 通知相同 type
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    ),
                )
            }
            return Result.success()
        } catch (e: Throwable) {
            // 失败或取消：删除 MediaStore 半文件
            pendingUri?.let { mediaStoreWriter.delete(it) }
            throw e
        }
    }

    private fun TransferTaskEntity.toUi(): TransferTask = TransferTask(
        id = id,
        type = TransferType.valueOf(type),
        sourceUri = sourceUri,
        parentId = parentId,
        displayName = displayName,
        totalBytes = totalBytes,
        transferredBytes = transferredBytes,
        status = runCatching { TransferStatus.valueOf(status) }.getOrDefault(TransferStatus.FAILED),
        errorMessage = errorMessage,
        conflictStrategy = conflictStrategy?.let { raw ->
            ConflictStrategy.entries.firstOrNull { it.raw == raw }
        },
        outputUri = outputUri,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        const val KEY_TASK_ID = "taskId"
        const val NOTIF_ID_BASE = 8000  // 与 UploadWorker (9000) 区分
        const val MAX_RETRIES = 3
    }
}

/**
 * Worker 运行时配置：baseUrl + 通知图标。由 :app 模块提供。
 */
data class DownloadWorkerConfig(
    val baseUrl: String,
    val smallIconRes: Int,
)
