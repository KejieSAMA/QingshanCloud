package com.x.cloudnext.android.network.interceptor

import com.x.cloudnext.android.datastore.TokenStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 给所有请求注入 X-CloudNext-Token，对齐
 * cloudnext-web/src/api/cloudnext.js:36-42 的 getAuthHeaders()。
 *
 * 读取 TokenStore.current（StateFlow.value），不阻塞 IO 线程。
 * TokenStore 在 App 启动时已 warm-up（构造器内同步读 EncryptedSharedPreferences）。
 */
@Singleton
class AuthHeaderInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenStore.current
        val request = if (token.isNullOrBlank()) {
            original
        } else {
            original.newBuilder()
                .header(HEADER_NAME, token)
                .build()
        }
        return chain.proceed(request)
    }

    private companion object {
        const val HEADER_NAME = "X-CloudNext-Token"
    }
}
