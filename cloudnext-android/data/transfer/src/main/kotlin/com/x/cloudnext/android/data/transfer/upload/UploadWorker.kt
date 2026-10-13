package com.x.cloudnext.android.data.transfer.upload

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.x.cloudnext.android.data.drive.DriveRepository
import com.x.cloudnext.android.data.transfer.ConflictStrategy
import com.x.cloudnext.android.data.transfer.TransferNotification
import com.x.cloudnext.android.data.transfer.TransferScope
import com.x.cloudnext.android.data.transfer.TransferStatus
import com.x.cloudnext.android.data.workspace.WorkspaceRepository
import com.x.cloudnext.android.database.dao.TransferTaskDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException

/**
 * 上传 Worker。从 Room 取任务 → 流式 POST /api/drive/me/files → 处理 200/409/413/其他。
 *
 * 输入：inputData["taskId"] = task 主键
 *
 * Foreground：进入 RUNNING 立即 setForeground(notification)，避免 doze / cgroup 杀掉。
 * 进度节流：每 ≥ 500ms 才更新 Room 一次，避免数据库爆。
 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val transferDao: TransferTaskDao,
    private val okHttpClient: OkHttpClient,
    private val config: UploadWorkerConfig,
    private val uploadSemaphore: UploadSemaphore,
    private val driveRepository: DriveRepository,
    private val workspaceRepository: WorkspaceRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return@withContext Result.failure()
        val entity = transferDao.byId(taskId) ?: return@withContext Result.failure()
        if (entity.status == TransferStatus.CANCELED.name) return@withContext Result.success()

        // 切到 RUNNING + foreground
        val now = System.currentTimeMillis()
        transferDao.updateStatus(taskId, TransferStatus.RUNNING.name, null, now)
        TransferNotification.ensureChannel(applicationContext)
        runCatching {
            val task = transferDao.byId(taskId)!!.toUiTaskForNotif()
            setForeground(
                ForegroundInfo(
                    NOTIF_ID_BASE + taskId.hashCode(),
                    TransferNotification.buildProgress(applicationContext, task, config.smallIconRes),
                    // Android 14 强制 FGS 类型；dataSync 对应文件上传场景
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                ),
            )
        }.onFailure { Timber.w(it, "setForeground failed for $taskId") }

        return@withContext try {
            // 全局并发上限：超出时 acquire 挂起等待。setForeground 已先生效，挂起期间通知仍在。
            uploadSemaphore.gate.withPermit { performUpload(taskId) }
        } catch (e: IOException) {
            // 网络错误：让 WorkManager 退避重试
            Timber.w(e, "upload IO failure for $taskId")
            transferDao.updateStatus(
                taskId,
                TransferStatus.FAILED.name,
                e.message ?: "网络错误",
                System.currentTimeMillis(),
            )
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        } catch (e: Throwable) {
            Timber.e(e, "upload unexpected failure for $taskId")
            transferDao.updateStatus(
                taskId,
                TransferStatus.FAILED.name,
                e.message ?: e::class.simpleName ?: "未知错误",
                System.currentTimeMillis(),
            )
            Result.failure()
        }
    }

    private suspend fun performUpload(taskId: String): Result {
        val task = transferDao.byId(taskId) ?: return Result.failure()
        val uri = Uri.parse(task.sourceUri)
        val scope = TransferScope.parse(task.scope)

        var lastUpdate = 0L
        val onProgress: (Long, Long) -> Unit = { transferred, _ ->
            val now = System.currentTimeMillis()
            if (now - lastUpdate > 500L) {
                lastUpdate = now
                runCatching {
                    // 同步阻塞 IO 更新；coroutine IO dispatcher 上下文已就绪
                    kotlinx.coroutines.runBlocking {
                        transferDao.updateProgress(taskId, transferred, now)
                    }
                }
            }
        }

        val resolver = applicationContext.contentResolver
        val mime = (resolver.getType(uri) ?: "application/octet-stream").toMediaTypeOrNull()

        val fileBody = ProgressRequestBody(
            streamProvider = {
                resolver.openInputStream(uri)
                    ?: throw IOException("无法打开文件流：$uri")
            },
            length = task.totalBytes,
            contentType = mime,
            onProgress = onProgress,
        )

        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", task.displayName, fileBody)
        if (task.parentId.isNotEmpty()) {
            builder.addFormDataPart("parentId", task.parentId)
        }
        task.conflictStrategy?.let { strategy ->
            builder.addFormDataPart("conflictStrategy", strategy)
        }
        // 不传 conflictStrategy 时后端默认 ABORT

        val endpoint = when (scope) {
            is TransferScope.Drive -> "/api/drive/me/files"
            is TransferScope.Workspace -> "/api/workspaces/${scope.workspaceId}/files"
        }
        val request = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + endpoint)
            .post(builder.build())
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val now = System.currentTimeMillis()
            return when (response.code) {
                in 200..299 -> {
                    transferDao.updateProgress(taskId, task.totalBytes, now)
                    transferDao.updateStatus(taskId, TransferStatus.SUCCEEDED.name, null, now)
                    // 自动刷新当前目录：drive scope 走 DriveRepository；workspace scope 走 WorkspaceRepository
                    runCatching {
                        when (scope) {
                            is TransferScope.Drive ->
                                driveRepository.refreshFolder(task.parentId.takeIf { it.isNotEmpty() })
                            is TransferScope.Workspace ->
                                workspaceRepository.listItems(
                                    scope.workspaceId,
                                    task.parentId.takeIf { it.isNotEmpty() },
                                )
                        }
                    }.onFailure { Timber.w(it, "refresh after upload failed") }
                    Result.success()
                }
                409 -> {
                    val msg = response.body?.string()?.takeIf { it.isNotBlank() }
                        ?: "文件已存在"
                    transferDao.updateStatus(taskId, TransferStatus.CONFLICT.name, msg, now)
                    Result.success()  // 用户决策后会重新入队，这里不算失败
                }
                413 -> {
                    transferDao.updateStatus(
                        taskId,
                        TransferStatus.FAILED.name,
                        "文件超过 100MB 限制",
                        now,
                    )
                    Result.failure()
                }
                in 400..499 -> {
                    val msg = parseErrorMessage(response.body?.string())
                        ?: "上传失败（${response.code}）"
                    transferDao.updateStatus(taskId, TransferStatus.FAILED.name, msg, now)
                    Result.failure()
                }
                in 500..599 -> {
                    // 服务端错误：让 WorkManager 退避重试
                    transferDao.updateStatus(
                        taskId,
                        TransferStatus.FAILED.name,
                        "服务器错误（${response.code}）",
                        now,
                    )
                    if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                }
                else -> {
                    transferDao.updateStatus(
                        taskId,
                        TransferStatus.FAILED.name,
                        "未知响应（${response.code}）",
                        now,
                    )
                    Result.failure()
                }
            }
        }
    }

    private fun parseErrorMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        // 后端 413 是 JSON {"message": "..."}，409 是 plain text；先尝试 JSON 解析
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) {
            val match = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(trimmed)
            return match?.groupValues?.get(1)
        }
        return trimmed.take(200)
    }

    private fun com.x.cloudnext.android.database.entity.TransferTaskEntity.toUiTaskForNotif() =
        com.x.cloudnext.android.data.transfer.TransferTask(
            id = id, type = com.x.cloudnext.android.data.transfer.TransferType.valueOf(type),
            sourceUri = sourceUri, parentId = parentId, displayName = displayName,
            totalBytes = totalBytes, transferredBytes = transferredBytes,
            status = TransferStatus.valueOf(status), errorMessage = errorMessage,
            conflictStrategy = conflictStrategy?.let { raw ->
                ConflictStrategy.entries.firstOrNull { it.raw == raw }
            },
            outputUri = outputUri,
            createdAt = createdAt, updatedAt = updatedAt,
        )

    companion object {
        const val KEY_TASK_ID = "taskId"
        const val NOTIF_ID_BASE = 9000
        const val MAX_RETRIES = 3
    }
}

/**
 * 注入到 Worker 的运行时配置：API base URL + 通知图标 res id。
 * 由 :app 模块按 BuildConfig.API_BASE_URL + R.mipmap.ic_launcher 提供。
 */
data class UploadWorkerConfig(
    val baseUrl: String,
    val smallIconRes: Int,
)
