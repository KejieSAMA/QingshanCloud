package com.x.cloudnext.android.network

import com.x.cloudnext.android.common.CnError
import com.x.cloudnext.android.common.CnResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException

/**
 * 后端业务错误响应（如 `{"message": "...", "code": "..."}`）解析。
 * 与 cloudnext-web 的 createHttpError 行为对齐：优先取 message，否则用 "请求失败: <status>"。
 */
private val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * 把任意异常归一到 [CnError]。在 Repository 层使用：
 *
 *   runCatching { api.login(...) }.toCnResult()
 */
fun Throwable.toCnError(): CnError = when (this) {
    is HttpException -> mapHttp(this)
    is IOException -> CnError.Network(message ?: "网络连接异常")
    else -> CnError.Unknown(message ?: this::class.simpleName ?: "未知错误")
}

inline fun <T> runCatchingApi(block: () -> T): CnResult<T> =
    try {
        CnResult.Success(block())
    } catch (e: Throwable) {
        Timber.w(e, "API call failed")
        CnResult.Failure(e.toCnError())
    }

private fun mapHttp(e: HttpException): CnError {
    val status = e.code()
    val message = extractServerMessage(e) ?: "请求失败: $status"
    return when (status) {
        401 -> CnError.SessionExpired
        in 400..499 -> CnError.Business(status, message)
        in 500..599 -> CnError.Server(status, message)
        else -> CnError.Unknown(message)
    }
}

private fun extractServerMessage(e: HttpException): String? {
    val raw = e.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        val obj = lenientJson.parseToJsonElement(raw) as? JsonObject
        obj?.get("message")?.jsonPrimitive?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
