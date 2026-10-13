package com.x.cloudnext.android.network

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局会话过期事件总线。
 *
 * 设计：用 SharedFlow(replay=0) + DROP_OLDEST 防止并发 401 同时触发多个跳转。
 * 拦截器/Authenticator 收到 401 时调 [notify]，MainActivity collect 后清 Token 并 navigate 到 Login。
 */
@Singleton
class SessionExpiredBus @Inject constructor() {
    private val _events = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun signal() {
        _events.tryEmit(Unit)
    }
}
