package com.x.cloudnext.android.data.link

import com.x.cloudnext.android.common.CnError
import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.network.api.LinkApi
import com.x.cloudnext.android.network.model.DrivePublicLinkCreateRequest
import com.x.cloudnext.android.network.model.DrivePublicLinkResponse
import com.x.cloudnext.android.network.model.DrivePublicLinkUpdateRequest
import com.x.cloudnext.android.network.runCatchingApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 外链管理仓库。无缓存（数据量小 + 时效性强）。
 *
 * 后端 cloudnext.iteration.public-links.enabled=false 时所有 endpoint 返回 404。
 * [listMine] 会把 404 转成 [LinkFeatureState.Disabled]，UI 据此显示「外链功能未启用」。
 * 其它 endpoint 则直接抛 Business(404) 由调用方处理。
 */
@Singleton
class LinkRepository @Inject constructor(
    private val linkApi: LinkApi,
) {

    /**
     * 列表 + 功能可用性探测。第一调用时如遇 404，识别为后端未启用外链功能。
     */
    suspend fun listMine(): CnResult<LinkFeatureState> {
        return when (val r = runCatchingApi { linkApi.listMine() }) {
            is CnResult.Success -> CnResult.Success(
                LinkFeatureState.Available(r.data.map { it.toDomain() }),
            )
            is CnResult.Failure -> {
                // 跨模块 public 属性不能直接 smart cast，提取局部变量
                val err = r.error
                if (err is CnError.Business && err.status == 404) {
                    CnResult.Success(LinkFeatureState.Disabled)
                } else {
                    CnResult.Failure(err)
                }
            }
        }
    }

    suspend fun create(
        itemId: String,
        permission: LinkPermission,
        password: String? = null,
        downloadLimit: Long? = null,
        expiresAt: String? = null,
    ): CnResult<DrivePublicLink> = runCatchingApi {
        val request = DrivePublicLinkCreateRequest(
            itemId = itemId,
            permission = permission.raw,
            password = password,
            downloadLimit = downloadLimit,
            expiresAt = expiresAt,
        )
        linkApi.create(request).toDomain()
    }

    /**
     * 更新外链。所有参数 null 表示「不更新」。password 传 "" 表示清除密码；
     * expiresAt 传 "" 表示清除有效期（永久）。
     */
    suspend fun update(
        id: String,
        permission: LinkPermission? = null,
        password: String? = null,
        downloadLimit: Long? = null,
        expiresAt: String? = null,
    ): CnResult<DrivePublicLink> = runCatchingApi {
        val request = DrivePublicLinkUpdateRequest(
            permission = permission?.raw,
            password = password,
            downloadLimit = downloadLimit,
            expiresAt = expiresAt,
        )
        linkApi.update(id, request).toDomain()
    }

    suspend fun revoke(id: String): CnResult<DrivePublicLink> = runCatchingApi {
        linkApi.revoke(id).toDomain()
    }

    suspend fun accessLogs(
        id: String,
        page: Int = 1,
        size: Int = 20,
    ): CnResult<LinkAccessLogsPage> = runCatchingApi {
        val resp = linkApi.accessLogs(id, page = page, size = size)
        val logs = resp.logs
        LinkAccessLogsPage(
            totalCount = resp.totalCount,
            successCount = resp.successCount,
            failCount = resp.failCount,
            logs = logs?.content?.map { dto ->
                LinkAccessLog(
                    id = dto.id.orEmpty(),
                    actionType = dto.actionType.orEmpty(),
                    success = dto.success,
                    failReason = dto.failReason,
                    ipAddress = dto.ipAddress,
                    userAgent = dto.userAgent,
                    attemptedAt = dto.attemptedAt,
                )
            } ?: emptyList(),
            pageIndex = logs?.number ?: 0,
            pageSize = logs?.size ?: size,
            totalPages = logs?.totalPages ?: 0,
            hasNext = logs?.last?.not() ?: false,
        )
    }

    private fun DrivePublicLinkResponse.toDomain(): DrivePublicLink = DrivePublicLink(
        id = id,
        token = token,
        itemId = itemId,
        fileName = fileName,
        permission = LinkPermission.parse(permission),
        requirePassword = requirePassword,
        expiresAt = expiresAt?.takeIf { it.isNotBlank() },
        downloadLimit = downloadLimit,
        downloadCount = downloadCount,
        revoked = revoked,
        createdAt = createdAt,
        updatedAt = updatedAt,
        shareUrl = shareUrl?.takeIf { it.isNotBlank() },
    )
}

/**
 * 把后端可能返回的相对 URL（"/{token}"）补全成绝对 URL。
 * 客户端拷贝时调用。
 */
fun resolveShareUrl(shareUrl: String?, apiBaseUrl: String): String? {
    if (shareUrl.isNullOrBlank()) return null
    if (shareUrl.startsWith("http://") || shareUrl.startsWith("https://")) {
        return shareUrl
    }
    return apiBaseUrl.trimEnd('/') + shareUrl
}
