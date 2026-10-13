package com.x.cloudnext.android.feature.links

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.data.link.DrivePublicLink
import com.x.cloudnext.android.data.link.LinkFeatureState
import com.x.cloudnext.android.data.link.LinkPermission
import com.x.cloudnext.android.data.link.LinkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LinksUiState(
    val featureEnabled: Boolean = true,
    val links: List<DrivePublicLink> = emptyList(),
    val loading: Boolean = true,
    val errorMessage: String? = null,
)

sealed interface LinksEvent {
    data class Toast(val message: String) : LinksEvent
    /** 复制 shareUrl 到剪贴板（caller 处理）。 */
    data class CopyToClipboard(val text: String) : LinksEvent
}

@HiltViewModel
class LinksViewModel @Inject constructor(
    private val linkRepository: LinkRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LinksUiState())
    val state: StateFlow<LinksUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<LinksEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<LinksEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val r = linkRepository.listMine()) {
                is CnResult.Success -> when (val data = r.data) {
                    is LinkFeatureState.Available -> _state.update {
                        it.copy(featureEnabled = true, links = data.links, loading = false)
                    }
                    is LinkFeatureState.Disabled -> _state.update {
                        it.copy(featureEnabled = false, links = emptyList(), loading = false)
                    }
                }
                is CnResult.Failure -> _state.update {
                    it.copy(loading = false, errorMessage = r.error.message)
                }
            }
        }
    }

    fun copyShareUrl(link: DrivePublicLink, apiBaseUrl: String) {
        val url = com.x.cloudnext.android.data.link.resolveShareUrl(link.shareUrl, apiBaseUrl)
        if (url.isNullOrBlank()) {
            emit(LinksEvent.Toast("链接为空"))
        } else {
            emit(LinksEvent.CopyToClipboard(url))
        }
    }

    fun revoke(id: String) {
        viewModelScope.launch {
            when (val r = linkRepository.revoke(id)) {
                is CnResult.Success -> emit(LinksEvent.Toast("已撤销外链"))
                is CnResult.Failure -> emit(LinksEvent.Toast(r.error.message))
            }
            refresh()
        }
    }

    /**
     * 创建外链（MyFiles ActionSheet 触发，传 itemId 进来）。
     * 返回值通过 events 通知 + refresh 列表。
     */
    fun create(
        itemId: String,
        permission: LinkPermission,
        password: String?,
        downloadLimit: Long?,
        expiresAt: String?,
    ) {
        viewModelScope.launch {
            when (val r = linkRepository.create(itemId, permission, password, downloadLimit, expiresAt)) {
                is CnResult.Success -> {
                    emit(LinksEvent.Toast("已创建外链"))
                    refresh()
                }
                is CnResult.Failure -> emit(LinksEvent.Toast(r.error.message))
            }
        }
    }

    fun update(
        id: String,
        permission: LinkPermission?,
        password: String?,
        downloadLimit: Long?,
        expiresAt: String?,
    ) {
        viewModelScope.launch {
            when (val r = linkRepository.update(id, permission, password, downloadLimit, expiresAt)) {
                is CnResult.Success -> {
                    emit(LinksEvent.Toast("已更新外链"))
                    refresh()
                }
                is CnResult.Failure -> emit(LinksEvent.Toast(r.error.message))
            }
        }
    }

    private fun emit(event: LinksEvent) {
        _events.tryEmit(event)
    }
}
