package com.x.cloudnext.android.feature.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.data.drive.BatchOperationResult
import com.x.cloudnext.android.data.drive.DriveItem
import com.x.cloudnext.android.data.trash.TrashFilter
import com.x.cloudnext.android.data.trash.TrashRepository
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

data class TrashUiState(
    val items: List<DriveItem> = emptyList(),
    val filter: TrashFilter = TrashFilter.All,
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

sealed interface TrashEvent {
    data class Toast(val message: String) : TrashEvent
}

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val trashRepository: TrashRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TrashUiState(loading = true))
    val state: StateFlow<TrashUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TrashEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<TrashEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    // -------------------- 列表 --------------------

    fun refresh() {
        _state.update { it.copy(loading = true, errorMessage = null, page = 1) }
        viewModelScope.launch {
            when (val r = trashRepository.query(filter = _state.value.filter, page = 1)) {
                is CnResult.Success -> _state.update {
                    it.copy(
                        items = r.data.items,
                        page = r.data.page,
                        hasNext = r.data.hasNext,
                        loading = false,
                        errorMessage = null,
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
            when (val r = trashRepository.query(filter = s.filter, page = s.page + 1)) {
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

    fun setFilter(filter: TrashFilter) {
        if (filter == _state.value.filter) return
        _state.update { it.copy(filter = filter) }
        refresh()
    }

    // -------------------- 多选 --------------------

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

    // -------------------- 操作 --------------------

    fun restore(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            when (val r = trashRepository.restore(ids.toList())) {
                is CnResult.Success -> emitBatchToast(r.data, "已恢复", "恢复失败")
                is CnResult.Failure -> emit(TrashEvent.Toast(r.error.message))
            }
            exitMulti()
            refresh()
        }
    }

    fun purge(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            when (val r = trashRepository.purge(ids.toList())) {
                is CnResult.Success -> emitBatchToast(r.data, "已彻底删除", "删除失败")
                is CnResult.Failure -> emit(TrashEvent.Toast(r.error.message))
            }
            exitMulti()
            refresh()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            when (val r = trashRepository.clearAll()) {
                is CnResult.Success -> emit(TrashEvent.Toast("回收站已清空（${r.data.totalSucceeded} 项）"))
                is CnResult.Failure -> emit(TrashEvent.Toast(r.error.message))
            }
            exitMulti()
            refresh()
        }
    }

    private fun emitBatchToast(result: BatchOperationResult, okLabel: String, failLabel: String) {
        val msg = when {
            result.isAllSuccess -> "$okLabel（${result.totalSucceeded} 项）"
            result.isAllFail -> "$failLabel（${result.totalFailed} 项）"
            else -> "成功 ${result.totalSucceeded} / 失败 ${result.totalFailed}"
        }
        emit(TrashEvent.Toast(msg))
    }

    private fun emit(event: TrashEvent) {
        _events.tryEmit(event)
    }
}
