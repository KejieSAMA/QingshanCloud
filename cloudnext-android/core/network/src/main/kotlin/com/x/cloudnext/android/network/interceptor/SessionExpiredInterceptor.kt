package com.x.cloudnext.android.network.interceptor

import com.x.cloudnext.android.datastore.TokenStore
import com.x.cloudnext.android.network.SessionExpiredBus
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 收到 401 时清 Token 并触发 SessionExpiredBus。
 *
 * 用 Interceptor 而非 Authenticator：Authenticator 会自动重试，但我们不重试（token 过期后不能仅靠
 * 同一个 token 重发）；以及希望 401 透传给业务层让其知道这次调用失败。
 *
 * 注意：业务层在 catch 401 时不要再展示自己的提示，统一由 MainActivity 监听 Bus 跳登录。
 */
@Singleton
class SessionExpiredInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
    private val bus: SessionExpiredBus,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 401) {
            tokenStore.clear()
            bus.signal()
        }
        return response
    }
}
