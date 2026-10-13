package com.x.cloudnext.android.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 对应 GET /api/auth/status 的返回。
 *
 * Web 端 LoginPage.vue:108-111 只使用 mode 和 o2Configured 两个字段。
 */
@Serializable
data class AuthStatusResponse(
    val mode: String? = null,
    @SerialName("o2Configured") val o2Configured: Boolean = false,
)

/**
 * POST /api/auth/login 请求体。字段名严格对齐 cloudnext-web/src/api/cloudnext.js:163-168。
 */
@Serializable
data class LoginRequest(
    val credential: String,
    val password: String,
)

/**
 * POST /api/auth/login 响应体。session.js setStoredSession 保存了整个对象，token 是必含字段。
 *
 * **字段对齐**：后端 [AuthLoginResponse.java](cloudnext-server/.../AuthLoginResponse.java)
 * 实际返回 `personName`（中文用户真名），早期 Android 这边错写成 `displayName`，
 * 反序列化时永远 null → Drawer 显示"用户"。保留 `displayName` 字段双兼容（H5 老接口
 * 或自建桥接环境可能用 displayName），优先取 `personName`。
 */
@Serializable
data class LoginResponse(
    val token: String,
    val personOaId: String? = null,
    val personName: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val email: String? = null,
)

/**
 * GET /api/auth/me 响应。结构与 LoginResponse 类似，刷新当前会话信息时使用。
 */
@Serializable
data class CurrentSessionResponse(
    val personOaId: String? = null,
    val personName: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val email: String? = null,
)
