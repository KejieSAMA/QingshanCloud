package com.x.cloudnext.android.network.api

import com.x.cloudnext.android.network.model.DriveBatchIdsRequest
import com.x.cloudnext.android.network.model.DriveBatchOperationResponse
import com.x.cloudnext.android.network.model.DriveCreateShareRequest
import com.x.cloudnext.android.network.model.DrivePathNodeResponse
import com.x.cloudnext.android.network.model.DriveShareResponse
import com.x.cloudnext.android.network.model.DriveSharePageResponse
import com.x.cloudnext.android.network.model.DriveUpdateShareRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 共享接口。1:1 映射 cloudnext-server CloudNextDriveShareController（`/api/drive/shares`）。
 *
 * - 创建分享是 POST 根路径，body 为 DriveCreateShareRequest（含 itemId/itemIds + targets + permission + expiresAt）
 * - 列表 incoming / outgoing：GET 子路径，query 形式
 * - 单条 / 批量撤销
 */
interface ShareApi {

    @POST("api/drive/shares")
    suspend fun createShares(@Body request: DriveCreateShareRequest): DriveBatchOperationResponse

    @GET("api/drive/shares/outgoing")
    suspend fun outgoing(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
        @Query("keyword") keyword: String = "",
        @Query("permission") permission: String = "",
        @Query("status") status: String = "",
    ): DriveSharePageResponse

    @GET("api/drive/shares/incoming")
    suspend fun incoming(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
        @Query("keyword") keyword: String = "",
        @Query("permission") permission: String = "",
        @Query("status") status: String = "",
        @Query("shareId") shareId: String = "",
        @Query("parentId") parentId: String = "",
    ): DriveSharePageResponse

    @GET("api/drive/shares/incoming/path")
    suspend fun incomingPath(
        @Query("shareId") shareId: String,
        @Query("itemId") itemId: String? = null,
    ): List<DrivePathNodeResponse>

    @PATCH("api/drive/shares/{id}")
    suspend fun updateShare(
        @Path("id") id: String,
        @Body request: DriveUpdateShareRequest,
    ): DriveShareResponse

    @POST("api/drive/shares/{id}/revoke")
    suspend fun revokeShare(@Path("id") id: String): DriveShareResponse

    @POST("api/drive/shares/revoke")
    suspend fun revokeBatch(@Body request: DriveBatchIdsRequest): DriveBatchOperationResponse
}
