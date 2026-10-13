package com.x.cloudnext.android.network.model

import kotlinx.serialization.Serializable

/**
 * M5b 外链相关 DTOs。1:1 对应 cloudnext-server `com.x.cloudnext.server.link`：
 *   DrivePublicLinkCreateRequest / DrivePublicLinkUpdateRequest /
 *   DrivePublicLinkResponse / DrivePublicLinkAccessLogResponse /
 *   DrivePublicLinkAccessLogsPageResponse
 *
 * 注意契约要点（见 server 端 DTO 注释）：
 * - permission：PREVIEW / DOWNLOAD（大小写不敏感，服务端正则化）
 * - downloadLimit：null 等价 -1（不限）；其它须 -1 或 [1, 1_000_000]
 * - password：create 为空串=无密码；update 为 null=不变 / 空串=清除 / 非空=重置
 * - expiresAt：ISO-8601；create 空串=永久；update 为 null=不变 / 空串=清除
 * - update DTO 全字段 nullable → 用 null 区分"不更新"和"清空"
 */

@Serializable
data class DrivePublicLinkCreateRequest(
    val itemId: String,
    val permission: String,
    /** null 或空串 = 无密码；非空 = 后端会哈希存储 */
    val password: String? = null,
    /** null = 走后端默认 (-1 不限)；其它须 -1 或 [1, 1_000_000] */
    val downloadLimit: Long? = null,
    /** ISO-8601；null 或空串 = 永久；非空须未来时刻 */
    val expiresAt: String? = null,
)

@Serializable
data class DrivePublicLinkUpdateRequest(
    /** null = 不更新；非 null = PREVIEW / DOWNLOAD */
    val permission: String? = null,
    /** null = 不更新；"" = 清除密码；非空 = 重置密码 */
    val password: String? = null,
    /** null = 不更新；其它同 Create */
    val downloadLimit: Long? = null,
    /** null = 不更新；"" = 清除有效期；非空须未来时刻 */
    val expiresAt: String? = null,
)

@Serializable
data class DrivePublicLinkResponse(
    val id: String,
    val token: String? = null,
    val itemId: String? = null,
    val fileName: String? = null,
    val permission: String? = null,
    val requirePassword: Boolean = false,
    val expiresAt: String? = null,
    val downloadLimit: Long = -1L,
    val downloadCount: Long = 0L,
    val revoked: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /** 可能是相对 "/{token}" 或绝对 URL；客户端按 http 前缀判断 */
    val shareUrl: String? = null,
)

/**
 * 单条访问日志。后端 actionType 为 access / download；failReason 是 enum.name 字符串。
 */
@Serializable
data class DrivePublicLinkAccessLogResponse(
    val id: String? = null,
    val actionType: String? = null,
    val success: Boolean = false,
    val failReason: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    /** ISO-8601 字符串（Instant 默认序列化） */
    val attemptedAt: String? = null,
)

/**
 * 访问日志分页响应。包装了 Spring Data Page 的常用字段。
 *
 * 后端实际序列化形如：
 *   { totalCount, successCount, failCount,
 *     logs: { content: [...], totalElements, totalPages, number, size, ... } }
 *
 * 内嵌 Spring Page 字段全部 nullable + default，对 Jackson 嵌套结构容错。
 */
@Serializable
data class DrivePublicLinkAccessLogsPageResponse(
    val totalCount: Long = 0L,
    val successCount: Long = 0L,
    val failCount: Long = 0L,
    val logs: SpringPage<DrivePublicLinkAccessLogResponse>? = null,
)

/**
 * 通用 Spring Data Page 包装。只保留 Android 端需要的字段。
 *
 * 注：number 是 0-起页；前端做 +1 转 1-起。
 */
@Serializable
data class SpringPage<T>(
    val content: List<T> = emptyList(),
    val number: Int = 0,
    val size: Int = 20,
    val totalElements: Long = 0L,
    val totalPages: Int = 0,
    val first: Boolean = true,
    val last: Boolean = true,
    val numberOfElements: Int = 0,
    val empty: Boolean = true,
)
