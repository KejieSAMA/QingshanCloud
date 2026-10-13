package com.x.cloudnext.android.data.trash

import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.data.drive.BatchOperationResult
import com.x.cloudnext.android.data.drive.DriveItem
import com.x.cloudnext.android.data.drive.DriveRepository
import com.x.cloudnext.android.data.drive.toDomain
import com.x.cloudnext.android.network.api.TrashApi
import com.x.cloudnext.android.network.model.DriveBatchIdsRequest
import com.x.cloudnext.android.network.runCatchingApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 回收站仓库。无 Room 缓存 —— 回收站不是热门数据，按需远程拉，避免污染 drive_cache。
 *
 * 写操作（restore / purge / clear）成功后调 [DriveRepository.refreshFolder]（根目录）
 * 让 MyFiles 实时显示恢复的项目。
 */
@Singleton
class TrashRepository @Inject constructor(
    private val trashApi: TrashApi,
    private val driveRepository: DriveRepository,
) {

    suspend fun query(
        keyword: String = "",
        filter: TrashFilter = TrashFilter.All,
        page: Int = 1,
        size: Int = 20,
    ): CnResult<TrashPage> = runCatchingApi {
        val response = trashApi.queryItems(
            keyword = keyword,
            itemType = filter.itemType,
            page = page,
            size = size,
        )
        TrashPage(
            items = response.items.map { it.toDomain() },
            page = response.page,
            size = response.size,
            total = response.total,
            totalPages = response.totalPages,
            hasNext = response.hasNext,
        )
    }

    /** 单项恢复。 */
    suspend fun restoreSingle(id: String): CnResult<DriveItem> = runCatchingApi {
        val restored = trashApi.restore(id)
        refreshDriveRootSilent()
        restored.toDomain()
    }

    /** 批量恢复。 */
    suspend fun restore(ids: List<String>): CnResult<BatchOperationResult> = runCatchingApi {
        val response = trashApi.restoreBatch(DriveBatchIdsRequest(itemIds = ids))
        refreshDriveRootSilent()
        response.toDomain()
    }

    /** 批量彻底删除。 */
    suspend fun purge(ids: List<String>): CnResult<BatchOperationResult> = runCatchingApi {
        trashApi.purgeBatch(DriveBatchIdsRequest(itemIds = ids)).toDomain()
    }

    /** 清空回收站。返回受影响数量。 */
    suspend fun clearAll(): CnResult<BatchOperationResult> = runCatchingApi {
        trashApi.clear().toDomain()
    }

    /**
     * restore 后用户可能不在根目录；保险起见刷新根。
     * 失败不抛错 —— 主操作已经成功。
     */
    private suspend fun refreshDriveRootSilent() {
        runCatching { driveRepository.refreshFolder(null) }
    }
}
