package com.x.cloudnext.android.feature.shared.outgoing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.data.drive.BatchOperationResult
import com.x.cloudnext.android.data.share.DriveShare
import com.x.cloudnext.android.data.share.ShareRepository
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

data class OutgoingUiState(
    val items: List<DriveShare> = emptyList(),
    val page: Int = 1,
    val hasNext: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val errorMessage: String? = null,
    val selecting: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
) {
    val allSelected: Boolean
        get() = items.isNotEmpty() && items.all { it.id in selectedIds }
}

sealed interface OutgoingEvent {
    data class Toast(val message: String) : OutgoingEvent
}

@HiltViewModel
class OutgoingViewModel @Inject constructor(
    private val shareRepository: ShareRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OutgoingUiState(loading = true))
    val state: StateFlow<OutgoingUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<OutgoingEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<OutgoingEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, errorMessage = null, page = 1) }
        viewModelScope.launch {
            when (val r = shareRepository.listOutgoing(page = 1)) {
                is CnResult.Success -> _state.update {
                    it.copy(
                        items = r.data.items,
                        page = r.data.page,
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
            when (val r = shareRepository.listOutgoing(page = s.page + 1)) {
                is CnResult.Success -> _state.update {
                    it.copy(
                        items = it.items + r.data.items,
                        page = r.data.page,
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

    fun enterMulti(initialId: String? = null) {
        _state.update {
            it.copy(
                selecting = true,
                selectedIds = if (initialId != null) setOf(initialId) else emptySet(),
            )
        }
    }

    fun exitMulti() {
        _state.update { it.copy(selecting = false, selectedIds = emptySet()) }
    }

    fun toggleSelected(id: String) {
        _state.update {
            val next = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id
            it.copy(selectedIds = next)
        }
    }

    fun toggleSelectAll() {
        val s = _state.value
        val next = if (s.allSelected) emptySet() else s.items.map { it.id }.toSet()
        _state.update { it.copy(selectedIds = next) }
    }

    fun revokeSingle(id: String) {
        viewModelScope.launch {
            when (val r = shareRepository.revokeShare(id)) {
                is CnResult.Success -> emit(OutgoingEvent.Toast("已撤销分享"))
                is CnResult.Failure -> emit(OutgoingEvent.Toast(r.error.message))
            }
            refresh()
        }
    }

    fun revokeBatch(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            when (val r = shareRepository.revokeBatch(ids.toList())) {
                is CnResult.Success -> emitBatch(r.data, "已撤销", "撤销失败")
                is CnResult.Failure -> emit(OutgoingEvent.Toast(r.error.message))
            }
            exitMulti()
            refresh()
        }
    }

    private fun emitBatch(result: BatchOperationResult, okLabel: String, failLabel: String) {
        val msg = when {
            result.isAllSuccess -> "$okLabel（${result.totalSucceeded} 项）"
            result.isAllFail -> "$failLabel（${result.totalFailed} 项）"
            else -> "成功 ${result.totalSucceeded} / 失败 ${result.totalFailed}"
        }
        emit(OutgoingEvent.Toast(msg))
    }

    private fun emit(event: OutgoingEvent) {
        _events.tryEmit(event)
    }
}
