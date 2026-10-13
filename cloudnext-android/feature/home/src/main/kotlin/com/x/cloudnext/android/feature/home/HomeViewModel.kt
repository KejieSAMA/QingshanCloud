package com.x.cloudnext.android.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.data.drive.DriveItem
import com.x.cloudnext.android.data.drive.DriveRepository
import com.x.cloudnext.android.datastore.SessionInfo
import com.x.cloudnext.android.datastore.SessionInfoStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val recent: List<DriveItem> = emptyList(),
    val recentOpened: List<DriveItem> = emptyList(),
    val refreshing: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * 首页 ViewModel：并行调 /api/drive/me/recent 和 /api/drive/me/recent-opened，
 * 展示在 [HomeScreen] 两横列。
 *
 * 当前用户信息从 [SessionInfoStore] 取，给 Drawer 头部显示用。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val driveRepository: DriveRepository,
    sessionInfoStore: SessionInfoStore,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val sessionInfo: StateFlow<SessionInfo?> = sessionInfoStore.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true, errorMessage = null) }
        viewModelScope.launch {
            val recentJob = async { driveRepository.getRecent() }
            val openedJob = async { driveRepository.getRecentOpened(6) }
            val recent = recentJob.await()
            val opened = openedJob.await()

            val firstError = listOfNotNull(
                (recent as? CnResult.Failure)?.error,
                (opened as? CnResult.Failure)?.error,
            ).firstOrNull()

            _state.update {
                it.copy(
                    recent = (recent as? CnResult.Success)?.data ?: it.recent,
                    recentOpened = (opened as? CnResult.Success)?.data ?: it.recentOpened,
                    refreshing = false,
                    errorMessage = firstError?.message,
                )
            }
        }
    }
}
