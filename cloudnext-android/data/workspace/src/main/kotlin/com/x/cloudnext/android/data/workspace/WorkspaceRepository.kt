package com.x.cloudnext.android.data.workspace

import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.data.drive.DriveItem
import com.x.cloudnext.android.data.drive.toDomain
import com.x.cloudnext.android.network.api.WorkspaceApi
import com.x.cloudnext.android.network.model.WorkspaceCreateRequest
import com.x.cloudnext.android.network.model.WorkspaceFolderCreateRequest
import com.x.cloudnext.android.network.model.WorkspaceMemberAddRequest
import com.x.cloudnext.android.network.model.WorkspaceMemberUpdateRequest
import com.x.cloudnext.android.network.runCatchingApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工作空间仓库。无 Room 缓存（M5b 简化）—— 列表 / 详情 / 文件 / 成员 全部远程读。
 *
 * 文件 CRUD（rename / delete / move）走通用 :data:drive 的 DriveRepository（scope-aware）。
 * 文件上传走 :data:transfer 的 UploadWorker（M5b-3 扩展 scope 字段）。
 */
@Singleton
class WorkspaceRepository @Inject constructor(
    private val workspaceApi: WorkspaceApi,
) {

    suspend fun listMine(): CnResult<List<WorkspaceSummary>> = runCatchingApi {
        workspaceApi.listMine().map { dto ->
            WorkspaceSummary(
                id = dto.id,
                name = dto.name.orEmpty(),
                status = dto.status,
                myRole = WorkspaceRole.parse(dto.myRole),
                creatorName = dto.creatorName,
                updatedAt = dto.updatedAt,
                memberCount = dto.memberCount,
            )
        }
    }

    suspend fun create(name: String): CnResult<WorkspaceDetail> = runCatchingApi {
        val dto = workspaceApi.create(WorkspaceCreateRequest(name = name))
        dto.toDomain()
    }

    suspend fun getDetail(id: String): CnResult<WorkspaceDetail> = runCatchingApi {
        workspaceApi.get(id).toDomain()
    }

    /**
     * 列工作空间内子节点。parentId null 表示根（后端取 rootItemId）。
     */
    suspend fun listItems(
        workspaceId: String,
        parentId: String? = null,
    ): CnResult<List<DriveItem>> = runCatchingApi {
        workspaceApi.items(workspaceId, parentId).map { it.toDomain() }
    }

    suspend fun createFolder(
        workspaceId: String,
        name: String,
        parentId: String? = null,
    ): CnResult<DriveItem> = runCatchingApi {
        val req = WorkspaceFolderCreateRequest(parentId = parentId.orEmpty(), name = name)
        workspaceApi.createFolder(workspaceId, req).toDomain()
    }

    // -------------------- 成员 --------------------

    suspend fun listMembers(workspaceId: String): CnResult<List<WorkspaceMember>> = runCatchingApi {
        workspaceApi.listMembers(workspaceId).map { it.toDomainMember() }
    }

    suspend fun addMember(
        workspaceId: String,
        personOaId: String,
        personName: String,
        role: WorkspaceRole,
    ): CnResult<WorkspaceMember> = runCatchingApi {
        val req = WorkspaceMemberAddRequest(
            personOaId = personOaId,
            personName = personName,
            role = role.raw,
        )
        workspaceApi.addMember(workspaceId, req).toDomainMember()
    }

    suspend fun updateMemberRole(
        workspaceId: String,
        memberId: String,
        role: WorkspaceRole,
    ): CnResult<WorkspaceMember> = runCatchingApi {
        workspaceApi.updateMember(
            workspaceId,
            memberId,
            WorkspaceMemberUpdateRequest(role = role.raw),
        ).toDomainMember()
    }

    suspend fun removeMember(workspaceId: String, memberId: String): CnResult<Unit> = runCatchingApi {
        workspaceApi.removeMember(workspaceId, memberId)
    }

    // -------------------- Mappers --------------------

    private fun com.x.cloudnext.android.network.model.WorkspaceDetailResponse.toDomain(): WorkspaceDetail =
        WorkspaceDetail(
            id = id,
            name = name.orEmpty(),
            status = status,
            myRole = WorkspaceRole.parse(myRole),
            rootItemId = rootItemId,
            creatorPersonOaId = creatorPersonOaId,
            creatorName = creatorName,
            memberCount = memberCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun com.x.cloudnext.android.network.model.WorkspaceMemberResponse.toDomainMember(): WorkspaceMember =
        WorkspaceMember(
            id = id,
            workspaceId = workspaceId.orEmpty(),
            personOaId = personOaId.orEmpty(),
            personName = personName.orEmpty(),
            role = WorkspaceRole.parse(role),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
