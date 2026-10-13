package com.x.cloudnext.android.network.api

import com.x.cloudnext.android.network.model.DriveItemResponse
import com.x.cloudnext.android.network.model.WorkspaceCreateRequest
import com.x.cloudnext.android.network.model.WorkspaceDetailResponse
import com.x.cloudnext.android.network.model.WorkspaceFolderCreateRequest
import com.x.cloudnext.android.network.model.WorkspaceMemberAddRequest
import com.x.cloudnext.android.network.model.WorkspaceMemberResponse
import com.x.cloudnext.android.network.model.WorkspaceMemberUpdateRequest
import com.x.cloudnext.android.network.model.WorkspaceSummaryResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 工作空间接口。对应 cloudnext-server CloudNextWorkspaceController（前缀 api/workspaces）。
 *
 * 文件 CRUD（重命名 / 移动 / 删除）走通用 endpoint `/api/drive/items/{id}`，
 * 不在本接口里；本接口只覆盖工作空间 scope 特有 endpoint。
 *
 * 文件上传是 multipart endpoint，由 UploadWorker 直接构造 OkHttp 请求 + scope = "workspace:{id}"
 * 派发到 `/api/workspaces/{id}/files`，不通过 Retrofit。
 */
interface WorkspaceApi {

    @GET("api/workspaces")
    suspend fun listMine(): List<WorkspaceSummaryResponse>

    @POST("api/workspaces")
    suspend fun create(@Body request: WorkspaceCreateRequest): WorkspaceDetailResponse

    @GET("api/workspaces/{id}")
    suspend fun get(@Path("id") id: String): WorkspaceDetailResponse

    /**
     * 列工作空间下文件。parentId 为 null 时后端取该工作空间的 rootItemId。
     */
    @GET("api/workspaces/{id}/items")
    suspend fun items(
        @Path("id") id: String,
        @Query("parentId") parentId: String? = null,
    ): List<DriveItemResponse>

    @POST("api/workspaces/{id}/folders")
    suspend fun createFolder(
        @Path("id") id: String,
        @Body request: WorkspaceFolderCreateRequest,
    ): DriveItemResponse

    @GET("api/workspaces/{id}/members")
    suspend fun listMembers(@Path("id") id: String): List<WorkspaceMemberResponse>

    @POST("api/workspaces/{id}/members")
    suspend fun addMember(
        @Path("id") id: String,
        @Body request: WorkspaceMemberAddRequest,
    ): WorkspaceMemberResponse

    @PATCH("api/workspaces/{id}/members/{memberId}")
    suspend fun updateMember(
        @Path("id") id: String,
        @Path("memberId") memberId: String,
        @Body request: WorkspaceMemberUpdateRequest,
    ): WorkspaceMemberResponse

    @DELETE("api/workspaces/{id}/members/{memberId}")
    suspend fun removeMember(
        @Path("id") id: String,
        @Path("memberId") memberId: String,
    )
}
