package com.x.cloudnext.android.network.api

import com.x.cloudnext.android.network.model.DriveBatchOperationResponse
import com.x.cloudnext.android.network.model.DriveCreateFolderRequest
import com.x.cloudnext.android.network.model.DriveItemResponse
import com.x.cloudnext.android.network.model.DriveMoveRequest
import com.x.cloudnext.android.network.model.DrivePathNodeResponse
import com.x.cloudnext.android.network.model.DrivePreviewResponse
import com.x.cloudnext.android.network.model.DriveRenameRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 我的文件接口。对应 cloudnext-web/src/api/cloudnext.js 的：
 *   getMyDriveItems / getRecentDriveItems / getRecentOpenedDriveItems / getDrivePath
 *   createFolder / renameDriveItem / deleteDriveItem / moveDriveItems
 *
 * 端点 1:1 映射 cloudnext-server CloudNextDriveController。
 */
interface DriveApi {

    @GET("api/drive/me/items")
    suspend fun getMyItems(
        @Query("parentId") parentId: String? = null,
    ): List<DriveItemResponse>

    @GET("api/drive/me/recent")
    suspend fun getRecent(): List<DriveItemResponse>

    @GET("api/drive/me/recent-opened")
    suspend fun getRecentOpened(
        @Query("limit") limit: Int? = null,
    ): List<DriveItemResponse>

    @GET("api/drive/me/path")
    suspend fun getPath(
        @Query("itemId") itemId: String? = null,
    ): List<DrivePathNodeResponse>

    // -------------------- CRUD (M2b) --------------------

    @POST("api/drive/me/folders")
    suspend fun createFolder(@Body request: DriveCreateFolderRequest): DriveItemResponse

    @PATCH("api/drive/items/{id}/name")
    suspend fun rename(
        @Path("id") id: String,
        @Body request: DriveRenameRequest,
    ): DriveItemResponse

    /** 软删：返回更新后的 DriveItemResponse（deleted=true）。客户端不需要响应体。 */
    @DELETE("api/drive/items/{id}")
    suspend fun delete(@Path("id") id: String): DriveItemResponse

    @POST("api/drive/items/move")
    suspend fun move(@Body request: DriveMoveRequest): DriveBatchOperationResponse

    // -------------------- Preview (M4) --------------------

    @GET("api/drive/items/{id}/preview")
    suspend fun getPreview(@Path("id") id: String): DrivePreviewResponse
}
