package com.x.cloudnext.android.common

/**
 * 简化的领域结果类型，避免直接抛异常穿越 Coroutine 边界。
 * 比 kotlin.Result 多一层语义：业务错误 vs 网络错误 vs 未知错误。
 */
sealed interface CnResult<out T> {
    data class Success<T>(val data: T) : CnResult<T>
    data class Failure(val error: CnError) : CnResult<Nothing>
}

sealed interface CnError {
    val message: String

    /** HTTP 4xx — 业务错误（输入非法、资源不存在等）。message 由后端返回。 */
    data class Business(val status: Int, override val message: String) : CnError

    /** HTTP 401 — 未鉴权。由 Authenticator 处理，业务层不应再展示提示。 */
    data object SessionExpired : CnError {
        override val message: String = "会话已失效，请重新登录"
    }

    /** HTTP 5xx — 服务端错误。 */
    data class Server(val status: Int, override val message: String) : CnError

    /** 网络错误（无网、超时、连接拒绝）。 */
    data class Network(override val message: String) : CnError

    /** 兜底未分类。 */
    data class Unknown(override val message: String) : CnError
}

inline fun <T, R> CnResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (CnError) -> R,
): R = when (this) {
    is CnResult.Success -> onSuccess(data)
    is CnResult.Failure -> onFailure(error)
}
