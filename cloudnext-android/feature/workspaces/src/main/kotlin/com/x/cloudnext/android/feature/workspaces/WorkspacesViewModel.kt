package com.x.cloudnext.android.feature.workspaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.data.workspace.WorkspaceRepository
import com.x.cloudnext.android.data.workspace.WorkspaceSummary
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

data class WorkspacesUiState(
    val all: List<WorkspaceSummary> = emptyList(),
    val keyword: String = "",
    val loading: Boolean = true,
    val errorMessage: String? = null,
) {
    val filtered: List<WorkspaceSummary>
        get() = if (keyword.isBlank()) all
        else all.filter { it.name.contains(keyword, ignoreCase = true) }
}

sealed interface WorkspacesEvent {
    data class Toast(val message: String) : WorkspacesEvent
}

@HiltViewModel
class WorkspacesViewModel @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkspacesUiState())
    val state: StateFlow<WorkspacesUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WorkspacesEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<WorkspacesEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val r = workspaceRepository.listMine()) {
                is CnResult.Success -> _state.update {
                    it.copy(all = r.data, loading = false)
                }
                is CnResult.Failure -> _state.update {
                    it.copy(loading = false, errorMessage = r.error.message)
                }
            }
        }
    }

    fun setKeyword(text: String) {
        _state.update { it.copy(keyword = text) }
    }

    fun create(name: String) {
        viewModelScope.launch {
            when (val r = workspaceRepository.create(name)) {
                is CnResult.Success -> {
                    _events.tryEmit(WorkspacesEvent.Toast("已创建：${r.data.name}"))
                    refresh()
                }
                is CnResult.Failure -> _events.tryEmit(WorkspacesEvent.Toast(r.error.message))
            }
        }
    }
}
