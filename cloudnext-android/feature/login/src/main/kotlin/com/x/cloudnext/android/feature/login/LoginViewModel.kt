package com.x.cloudnext.android.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val credential: String = "",
    val password: String = "",
    val authMode: String = "",
    val o2Configured: Boolean = false,
    val submitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    /** 登录是否完成，UI 用它做导航触发。 */
    val loggedIn: Boolean = false,
) {
    val canSubmit: Boolean
        get() = credential.isNotBlank() && password.isNotEmpty() && !submitting
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        loadAuthStatus()
    }

    fun onCredentialChange(value: String) {
        _state.update { it.copy(credential = value.trim(), errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, errorMessage = null) }
    }

    fun consumeError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /**
     * 提交登录。对照 cloudnext-web/src/pages/LoginPage.vue:128-149 的 onSubmit。
     */
    fun submit() {
        val current = _state.value
        if (!current.canSubmit) {
            _state.update { it.copy(errorMessage = "请输入账号和密码。") }
            return
        }
        _state.update { it.copy(submitting = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            when (val result = authRepository.login(current.credential, current.password)) {
                is CnResult.Success -> _state.update {
                    it.copy(
                        submitting = false,
                        successMessage = "登录成功，正在进入工作台。",
                        loggedIn = true,
                    )
                }
                is CnResult.Failure -> _state.update {
                    it.copy(
                        submitting = false,
                        errorMessage = result.error.message,
                    )
                }
            }
        }
    }

    private fun loadAuthStatus() {
        viewModelScope.launch {
            when (val result = authRepository.getAuthStatus()) {
                is CnResult.Success -> _state.update {
                    it.copy(
                        authMode = result.data.mode.orEmpty(),
                        o2Configured = result.data.o2Configured,
                    )
                }
                is CnResult.Failure -> _state.update {
                    it.copy(errorMessage = result.error.message)
                }
            }
        }
    }
}
