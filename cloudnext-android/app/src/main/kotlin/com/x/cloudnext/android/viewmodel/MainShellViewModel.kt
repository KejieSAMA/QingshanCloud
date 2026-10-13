package com.x.cloudnext.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.data.auth.AuthRepository
import com.x.cloudnext.android.datastore.SessionInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主壳层（已登录后的 Scaffold）共享 ViewModel。给 Drawer 提供用户信息和登出动作。
 */
@HiltViewModel
class MainShellViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val sessionInfo: StateFlow<SessionInfo?> = authRepository.sessionInfoFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Drawer 头部显示名兜底链。优先级：displayName（personName）→ personOaId（非 UUID）→ "用户"。
     *
     * 对齐 H5 [MobileDrawer.vue](cloudnext-web/src/components/mobile/MobileDrawer.vue) 的
     * `personName || personOaId || "当前用户"` 取值。
     *
     * 保留 UUID 检测：某些 OA 环境 personOaId 是纯 UUID 或 distinguishedName，对用户显示
     * 体验差（如 "bb7c92aa-4446-4357-ae81-c123b28e7331"），仍 fallback 到"用户"。
     * email 字段后端不返回，原来的 email 分支去掉。
     */
    val displayLabel: StateFlow<String> = authRepository.sessionInfoFlow
        .map { info ->
            val name = info?.displayName?.trim()
            if (!name.isNullOrEmpty() && !looksLikeUuid(name)) return@map name
            val oaId = info?.personOaId?.trim()
            if (!oaId.isNullOrEmpty() && !looksLikeUuid(oaId)) return@map oaId
            "用户"
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "用户")

    /** v4 UUID 或 distinguishedName 形态判断 —— 避免 UUID 当用户名露给用户。 */
    private fun looksLikeUuid(s: String): Boolean =
        UUID_REGEX.matches(s)

    companion object {
        private val UUID_REGEX = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
        )
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
