package com.x.cloudnext.android.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 传输任务：上传 / 下载（M3b）。
 *
 * 设计：
 * - id 由客户端生成（UUID），与 WorkManager uniqueWork 一一对应
 * - sourceUri 持久化为 String（content://...）；Worker 取出后 Uri.parse
 * - status 文本枚举，便于 SQL 查询和后续扩展（DOWNLOAD 用同一表）
 */
@Entity(
    tableName = "transfer_task",
    indices = [Index(value = ["status"]), Index(value = ["createdAt"])],
)
data class TransferTaskEntity(
    @PrimaryKey val id: String,
    /** "UPLOAD" | "DOWNLOAD"（M3b） */
    val type: String,
    /** 源 URI：上传 = content:// 或 file://；下载 = 后端文件 id */
    val sourceUri: String,
    /** 上传目标 parent；根目录用空串 */
    val parentId: String,
    val displayName: String,
    val totalBytes: Long,
    val transferredBytes: Long = 0L,
    /** QUEUED / RUNNING / SUCCEEDED / FAILED / CONFLICT / CANCELED */
    val status: String,
    val errorMessage: String? = null,
    /** 冲突解决：null=未设 / "keep_both" / "replace"；ABORT 由 null 表达 */
    val conflictStrategy: String? = null,
    /** 下载完成后的 MediaStore URI（通知 PendingIntent ACTION_VIEW 用）；M3b v3 新增 */
    val outputUri: String? = null,
    /**
     * 上传/下载作用域。M5b v4 新增。
     * - "drive"：默认，走 /api/drive/me/files
     * - "workspace:{wsId}"：走 /api/workspaces/{wsId}/files
     */
    val scope: String = "drive",
    val createdAt: Long,
    val updatedAt: Long,
)
