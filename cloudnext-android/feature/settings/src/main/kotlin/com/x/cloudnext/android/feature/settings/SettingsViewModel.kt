package com.x.cloudnext.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.datastore.PreferencesRepository
import com.x.cloudnext.android.datastore.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val wifiOnly: Boolean = false,
)

sealed interface SettingsEvent {
    data class Toast(val message: String) : SettingsEvent
    data object CacheCleared : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val cacheCleaner: CacheCleaner,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = kotlinx.coroutines.flow.combine(
        preferences.themeModeFlow,
        preferences.wifiOnlyFlow,
    ) { themeMode, wifiOnly ->
        SettingsUiState(themeMode = themeMode, wifiOnly = wifiOnly)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _events = MutableSharedFlow<SettingsEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { preferences.setWifiOnly(enabled) }
    }

    fun clearCache() {
        viewModelScope.launch {
            cacheCleaner.clearAll()
            _events.tryEmit(SettingsEvent.Toast("已清除缓存"))
            _events.tryEmit(SettingsEvent.CacheCleared)
        }
    }
}
