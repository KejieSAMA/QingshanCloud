package com.x.cloudnext.android.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户会话非敏感信息（用户名、显示名、头像 URL 等）。token 不在这里，见 [TokenStore]。
 *
 * 对应 cloudnext-web 的 `cloudnext.session` localStorage 项（除了 token 字段）。
 */
@Serializable
data class SessionInfo(
    val personOaId: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val email: String? = null,
)

private val Context.sessionInfoDataStore by preferencesDataStore(name = "cloudnext_session")

@Singleton
class SessionInfoStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key: Preferences.Key<String> = stringPreferencesKey("session_json")

    val flow: Flow<SessionInfo?> = context.sessionInfoDataStore.data.map { prefs ->
        prefs[key]?.let { runCatching { Json.decodeFromString<SessionInfo>(it) }.getOrNull() }
    }

    suspend fun save(info: SessionInfo) {
        context.sessionInfoDataStore.edit { it[key] = Json.encodeToString(info) }
    }

    suspend fun clear() {
        context.sessionInfoDataStore.edit { it.remove(key) }
    }
}
