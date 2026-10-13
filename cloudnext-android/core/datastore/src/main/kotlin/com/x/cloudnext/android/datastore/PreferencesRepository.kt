package com.x.cloudnext.android.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

enum class ViewMode { List, Grid }

/**
 * 主题模式。System 跟随系统；Light / Dark 强制覆盖。
 */
enum class ThemeMode { System, Light, Dark }

private val Context.preferencesDataStore by preferencesDataStore(name = "cloudnext_prefs")

/**
 * 通用 App 偏好存储（非加密）。token/session 不走这里，见 [TokenStore] / [SessionInfoStore]。
 *
 * 当前持久化项：
 * - drive.view.mode（list / grid）
 * - search.history（M5a 搜索历史词，JSON List<String>，最多 10 条，去重 + LRU）
 * - ui.theme.mode（M6 system / light / dark）
 * - transfer.wifi.only（M6 仅 WiFi 上传/下载）
 */
@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyDriveViewMode: Preferences.Key<String> = stringPreferencesKey("drive.view.mode")
    private val keySearchHistory: Preferences.Key<String> = stringPreferencesKey("search.history")
    private val keyThemeMode: Preferences.Key<String> = stringPreferencesKey("ui.theme.mode")
    private val keyWifiOnly: Preferences.Key<Boolean> = booleanPreferencesKey("transfer.wifi.only")

    private val historyJson = Json { ignoreUnknownKeys = true }
    private val historySerializer = ListSerializer(String.serializer())

    val driveViewModeFlow: Flow<ViewMode> = context.preferencesDataStore.data.map { prefs ->
        when (prefs[keyDriveViewMode]) {
            "grid" -> ViewMode.Grid
            else -> ViewMode.List
        }
    }

    suspend fun setDriveViewMode(mode: ViewMode) {
        context.preferencesDataStore.edit { prefs ->
            prefs[keyDriveViewMode] = if (mode == ViewMode.Grid) "grid" else "list"
        }
    }

    // -------------------- 搜索历史 (M5a) --------------------

    /**
     * 搜索历史词。LRU 顺序（最新在前），最多 10 条，去重。
     */
    val searchHistoryFlow: Flow<List<String>> = context.preferencesDataStore.data.map { prefs ->
        decodeHistory(prefs[keySearchHistory])
    }

    /** 添加一条历史（已存在则提到最前；超过 10 截断尾部）。空串忽略。 */
    suspend fun addSearchHistory(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        context.preferencesDataStore.edit { prefs ->
            val current = decodeHistory(prefs[keySearchHistory])
            val merged = buildList(capacity = MAX_HISTORY) {
                add(trimmed)
                addAll(current.filter { it != trimmed })
            }.take(MAX_HISTORY)
            prefs[keySearchHistory] = historyJson.encodeToString(historySerializer, merged)
        }
    }

    /** 移除单条历史。 */
    suspend fun removeSearchHistory(keyword: String) {
        context.preferencesDataStore.edit { prefs ->
            val current = decodeHistory(prefs[keySearchHistory])
            val next = current.filter { it != keyword }
            prefs[keySearchHistory] = historyJson.encodeToString(historySerializer, next)
        }
    }

    /** 清空全部历史。 */
    suspend fun clearSearchHistory() {
        context.preferencesDataStore.edit { prefs ->
            prefs.remove(keySearchHistory)
        }
    }

    private fun decodeHistory(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            historyJson.decodeFromString(historySerializer, raw)
        }.getOrDefault(emptyList())
    }

    // -------------------- 主题 (M6) --------------------

    /** 主题模式 Flow。默认 System（跟随系统）。 */
    val themeModeFlow: Flow<ThemeMode> = context.preferencesDataStore.data.map { prefs ->
        when (prefs[keyThemeMode]) {
            "light" -> ThemeMode.Light
            "dark" -> ThemeMode.Dark
            else -> ThemeMode.System
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.preferencesDataStore.edit { prefs ->
            prefs[keyThemeMode] = when (mode) {
                ThemeMode.System -> "system"
                ThemeMode.Light -> "light"
                ThemeMode.Dark -> "dark"
            }
        }
    }

    // -------------------- 仅 WiFi 传输 (M6) --------------------

    /** 仅 WiFi 上传/下载 Flow。默认 false（允许移动网络）。 */
    val wifiOnlyFlow: Flow<Boolean> = context.preferencesDataStore.data.map { prefs ->
        prefs[keyWifiOnly] ?: false
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.preferencesDataStore.edit { prefs ->
            prefs[keyWifiOnly] = enabled
        }
    }

    companion object {
        private const val MAX_HISTORY = 10
    }
}
