package com.x.cloudnext.android.network.api

import com.x.cloudnext.android.network.model.DrivePublicLinkAccessLogsPageResponse
import com.x.cloudnext.android.network.model.DrivePublicLinkCreateRequest
import com.x.cloudnext.android.network.model.DrivePublicLinkResponse
import com.x.cloudnext.android.network.model.DrivePublicLinkUpdateRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 外链管理接口。对应 cloudnext-server CloudNextLinkController（前缀 api/links）。
 *
 * 注意：后端 ConditionalOnProperty cloudnext.iteration.public-links.enabled=true 才注册
 * 该 controller。当 flag 关闭时所有 endpoint 返回 404。Android 端用 404 识别 "功能未启用"
 * 业务态，UI 显示提示而不是错误页。
 */
interface LinkApi {

    @POST("api/links")
    suspend fun create(@Body request: DrivePublicLinkCreateRequest): DrivePublicLinkResponse

    @GET("api/links")
    suspend fun listMine(): List<DrivePublicLinkResponse>

    @PATCH("api/links/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body request: DrivePublicLinkUpdateRequest,
    ): DrivePublicLinkResponse

    @POST("api/links/{id}/revoke")
    suspend fun revoke(@Path("id") id: String): DrivePublicLinkResponse

    /**
     * 访问日志（owner only）。返回 totalCount/successCount/failCount + Spring Page 包装的 logs。
     *
     * page 1-起；后端会减 1 转 0-起的 Pageable。
     */
    @GET("api/links/{id}/access-logs")
    suspend fun accessLogs(
        @Path("id") id: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
    ): DrivePublicLinkAccessLogsPageResponse
}
