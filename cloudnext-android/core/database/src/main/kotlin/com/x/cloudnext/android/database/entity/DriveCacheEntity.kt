package com.x.cloudnext.android.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 我的文件列表本地缓存。一行 = 一个 DriveItem。
 *
 * 设计：扁平表，按 parentId 索引。根目录用空串 "" 表示（避免 nullable PK 索引问题）。
 * lastSyncAt 记录该 parentId 整目录最近一次刷新的时间戳，用来判断是否过期。
 */
@Entity(
    tableName = "drive_cache",
    indices = [Index(value = ["parentId"]), Index(value = ["lastSyncAt"])],
)
data class DriveCacheEntity(
    @PrimaryKey val id: String,
    /** 根目录用 ""（不用 null，简化查询） */
    val parentId: String,
    val name: String,
    val itemType: String,            // "FOLDER" | "FILE"
    val fileType: String?,           // "folder"/"word"/"pdf"/...
    val contentType: String?,
    val sizeBytes: Long?,
    val createdAt: String?,
    val updatedAt: String?,
    val lastAccessedAt: String?,
    /** 上次远程刷新该 parentId 的时间戳（epoch ms） */
    val lastSyncAt: Long,
)
