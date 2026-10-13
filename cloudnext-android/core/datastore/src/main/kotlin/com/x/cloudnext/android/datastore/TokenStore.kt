package com.x.cloudnext.android.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Token 持久化 + 内存镜像。
 *
 * - 物理：EncryptedSharedPreferences（AES256_SIV / AES256_GCM，由 Tink 实现）
 * - 内存：MutableStateFlow，OkHttp 拦截器同步读 .value 不阻塞 IO 线程
 *
 * 单例。初始化时同步读一次（构造器内），后续 save/clear 同步写盘 + 更新 flow。
 *
 * 与 cloudnext-web/src/session.js 的 CLOUDNEXT_TOKEN_KEY = 'cloudnext.token' 对齐。
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    private val _tokenFlow = MutableStateFlow(prefs.getString(KEY_TOKEN, null))
    val tokenFlow: StateFlow<String?> = _tokenFlow.asStateFlow()

    val current: String?
        get() = _tokenFlow.value

    fun save(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
        _tokenFlow.value = token
    }

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
        _tokenFlow.value = null
    }

    private companion object {
        const val KEY_TOKEN = "cloudnext.token"
        const val PREFS_NAME = "cloudnext_secure_prefs"

        fun createEncryptedPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
