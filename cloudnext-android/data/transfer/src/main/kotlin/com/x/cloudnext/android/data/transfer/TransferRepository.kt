package com.x.cloudnext.android.data.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.x.cloudnext.android.data.drive.DriveItem
import com.x.cloudnext.android.data.transfer.download.DownloadWorker
import com.x.cloudnext.android.data.transfer.upload.UploadWorker
import com.x.cloudnext.android.database.dao.TransferTaskDao
import com.x.cloudnext.android.datastore.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transferDao: TransferTaskDao,
    private val workManager: WorkManager,
    private val preferences: PreferencesRepository,
) {

    fun observeAll(): Flow<List<TransferTask>> =
        transferDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActive(): Flow<List<TransferTask>> =
        transferDao.observeByStatus(listOf("QUEUED", "RUNNING", "CONFLICT"))
            .map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): TransferTask? = transferDao.byId(id)?.toDomain()

    /**
     * 入队一批上传任务。返回 task id 列表。
     *
     * @param uris SAF / Camera 返回的 URI；上层应已 takePersistableUriPermission（SAF）
     * @param parentId 上传目标父文件夹；null 表示根目录（drive scope = MyFiles 根；workspace scope = rootItemId）
     * @param scope 作用域。默认 Drive（个人盘）；传 Workspace(id) 走工作空间 endpoint
     * @param maxBytes 客户端预校验上限（默认 100MB），超过则跳过该 URI 并返回失败 id（仍写 Room 标 FAILED）
     */
    suspend fun enqueueUploads(
        uris: List<Uri>,
        parentId: String?,
        scope: TransferScope = TransferScope.Drive,
        maxBytes: Long = 100L * 1024L * 1024L,
    ): List<String> {
        val ids = mutableListOf<String>()
        val now = System.currentTimeMillis()
        uris.forEach { uri ->
            val (name, size) = resolveNameAndSize(uri)
            val id = UUID.randomUUID().toString()
            ids += id

            if (size > maxBytes) {
                transferDao.insert(
                    TransferTask(
                        id = id,
                        type = TransferType.UPLOAD,
                        sourceUri = uri.toString(),
                        parentId = parentId.orEmpty(),
                        displayName = name,
                        totalBytes = size,
                        transferredBytes = 0,
                        status = TransferStatus.FAILED,
                        errorMessage = "文件超过 100MB 限制",
                        conflictStrategy = null,
                        outputUri = null,
                        scope = scope,
                        createdAt = now,
                        updatedAt = now,
                    ).toEntity(),
                )
                return@forEach
            }

            transferDao.insert(
                TransferTask(
                    id = id,
                    type = TransferType.UPLOAD,
                    sourceUri = uri.toString(),
                    parentId = parentId.orEmpty(),
                    displayName = name,
                    totalBytes = size,
                    transferredBytes = 0,
                    status = TransferStatus.QUEUED,
                    errorMessage = null,
                    conflictStrategy = null,
                    outputUri = null,
                    scope = scope,
                    createdAt = now,
                    updatedAt = now,
                ).toEntity(),
            )

            enqueueWorker(taskId = id, type = TransferType.UPLOAD)
        }
        return ids
    }

    /**
     * 入队下载。每个 DriveItem 一个任务；下载到 MediaStore Downloads/CloudNext/。
     * 返回创建的 taskId 列表。
     */
    suspend fun enqueueDownloads(items: List<DriveItem>): List<String> {
        val ids = mutableListOf<String>()
        val now = System.currentTimeMillis()
        items.filter { !it.isFolder }.forEach { item ->
            val id = UUID.randomUUID().toString()
            ids += id
            transferDao.insert(
                TransferTask(
                    id = id,
                    type = TransferType.DOWNLOAD,
                    sourceUri = item.id,         // 下载任务的 sourceUri 存 itemId
                    parentId = "",
                    displayName = item.name,
                    totalBytes = item.sizeBytes ?: -1L,
                    transferredBytes = 0,
                    status = TransferStatus.QUEUED,
                    errorMessage = null,
                    conflictStrategy = null,
                    outputUri = null,
                    createdAt = now,
                    updatedAt = now,
                ).toEntity(),
            )
            enqueueWorker(taskId = id, type = TransferType.DOWNLOAD)
        }
        return ids
    }

    suspend fun cancel(taskId: String) {
        workManager.cancelUniqueWork(workName(taskId))
        transferDao.updateStatus(taskId, TransferStatus.CANCELED.name, null, System.currentTimeMillis())
    }

    suspend fun retry(taskId: String) {
        val task = transferDao.byId(taskId) ?: return
        val now = System.currentTimeMillis()
        transferDao.updateStatus(taskId, TransferStatus.QUEUED.name, null, now)
        transferDao.updateProgress(taskId, 0L, now)
        enqueueWorker(taskId, TransferType.valueOf(task.type))
    }

    /**
     * 用户解决冲突：写入策略后重新入队。Abort 视作取消。
     */
    suspend fun resolveConflict(taskId: String, strategy: ConflictStrategy) {
        val now = System.currentTimeMillis()
        if (strategy == ConflictStrategy.Abort) {
            transferDao.updateStatus(taskId, TransferStatus.CANCELED.name, "已取消上传", now)
            return
        }
        transferDao.updateConflictStrategy(taskId, strategy.raw, now)
        transferDao.updateStatus(taskId, TransferStatus.QUEUED.name, null, now)
        transferDao.updateProgress(taskId, 0L, now)
        enqueueWorker(taskId, TransferType.UPLOAD)  // 冲突只发生在 UPLOAD
    }

    suspend fun clearFinished() {
        transferDao.clearFinished()
    }

    private fun enqueueWorker(taskId: String, type: TransferType) {
        val data = Data.Builder()
        val request = when (type) {
            TransferType.UPLOAD -> OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(data.putString(UploadWorker.KEY_TASK_ID, taskId).build())
            TransferType.DOWNLOAD -> OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(data.putString(DownloadWorker.KEY_TASK_ID, taskId).build())
        }
        // M6：读用户偏好「仅 WiFi」决定 NetworkType。
        // runBlocking 在仓库层一次性读 DataStore 快照（首次读约 < 5ms），不会卡 UI。
        // 入队完成后任务再用什么网络与此值无关 —— Worker 启动时会按 Constraints 等待。
        val wifiOnly = runBlocking { preferences.wifiOnlyFlow.first() }
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val built = request
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10L, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(workName(taskId), ExistingWorkPolicy.REPLACE, built)
    }

    private fun resolveNameAndSize(uri: Uri): Pair<String, Long> {
        var name: String = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
            .ifBlank { "upload_${System.currentTimeMillis()}" }
        var size: Long = -1L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) {
                        val n = cursor.getString(nameIdx)
                        if (!n.isNullOrBlank()) name = n
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        return name to size
    }

    private fun workName(taskId: String) = "transfer-$taskId"

    companion object {
        const val WORK_TAG = "cloudnext.transfer"
    }
}
