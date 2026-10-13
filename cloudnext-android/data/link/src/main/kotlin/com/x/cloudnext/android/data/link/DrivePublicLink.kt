package com.x.cloudnext.android.data.link

/**
 * 外链权限。后端枚举 PublicLinkPermission：PREVIEW / DOWNLOAD。
 */
enum class LinkPermission(val raw: String, val label: String) {
    Preview("PREVIEW", "仅预览"),
    Download("DOWNLOAD", "可预览可下载"),
    ;

    companion object {
        fun parse(raw: String?): LinkPermission = when (raw?.uppercase()) {
            "DOWNLOAD" -> Download
            else -> Preview
        }
    }
}

/**
 * 外链状态。客户端按 revoked + downloadCount + expiresAt 派生。
 */
enum class LinkStatus(val label: String) {
    Active("有效"),
    Expired("已过期"),
    Consumed("已用完"),
    Revoked("已失效"),
}

/**
 * 外链领域模型。
 */
data class DrivePublicLink(
    val id: String,
    val token: String?,
    val itemId: String?,
    val fileName: String?,
    val permission: LinkPermission,
    val requirePassword: Boolean,
    val expiresAt: String?,
    /** -1 = 不限 */
    val downloadLimit: Long,
    val downloadCount: Long,
    val revoked: Boolean,
    val createdAt: String?,
    val updatedAt: String?,
    /** 后端可能返回相对 "/{token}" 或绝对 URL；UI 层用 [resolveShareUrl] 补全 */
    val shareUrl: String?,
) {
    val status: LinkStatus
        get() = when {
            revoked -> LinkStatus.Revoked
            downloadLimit > 0 && downloadCount >= downloadLimit -> LinkStatus.Consumed
            else -> LinkStatus.Active
            // 过期判断需要解析 expiresAt，UI 层做。
        }
}

/**
 * 单条访问日志。
 */
data class LinkAccessLog(
    val id: String,
    val actionType: String,
    val success: Boolean,
    val failReason: String?,
    val ipAddress: String?,
    val userAgent: String?,
    val attemptedAt: String?,
)

/**
 * 访问日志分页（含顶部聚合 counter）。
 */
data class LinkAccessLogsPage(
    val totalCount: Long,
    val successCount: Long,
    val failCount: Long,
    val logs: List<LinkAccessLog>,
    /** Spring Page number 字段，0-起 */
    val pageIndex: Int,
    val pageSize: Int,
    val totalPages: Int,
    val hasNext: Boolean,
)

/**
 * 外链功能可用性。LinkRepository.listMine 返回该结果，UI 据此显示「未启用」提示或列表。
 */
sealed interface LinkFeatureState {
    data class Available(val links: List<DrivePublicLink>) : LinkFeatureState

    /** 后端 ConditionalOnProperty cloudnext.iteration.public-links.enabled=false → 全部 endpoint 404 */
    data object Disabled : LinkFeatureState
}
