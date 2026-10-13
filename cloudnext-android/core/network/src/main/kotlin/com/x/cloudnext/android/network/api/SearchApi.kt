package com.x.cloudnext.android.network.api

import com.x.cloudnext.android.network.model.DrivePageResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 搜索接口。对应 cloudnext-server CloudNextSearchController（`/api/search/files`）。
 *
 * scope 取值：personal（默认） / all
 * itemType："" / "folder" / "file"
 * fileType："" / word / excel / ppt / pdf / image / video / archive / other
 */
interface SearchApi {

    @GET("api/search/files")
    suspend fun searchFiles(
        @Query("keyword") keyword: String,
        @Query("scope") scope: String = "personal",
        @Query("itemType") itemType: String = "",
        @Query("fileType") fileType: String = "",
        @Query("owner") owner: String = "",
        @Query("updatedFrom") updatedFrom: String = "",
        @Query("updatedTo") updatedTo: String = "",
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
    ): DrivePageResponse
}
