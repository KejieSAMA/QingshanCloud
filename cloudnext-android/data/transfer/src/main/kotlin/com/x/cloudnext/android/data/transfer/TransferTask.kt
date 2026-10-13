package com.x.cloudnext.android.data.transfer

import com.x.cloudnext.android.database.entity.TransferTaskEntity

enum class TransferType { UPLOAD, DOWNLOAD }

enum class TransferStatus {
    QUEUED,     // 入队等执行
    RUNNING,    // 正在传输
    SUCCEEDED,  // 完成
    FAILED,     // 已失败（用户可重试）
    CONFLICT,   // 同名冲突（等用户解决）
    CANCELED;   // 用户取消

    val isFinal: Boolean get() = this == SUCCEEDED || this == CANCELED
    val isActive: Boolean get() = this == QUEUED || this == RUNNING
}

enum class ConflictStrategy(val raw: String) {
    Abort("abort"),
    KeepBoth("keep_both"),
    Replace("replace"),
}

/**
 * 上传/下载作用域（M5b）。
 * - Drive：默认，走 /api/drive/me/files
 * - Workspace(wsId)：走 /api/workspaces/{wsId}/files
 */
sealed interface TransferScope {
    val raw: String

    data object Drive : TransferScope {
        override val raw: String = "drive"
    }

    data class Workspace(val workspaceId: String) : TransferScope {
        override val raw: String = "workspace:$workspaceId"
    }

    companion object {
        fun parse(raw: String?): TransferScope = when {
            raw.isNullOrBlank() -> Drive
            raw == "drive" -> Drive
            raw.startsWith("workspace:") -> Workspace(raw.removePrefix("workspace:"))
            else -> Drive
        }
    }
}

data class TransferTask(
    val id: String,
    val type: TransferType,
    val sourceUri: String,
    val parentId: String,
    val displayName: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val status: TransferStatus,
    val errorMessage: String?,
    val conflictStrategy: ConflictStrategy?,
    /** 下载完成后的 MediaStore URI（通知 ACTION_VIEW 用）；上传任务始终为 null */
    val outputUri: String?,
    val scope: TransferScope = TransferScope.Drive,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val progressFraction: Float
        get() = if (totalBytes <= 0) 0f else (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

internal fun TransferTaskEntity.toDomain(): TransferTask = TransferTask(
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
    scope = TransferScope.parse(scope),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun TransferTask.toEntity(): TransferTaskEntity = TransferTaskEntity(
    id = id,
    type = type.name,
    sourceUri = sourceUri,
    parentId = parentId,
    displayName = displayName,
    totalBytes = totalBytes,
    transferredBytes = transferredBytes,
    status = status.name,
    errorMessage = errorMessage,
    conflictStrategy = conflictStrategy?.raw,
    outputUri = outputUri,
    scope = scope.raw,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
