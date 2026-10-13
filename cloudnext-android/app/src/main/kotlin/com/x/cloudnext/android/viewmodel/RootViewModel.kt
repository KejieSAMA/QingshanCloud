package com.x.cloudnext.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.data.auth.AuthRepository
import com.x.cloudnext.android.navigation.CnRoute
import com.x.cloudnext.android.network.SessionExpiredBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 启动期 token 体检状态机。
 *
 * - Checking：冷启动且本地有 token，正在调 /api/auth/me 验证
 * - Ready：可以渲染 NavHost（无论是否登录）
 *
 * 注意：失败由 SessionExpiredInterceptor 自动清 token + 发 SessionExpiredBus，
 * CloudNextNavHost 已订阅 token 流自动跳 Login。此处只负责"等校验返回再渲染 UI"，
 * 避免冷启动闪过 Home 再被踢回 Login。
 */
enum class BootstrapState { Checking, Ready }

@HiltViewModel
class RootViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    sessionExpiredBus: SessionExpiredBus,
) : ViewModel() {

    val tokenFlow: StateFlow<String?> = authRepository.tokenFlow

    val sessionExpired: SharedFlow<Unit> = sessionExpiredBus.events

    private val _bootstrap = MutableStateFlow(
        if (authRepository.isLoggedIn) BootstrapState.Checking else BootstrapState.Ready,
    )
    val bootstrap: StateFlow<BootstrapState> = _bootstrap.asStateFlow()

    init {
        if (authRepository.isLoggedIn) {
            viewModelScope.launch {
                // 不关心结果：成功不动 token；失败由拦截器清掉 token 并 SessionExpiredBus 通知。
                runCatching { authRepository.refreshSession() }
                _bootstrap.value = BootstrapState.Ready
            }
        }
    }

    fun initialDestination(): CnRoute =
        if (authRepository.isLoggedIn) CnRoute.Home else CnRoute.Login
}
