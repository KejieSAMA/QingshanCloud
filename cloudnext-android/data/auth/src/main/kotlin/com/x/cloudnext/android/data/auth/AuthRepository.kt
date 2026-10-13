package com.x.cloudnext.android.data.auth

import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.datastore.SessionInfo
import com.x.cloudnext.android.datastore.SessionInfoStore
import com.x.cloudnext.android.datastore.TokenStore
import com.x.cloudnext.android.network.api.AuthApi
import com.x.cloudnext.android.network.model.AuthStatusResponse
import com.x.cloudnext.android.network.model.LoginRequest
import com.x.cloudnext.android.network.runCatchingApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 鉴权领域门面。把 AuthApi、TokenStore、SessionInfoStore 拼到一起，
 * UI/ViewModel 只依赖这个 Repository。
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val sessionInfoStore: SessionInfoStore,
) {
    /** 当前是否已登录（只看本地 token，不验证服务端）。 */
    val tokenFlow: StateFlow<String?> = tokenStore.tokenFlow

    val sessionInfoFlow: Flow<SessionInfo?> = sessionInfoStore.flow

    val isLoggedIn: Boolean
        get() = !tokenStore.current.isNullOrBlank()

    /**
     * 拉取认证模式信息，用于登录页显示「O2 已连接」「未配置」等。
     * 对照 cloudnext-web/src/pages/LoginPage.vue:120-126 的 loadStatus()。
     */
    suspend fun getAuthStatus(): CnResult<AuthStatusResponse> = runCatchingApi {
        authApi.getAuthStatus()
    }

    /**
     * 提交账号密码登录。成功后落盘 token + session 信息。
     * 失败由 CnResult.Failure 携带 CnError 返回。
     *
     * 防御性：任一步骤失败都回滚已写入的 token，避免下次冷启动靠残留 token 进 Home。
     */
    suspend fun login(credential: String, password: String): CnResult<SessionInfo> =
        runCatchingApi {
            val response = authApi.login(LoginRequest(credential = credential, password = password))
            try {
                tokenStore.save(response.token)
                val info = SessionInfo(
                    personOaId = response.personOaId.orEmpty(),
                    // 后端返回 personName（真名）；保留 displayName 兜底兼容
                    displayName = (response.personName ?: response.displayName).orEmpty(),
                    avatarUrl = response.avatarUrl,
                    email = response.email,
                )
                sessionInfoStore.save(info)
                info
            } catch (e: Throwable) {
                runCatching { tokenStore.clear() }
                runCatching { sessionInfoStore.clear() }
                throw e
            }
        }

    /**
     * 主动登出：通知服务器，无论结果都清本地状态。
     */
    suspend fun logout(): CnResult<Unit> = runCatchingApi {
        runCatching { authApi.logout() }   // 服务端失败也要清本地
        tokenStore.clear()
        sessionInfoStore.clear()
    }

    /**
     * 校验当前 token 是否仍然有效（用于冷启动）。返回 Failure 不一定意味着要登出 —— 401 时
     * SessionExpiredInterceptor 已经清过 token 并发了 bus，UI 自行根据 token 流响应即可。
     */
    suspend fun refreshSession(): CnResult<SessionInfo> = runCatchingApi {
        val response = authApi.me()
        val info = SessionInfo(
            personOaId = response.personOaId.orEmpty(),
            // 后端返回 personName（真名）；保留 displayName 兜底兼容
            displayName = (response.personName ?: response.displayName).orEmpty(),
            avatarUrl = response.avatarUrl,
            email = response.email,
        )
        sessionInfoStore.save(info)
        info
    }
}
