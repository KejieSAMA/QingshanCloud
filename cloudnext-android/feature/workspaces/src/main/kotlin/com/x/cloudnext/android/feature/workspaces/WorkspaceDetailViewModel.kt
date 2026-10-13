package com.x.cloudnext.android.feature.workspaces

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.data.drive.DriveItem
import com.x.cloudnext.android.data.drive.DriveRepository
import com.x.cloudnext.android.data.transfer.TransferRepository
import com.x.cloudnext.android.data.transfer.TransferScope
import com.x.cloudnext.android.data.workspace.WorkspaceDetail
import com.x.cloudnext.android.data.workspace.WorkspaceRepository
import com.x.cloudnext.android.data.workspace.WorkspaceRole
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

data class WorkspaceDetailUiState(
    val workspace: WorkspaceDetail? = null,
    val items: List<DriveItem> = emptyList(),
    val parentId: String? = null,
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val selecting: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
) {
    val myRole: WorkspaceRole get() = workspace?.myRole ?: WorkspaceRole.Viewer
    val canWrite: Boolean get() = myRole.canWrite
    val allSelected: Boolean
        get() = items.isNotEmpty() && items.all { it.id in selectedIds }
    val selectedItems: List<DriveItem>
        get() = items.filter { it.id in selectedIds }
}

sealed interface WorkspaceDetailEvent {
    data class Toast(val message: String) : WorkspaceDetailEvent
}

@HiltViewModel
class WorkspaceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workspaceRepository: WorkspaceRepository,
    private val driveRepository: DriveRepository,
    private val transferRepository: TransferRepository,
) : ViewModel() {

    val workspaceId: String = savedStateHandle.get<String>(ARG_WORKSPACE_ID).orEmpty()
    /** null = 工作空间根；非 null = 子目录 */
    val parentId: String? = savedStateHandle.get<String?>(ARG_PARENT_ID)

    private val _state = MutableStateFlow(WorkspaceDetailUiState(loading = true, parentId = parentId))
    val state: StateFlow<WorkspaceDetailUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<WorkspaceDetailEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<WorkspaceDetailEvent> = _events.asSharedFlow()

    init {
        loadDetail()
        refreshItems()
    }

    private fun loadDetail() {
        viewModelScope.launch {
            when (val r = workspaceRepository.getDetail(workspaceId)) {
                is CnResult.Success -> _state.update { it.copy(workspace = r.data) }
                is CnResult.Failure -> _state.update { it.copy(errorMessage = r.error.message) }
            }
        }
    }

    fun refreshItems() {
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val r = workspaceRepository.listItems(workspaceId, parentId)) {
                is CnResult.Success -> _state.update {
                    it.copy(items = r.data, loading = false)
                }
                is CnResult.Failure -> _state.update {
                    it.copy(loading = false, errorMessage = r.error.message)
                }
            }
        }
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

    // -------------------- CRUD --------------------

    fun createFolder(name: String) {
        if (!_state.value.canWrite) return
        viewModelScope.launch {
            when (val r = workspaceRepository.createFolder(workspaceId, name, parentId)) {
                is CnResult.Success -> {
                    emit(WorkspaceDetailEvent.Toast("已创建：${r.data.name}"))
                    refreshItems()
                }
                is CnResult.Failure -> emit(WorkspaceDetailEvent.Toast(r.error.message))
            }
        }
    }

    /**
     * 重命名 / 删除走通用 /api/drive/items/{id}（scope-aware；后端会路由到 workspace trash）。
     * 复用 DriveRepository。
     */
    fun rename(id: String, name: String) {
        if (!_state.value.canWrite) return
        viewModelScope.launch {
            when (val r = driveRepository.renameItem(id, name, parentId)) {
                is CnResult.Success -> {
                    emit(WorkspaceDetailEvent.Toast("已重命名"))
                    refreshItems()
                }
                is CnResult.Failure -> emit(WorkspaceDetailEvent.Toast(r.error.message))
            }
        }
    }

    fun delete(ids: Set<String>) {
        if (!_state.value.canWrite || ids.isEmpty()) return
        viewModelScope.launch {
            var ok = 0
            var fail = 0
            ids.forEach { id ->
                when (driveRepository.deleteItem(id, parentId)) {
                    is CnResult.Success -> ok++
                    is CnResult.Failure -> fail++
                }
            }
            val msg = when {
                fail == 0 -> "已删除（$ok 项）"
                ok == 0 -> "删除失败（$fail 项）"
                else -> "成功 $ok / 失败 $fail"
            }
            emit(WorkspaceDetailEvent.Toast(msg))
            exitMulti()
            refreshItems()
        }
    }

    // -------------------- 上传 --------------------

    fun enqueueUploads(uris: List<Uri>) {
        if (!_state.value.canWrite || uris.isEmpty()) return
        viewModelScope.launch {
            val ids = transferRepository.enqueueUploads(
                uris = uris,
                parentId = parentId,
                scope = TransferScope.Workspace(workspaceId),
            )
            emit(WorkspaceDetailEvent.Toast("已加入上传队列（${ids.size} 项）"))
        }
    }

    // -------------------- helpers --------------------

    private fun emit(event: WorkspaceDetailEvent) {
        _events.tryEmit(event)
    }

    companion object {
        const val ARG_WORKSPACE_ID = "workspaceId"
        const val ARG_PARENT_ID = "parentId"
    }
}
