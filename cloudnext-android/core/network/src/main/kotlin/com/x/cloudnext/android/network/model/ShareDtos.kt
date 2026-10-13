package com.x.cloudnext.android.network.model

import kotlinx.serialization.Serializable

/**
 * M5a 共享相关 DTOs。1:1 对应 cloudnext-server `com.x.cloudnext.server.drive`：
 *   DriveCreateShareRequest / DriveShareTargetRequest / DriveShareQueryRequest /
 *   DriveSharePageResponse / DriveShareResponse / DriveUpdateShareRequest
 *
 * 风格遵循 DriveDtos.kt：全部字段 nullable / default，容忍后端 null。
 */

/**
 * 共享目标（人 / 部门）。后端 DriveShareTargetRequest。
 */
@Serializable
data class DriveShareTargetRequest(
    /** "person" | "department" */
    val type: String = "person",
    val id: String = "",
    val name: String = "",
)

/**
 * 创建分享请求。
 *
 * - 单文件创建：itemId 填具体文件 id；itemIds 留空。
 * - 多文件创建：itemIds 列表；itemId 留空字符串。
 * - permission：read / edit（默认 read）
 * - expiresAt：ISO-8601 字符串；空串表示永久
 * - canReshare：是否允许接收方再分享
 */
@Serializable
data class DriveCreateShareRequest(
    val itemId: String = "",
    val itemIds: List<String> = emptyList(),
    val targets: List<DriveShareTargetRequest> = emptyList(),
    val permission: String = "read",
    val expiresAt: String = "",
    val canReshare: Boolean = false,
)

/**
 * 更新分享请求。三字段全部可选，nullable + 空串语义不一样：
 * - permission/expiresAt 空串："不更新"
 * - canReshare null："不更新"；true/false：显式覆盖
 */
@Serializable
data class DriveUpdateShareRequest(
    val permission: String = "",
    val expiresAt: String = "",
    val canReshare: Boolean? = null,
)

/**
 * 分享响应（incoming / outgoing 通用）。后端 DriveShareResponse。
 *
 * - item：被共享的文件项（嵌套 DriveItemResponse，可读取名字 / 类型）
 * - permission：read / edit
 * - status：active / expired / revoked / consumed
 * - effectivePermission：实际生效权限（考虑过期 / 撤销后可能为空）
 * - allowPreview / allowDownload：基于 permission + status 计算的客户端可执行能力
 */
@Serializable
data class DriveShareResponse(
    val id: String,
    val item: DriveItemResponse? = null,
    val ownerPersonOaId: String? = null,
    val ownerName: String? = null,
    val targetType: String? = null,
    val targetId: String? = null,
    val targetName: String? = null,
    val permission: String? = null,
    val expiresAt: String? = null,
    val revoked: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val status: String? = null,
    val shareId: String? = null,
    val effectivePermission: String? = null,
    val allowPreview: Boolean? = null,
    val allowDownload: Boolean? = null,
)

/**
 * 分页分享响应。后端 DriveSharePageResponse（与 DrivePageResponse 同结构但 items 类型不同）。
 */
@Serializable
data class DriveSharePageResponse(
    val items: List<DriveShareResponse> = emptyList(),
    val page: Int = 1,
    val size: Int = 20,
    val total: Long = 0L,
    val totalPages: Int = 0,
    val hasNext: Boolean = false,
)
