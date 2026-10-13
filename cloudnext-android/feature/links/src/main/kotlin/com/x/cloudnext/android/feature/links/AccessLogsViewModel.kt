package com.x.cloudnext.android.feature.links

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.data.link.LinkAccessLog
import com.x.cloudnext.android.data.link.LinkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccessLogsUiState(
    val totalCount: Long = 0L,
    val successCount: Long = 0L,
    val failCount: Long = 0L,
    val logs: List<LinkAccessLog> = emptyList(),
    val page: Int = 1,
    val hasNext: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AccessLogsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val linkRepository: LinkRepository,
) : ViewModel() {

    private val linkId: String = savedStateHandle.get<String>(ARG_LINK_ID).orEmpty()

    private val _state = MutableStateFlow(AccessLogsUiState(loading = true))
    val state: StateFlow<AccessLogsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, errorMessage = null, page = 1) }
        viewModelScope.launch {
            when (val r = linkRepository.accessLogs(linkId, page = 1)) {
                is CnResult.Success -> _state.update {
                    it.copy(
                        totalCount = r.data.totalCount,
                        successCount = r.data.successCount,
                        failCount = r.data.failCount,
                        logs = r.data.logs,
                        page = 1,
                        hasNext = r.data.hasNext,
                        loading = false,
                    )
                }
                is CnResult.Failure -> _state.update {
                    it.copy(loading = false, errorMessage = r.error.message)
                }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || !s.hasNext) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            when (val r = linkRepository.accessLogs(linkId, page = s.page + 1)) {
                is CnResult.Success -> _state.update {
                    it.copy(
                        logs = it.logs + r.data.logs,
                        page = s.page + 1,
                        hasNext = r.data.hasNext,
                        loadingMore = false,
                    )
                }
                is CnResult.Failure -> _state.update {
                    it.copy(loadingMore = false, errorMessage = r.error.message)
                }
            }
        }
    }

    companion object {
        const val ARG_LINK_ID = "linkId"
    }
}
