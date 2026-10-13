package com.x.cloudnext.android.data.drive

import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.database.dao.DriveCacheDao
import com.x.cloudnext.android.network.api.DriveApi
import com.x.cloudnext.android.network.model.DriveCreateFolderRequest
import com.x.cloudnext.android.network.model.DriveMoveRequest
import com.x.cloudnext.android.network.model.DriveRenameRequest
import com.x.cloudnext.android.network.runCatchingApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 我的文件仓库。Cache-first + 远程刷新策略：
 *
 * - [observeFolder]：列表先从 Room 流出（即时显示），再异步调远程，远程成功后覆盖 Room
 *   触发 Flow 更新。失败不影响已显示的缓存。
 * - [refreshFolder]：主动拉远程一次（下拉刷新），返回 CnResult。
 * - [getRecent] / [getRecentOpened]：首页两横列用，不走缓存（数据量小且时效敏感）。
 * - [getPath]：进入子目录后拿面包屑，也是直接走远程（路径不容易变，可后续加 cache）。
 *
 * 写操作（M2b）：[createFolder] / [renameItem] / [deleteItem] / [moveItems] 成功后
 * 主动 invalidate 相关 parentId 缓存（重拉一次），UI 通过 observeFolder 流自动更新。
 */
@Singleton
class DriveRepository @Inject constructor(
    private val driveApi: DriveApi,
    private val driveCacheDao: DriveCacheDao,
) {

    /** 监听某目录的本地缓存。parentId=null 表示根目录。 */
    fun observeFolder(parentId: String?): Flow<List<DriveItem>> {
        val key = parentIdKey(parentId)
        return driveCacheDao.itemsByParent(key).map { entities -> entities.map { it.toDomain() } }
    }

    /** 主动从远程拉一次，写入 Room。 */
    suspend fun refreshFolder(parentId: String?): CnResult<List<DriveItem>> = runCatchingApi {
        val response = driveApi.getMyItems(parentId)
        val key = parentIdKey(parentId)
        val now = System.currentTimeMillis()
        driveCacheDao.replaceParent(key, response.map { it.toEntity(key, now) })
        response.map { it.toDomain() }
    }

    suspend fun getRecent(): CnResult<List<DriveItem>> = runCatchingApi {
        driveApi.getRecent().map { it.toDomain() }
    }

    suspend fun getRecentOpened(limit: Int = 6): CnResult<List<DriveItem>> = runCatchingApi {
        driveApi.getRecentOpened(limit).map { it.toDomain() }
    }

    suspend fun getPath(itemId: String?): CnResult<List<DrivePathSegment>> = runCatchingApi {
        driveApi.getPath(itemId).map { it.toDomain() }
    }

    // -------------------- 写操作 (M2b) --------------------

    /**
     * 新建文件夹。成功后 invalidate parentId 目录。
     * 根目录传 parentId=null（API 层会转空串）。
     */
    suspend fun createFolder(parentId: String?, name: String): CnResult<DriveItem> = runCatchingApi {
        val req = DriveCreateFolderRequest(name = name, parentId = parentId.orEmpty())
        val created = driveApi.createFolder(req)
        // 重新拉父目录覆盖缓存
        refreshParentSilent(parentId)
        created.toDomain()
    }

    /**
     * 重命名。成功后 invalidate 当前所在目录（caller 提供 parentId 以便定位）。
     */
    suspend fun renameItem(
        id: String,
        name: String,
        currentParentId: String?,
    ): CnResult<DriveItem> = runCatchingApi {
        val renamed = driveApi.rename(id, DriveRenameRequest(name = name))
        refreshParentSilent(currentParentId)
        renamed.toDomain()
    }

    /**
     * 软删（移到回收站）。caller 提供 parentId 以便刷新当前列表。
     */
    suspend fun deleteItem(id: String, currentParentId: String?): CnResult<Unit> = runCatchingApi {
        driveApi.delete(id)
        refreshParentSilent(currentParentId)
    }

    /**
     * 批量移动。源 parentId 和目标 parentId 都要 invalidate。
     */
    suspend fun moveItems(
        itemIds: List<String>,
        sourceParentId: String?,
        targetParentId: String?,
        conflictStrategy: String = "abort",
    ): CnResult<BatchOperationResult> = runCatchingApi {
        val req = DriveMoveRequest(
            itemIds = itemIds,
            targetParentId = targetParentId.orEmpty(),
            conflictStrategy = conflictStrategy,
        )
        val response = driveApi.move(req)
        refreshParentSilent(sourceParentId)
        if (targetParentId != sourceParentId) refreshParentSilent(targetParentId)
        response.toDomain()
    }

    /** 失败不抛错，缓存能更新就更新，更新不了也不影响调用者拿到主操作的成功结果。 */
    private suspend fun refreshParentSilent(parentId: String?) {
        runCatching { refreshFolder(parentId) }
    }
}
