package com.x.cloudnext.android.network.api

import com.x.cloudnext.android.network.model.DriveBatchIdsRequest
import com.x.cloudnext.android.network.model.DriveBatchOperationResponse
import com.x.cloudnext.android.network.model.DriveItemResponse
import com.x.cloudnext.android.network.model.DrivePageResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 回收站接口。1:1 映射 cloudnext-server CloudNextDriveController 下 /api/drive/trash 前缀的所有 endpoint。
 *
 * 注意路径前缀是 api/drive/trash/items/...（不是 api/drive/trash）。
 */
interface TrashApi {

    /**
     * 简单列表，不分页。回收站项目一般 < 100；分页查询用 [queryItems]。
     */
    @GET("api/drive/trash/items")
    suspend fun listItems(): List<DriveItemResponse>

    /**
     * 分页 + 过滤查询。所有参数对应后端 DriveQueryRequest。
     *
     * @param itemType "" / "folder" / "file"
     * @param sortBy "deletedAt"（默认） / "name" / "size"
     * @param sortOrder "desc"（默认） / "asc"
     */
    @GET("api/drive/trash/items/query")
    suspend fun queryItems(
        @Query("keyword") keyword: String = "",
        @Query("itemType") itemType: String = "",
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
        @Query("sortBy") sortBy: String = "deletedAt",
        @Query("sortOrder") sortOrder: String = "desc",
    ): DrivePageResponse

    /** 单项恢复。返回更新后的 DriveItemResponse。 */
    @POST("api/drive/trash/items/{id}/restore")
    suspend fun restore(@Path("id") id: String): DriveItemResponse

    /** 批量恢复。 */
    @POST("api/drive/trash/items/restore")
    suspend fun restoreBatch(@Body request: DriveBatchIdsRequest): DriveBatchOperationResponse

    /** 批量彻底删除。 */
    @POST("api/drive/trash/items/purge")
    suspend fun purgeBatch(@Body request: DriveBatchIdsRequest): DriveBatchOperationResponse

    /** 清空回收站。 */
    @POST("api/drive/trash/clear")
    suspend fun clear(): DriveBatchOperationResponse
}
