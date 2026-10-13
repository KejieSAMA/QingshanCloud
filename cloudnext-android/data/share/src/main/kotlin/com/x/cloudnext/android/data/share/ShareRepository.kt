package com.x.cloudnext.android.data.share

import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.data.drive.BatchOperationResult
import com.x.cloudnext.android.data.drive.toDomain
import com.x.cloudnext.android.network.api.ShareApi
import com.x.cloudnext.android.network.model.DriveBatchIdsRequest
import com.x.cloudnext.android.network.runCatchingApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 分享仓库。无 Room 缓存（列表会随权限/状态变化，按需远程拉）。
 *
 * - [listIncoming] / [listOutgoing]：分页拉取
 * - [createShares]：MyFiles 触发，body 含 itemIds + targets + permission + expiresAt
 * - [updateShare]：单条修改权限 / 有效期
 * - [revokeShare] / [revokeBatch]：撤销
 */
@Singleton
class ShareRepository @Inject constructor(
    private val shareApi: ShareApi,
) {

    suspend fun listIncoming(
        page: Int = 1,
        size: Int = 20,
        keyword: String = "",
        status: String = "",
    ): CnResult<SharePage> = runCatchingApi {
        val response = shareApi.incoming(
            page = page,
            size = size,
            keyword = keyword,
            status = status,
        )
        SharePage(
            items = response.items.map { it.toDomain() },
            page = response.page,
            size = response.size,
            total = response.total,
            totalPages = response.totalPages,
            hasNext = response.hasNext,
        )
    }

    suspend fun listOutgoing(
        page: Int = 1,
        size: Int = 20,
        keyword: String = "",
        status: String = "",
    ): CnResult<SharePage> = runCatchingApi {
        val response = shareApi.outgoing(
            page = page,
            size = size,
            keyword = keyword,
            status = status,
        )
        SharePage(
            items = response.items.map { it.toDomain() },
            page = response.page,
            size = response.size,
            total = response.total,
            totalPages = response.totalPages,
            hasNext = response.hasNext,
        )
    }

    /**
     * 创建分享。一次可对多个文件 + 多个目标人/部门。
     *
     * @param itemIds 被分享的文件 id
     * @param targets 接收方
     * @param permission 权限
     * @param expiresAt ISO-8601 字符串；null 或空表示永久
     * @param canReshare 是否允许接收方再分享
     */
    suspend fun createShares(
        itemIds: List<String>,
        targets: List<ShareTarget>,
        permission: SharePermission,
        expiresAt: String? = null,
        canReshare: Boolean = false,
    ): CnResult<BatchOperationResult> = runCatchingApi {
        val request = buildCreateRequest(itemIds, targets, permission, expiresAt, canReshare)
        shareApi.createShares(request).toDomain()
    }

    /**
     * 更新分享。permission/expiresAt null 表示不更新；canReshare null 同理。
     */
    suspend fun updateShare(
        id: String,
        permission: SharePermission? = null,
        expiresAt: String? = null,
        canReshare: Boolean? = null,
    ): CnResult<DriveShare> = runCatchingApi {
        val request = buildUpdateRequest(permission, expiresAt, canReshare)
        shareApi.updateShare(id, request).toDomain()
    }

    suspend fun revokeShare(id: String): CnResult<DriveShare> = runCatchingApi {
        shareApi.revokeShare(id).toDomain()
    }

    suspend fun revokeBatch(ids: List<String>): CnResult<BatchOperationResult> = runCatchingApi {
        shareApi.revokeBatch(DriveBatchIdsRequest(itemIds = ids)).toDomain()
    }
}
