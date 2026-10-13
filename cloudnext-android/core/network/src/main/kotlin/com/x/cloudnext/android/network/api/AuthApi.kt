package com.x.cloudnext.android.network.api

import com.x.cloudnext.android.network.model.AuthStatusResponse
import com.x.cloudnext.android.network.model.CurrentSessionResponse
import com.x.cloudnext.android.network.model.LoginRequest
import com.x.cloudnext.android.network.model.LoginResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * 认证相关接口。对应 cloudnext-web/src/api/cloudnext.js 中
 *   getAuthStatus / login / getCurrentSession / logout 四个函数。
 *
 * 鉴权 Header X-CloudNext-Token 由 AuthHeaderInterceptor 统一注入；
 * /api/auth/status 端点本身不要求 token，但拦截器多带也无害（后端 AuthSessionInterceptor 对该路径放行）。
 */
interface AuthApi {

    @GET("api/auth/status")
    suspend fun getAuthStatus(): AuthStatusResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/auth/me")
    suspend fun me(): CurrentSessionResponse

    @DELETE("api/auth/logout")
    suspend fun logout()
}
