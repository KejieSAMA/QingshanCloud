package com.x.cloudnext.android.network.api

import com.x.cloudnext.android.network.model.OrgSearchResultResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 组织搜索接口。对应 cloudnext-server CloudNextOrgController（`/api/org/search`）。
 *
 * 用于创建分享时选择目标人 / 部门。
 *
 * type 取值："person" / "department"
 */
interface OrgApi {

    @GET("api/org/search")
    suspend fun search(
        @Query("type") type: String,
        @Query("keyword") keyword: String,
    ): List<OrgSearchResultResponse>
}
