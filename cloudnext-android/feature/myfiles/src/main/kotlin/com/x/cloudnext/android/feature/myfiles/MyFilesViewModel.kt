package com.x.cloudnext.android.feature.myfiles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.data.drive.BatchOperationResult
import com.x.cloudnext.android.data.drive.DriveItem
import com.x.cloudnext.android.data.drive.DrivePathSegment
import com.x.cloudnext.android.data.drive.DriveRepository
import com.x.cloudnext.android.data.transfer.TransferRepository
import com.x.cloudnext.android.datastore.PreferencesRepository
import com.x.cloudnext.android.datastore.ViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SelectionMode { Single, Multi }

data class FolderUiState(
    val items: List<DriveItem> = emptyList(),
    val path: List<DrivePathSegment> = emptyList(),
    val refreshing: Boolean = false,
    val errorMessage: String? = null,
    val selectionMode: SelectionMode = SelectionMode.Single,
    val selectedIds: Set<String> = emptySet(),
    val viewMode: ViewMode = ViewMode.List,
) {
    val allSelected: Boolean
        get() = items.isNotEmpty() && items.all { it.id in selectedIds }

    val selectedItems: List<DriveItem>
        get() = items.filter { it.id in selectedIds }
}

/** 移动目标选择器 UI state。 */
data class PickerUiState(
    val pendingMoveIds: Set<String>,
    val currentParentId: String?,
    val currentItems: List<DriveItem> = emptyList(),
    val pathSegments: List<DrivePathSegment> = emptyList(),
    val loading: Boolean = false,
) {
    /** 不能把文件夹移动到它自身或子树里 → 当前层级过滤掉 pendingMove 的 id */
    val visibleFolders: List<DriveItem>
        get() = currentItems.filter { it.isFolder && it.id !in pendingMoveIds }
}

sealed interface UiEvent {
    data class Toast(val message: String) : UiEvent
    /** 请求创建分享。app 模块的 NavHost 收到后导航到 CreateShareRoute。 */
    data class RequestCreateShare(val itemIds: List<String>) : UiEvent
    /** 请求创建外链。仅单文件；fileName 仅用于在创建页显示。 */
    data class RequestCreateLink(val itemId: String, val fileName: String) : UiEvent
}

/**
 * 我的文件视图模型。复用于 MyFilesScreen（根目录）和 FolderScreen（子目录），
 * 通过 [parentId] 区分。
 *
 * M2b 新增：多选、视图模式、CRUD（new/rename/delete/move）、移动目标选择器。
 */
@HiltViewModel
class MyFilesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val driveRepository: DriveRepository,
    private val preferences: PreferencesRepository,
    private val transferRepository: TransferRepository,
) : ViewModel() {

    val parentId: String? = savedStateHandle.get<String?>(ARG_ITEM_ID)

    private val _localState = MutableStateFlow(LocalState())

    val state: StateFlow<FolderUiState> = combine(
        driveRepository.observeFolder(parentId),
        _localState,
        preferences.driveViewModeFlow,
    ) { items, local, viewMode ->
        FolderUiState(
            items = items,
            path = local.path,
            refreshing = local.refreshing,
            errorMessage = local.errorMessage,
            selectionMode = local.selectionMode,
            selectedIds = local.selectedIds,
            viewMode = viewMode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderUiState())

    private val _picker = MutableStateFlow<PickerUiState?>(null)
    val picker: StateFlow<PickerUiState?> = _picker.asStateFlow()
    private var pickerJob: Job? = null

    private val _events = MutableSharedFlow<UiEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        refresh()
        if (parentId != null) loadPath(parentId)
    }

    // -------------------- 列表 --------------------

    fun refresh() {
        _localState.update { it.copy(refreshing = true, errorMessage = null) }
        viewModelScope.launch {
            when (val r = driveRepository.refreshFolder(parentId)) {
                is CnResult.Success -> _localState.update { it.copy(refreshing = false) }
                is CnResult.Failure -> _localState.update {
                    it.copy(refreshing = false, errorMessage = r.error.message)
                }
            }
        }
    }

    fun dismissError() {
        _localState.update { it.copy(errorMessage = null) }
    }

    private fun loadPath(itemId: String) {
        viewModelScope.launch {
            when (val r = driveRepository.getPath(itemId)) {
                is CnResult.Success -> _localState.update { it.copy(path = r.data) }
                is CnResult.Failure -> Unit
            }
        }
    }

    // -------------------- 多选 --------------------

    fun enterMulti(initialId: String? = null) {
        _localState.update {
            it.copy(
                selectionMode = SelectionMode.Multi,
                selectedIds = if (initialId != null) setOf(initialId) else emptySet(),
            )
        }
    }

    fun exitMulti() {
        _localState.update { it.copy(selectionMode = SelectionMode.Single, selectedIds = emptySet()) }
    }

    fun toggleSelected(id: String) {
        _localState.update {
            val next = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id
            it.copy(selectedIds = next)
        }
    }

    fun toggleSelectAll() {
        val s = state.value
        val next = if (s.allSelected) emptySet() else s.items.map { it.id }.toSet()
        _localState.update { it.copy(selectedIds = next) }
    }

    // -------------------- 视图模式 --------------------

    fun setViewMode(mode: ViewMode) {
        viewModelScope.launch { preferences.setDriveViewMode(mode) }
    }

    // -------------------- 上传 --------------------

    /**
     * 把 SAF / 相机返回的 URI 入队上传。本目录作为 parentId。
     */
    fun enqueueUploads(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val ids = transferRepository.enqueueUploads(uris, parentId)
            emit(UiEvent.Toast("已加入上传队列（${ids.size} 项）"))
        }
    }

    // -------------------- 分享 --------------------

    /**
     * 请求创建分享。emit 事件让 UI 层（app 模块 NavHost）导航到 CreateShareScreen。
     * 文件夹同样支持分享，所以不做过滤。
     */
    fun requestShare(items: List<DriveItem>) {
        if (items.isEmpty()) return
        emit(UiEvent.RequestCreateShare(items.map { it.id }))
        if (state.value.selectionMode == SelectionMode.Multi) exitMulti()
    }

    /**
     * 请求创建外链。仅单文件；文件夹忽略。
     */
    fun requestCreateLink(item: DriveItem) {
        if (item.isFolder) {
            emit(UiEvent.Toast("文件夹不支持创建外链"))
            return
        }
        emit(UiEvent.RequestCreateLink(item.id, item.name))
    }

    // -------------------- 下载 --------------------

    /**
     * 把 DriveItem 入队下载到 Downloads/CloudNext/。文件夹会被过滤掉。
     */
    fun enqueueDownloads(items: List<DriveItem>) {
        val files = items.filter { !it.isFolder }
        if (files.isEmpty()) {
            emit(UiEvent.Toast("文件夹暂不支持下载"))
            return
        }
        viewModelScope.launch {
            val ids = transferRepository.enqueueDownloads(files)
            emit(UiEvent.Toast("已加入下载队列（${ids.size} 项）"))
            if (state.value.selectionMode == SelectionMode.Multi) exitMulti()
        }
    }

    // -------------------- CRUD --------------------

    fun createFolder(name: String) {
        viewModelScope.launch {
            when (val r = driveRepository.createFolder(parentId, name)) {
                is CnResult.Success -> emit(UiEvent.Toast("已创建：${r.data.name}"))
                is CnResult.Failure -> emit(UiEvent.Toast(r.error.message))
            }
        }
    }

    fun rename(id: String, newName: String) {
        viewModelScope.launch {
            when (val r = driveRepository.renameItem(id, newName, parentId)) {
                is CnResult.Success -> emit(UiEvent.Toast("已重命名"))
                is CnResult.Failure -> emit(UiEvent.Toast(r.error.message))
            }
        }
    }

    fun delete(ids: Set<String>) {
        if (ids.isEmpty()) return
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
                fail == 0 -> "已移至回收站（$ok 项）"
                ok == 0 -> "删除失败（$fail 项）"
                else -> "成功 $ok / 失败 $fail"
            }
            emit(UiEvent.Toast(msg))
            exitMulti()
        }
    }

    fun moveConfirm() {
        val picker = _picker.value ?: return
        val targetParentId = picker.currentParentId
        val ids = picker.pendingMoveIds.toList()
        if (ids.isEmpty()) {
            closePicker()
            return
        }
        viewModelScope.launch {
            when (val r = driveRepository.moveItems(
                itemIds = ids,
                sourceParentId = parentId,
                targetParentId = targetParentId,
            )) {
                is CnResult.Success -> emitBatchToast(r.data, "移动")
                is CnResult.Failure -> emit(UiEvent.Toast(r.error.message))
            }
            closePicker()
            exitMulti()
        }
    }

    private fun emitBatchToast(result: BatchOperationResult, verb: String) {
        val msg = when {
            result.isAllSuccess -> "已$verb（${result.totalSucceeded} 项）"
            result.isAllFail -> "${verb}失败（${result.totalFailed} 项）"
            else -> "${verb}成功 ${result.totalSucceeded} / 失败 ${result.totalFailed}"
        }
        emit(UiEvent.Toast(msg))
    }

    // -------------------- Picker --------------------

    fun openMovePicker(itemIds: Set<String>) {
        if (itemIds.isEmpty()) return
        _picker.value = PickerUiState(
            pendingMoveIds = itemIds,
            currentParentId = null,
            loading = true,
        )
        loadPicker(parentId = null)
    }

    fun pickerEnter(folderId: String) {
        val s = _picker.value ?: return
        _picker.value = s.copy(currentParentId = folderId, loading = true)
        loadPicker(parentId = folderId)
    }

    fun pickerUp() {
        val s = _picker.value ?: return
        val newId = when {
            s.pathSegments.size <= 1 -> null
            else -> s.pathSegments[s.pathSegments.size - 2].id
        }
        _picker.value = s.copy(currentParentId = newId, loading = true)
        loadPicker(parentId = newId)
    }

    fun closePicker() {
        pickerJob?.cancel()
        _picker.value = null
    }

    private fun loadPicker(parentId: String?) {
        pickerJob?.cancel()
        pickerJob = viewModelScope.launch {
            // 拉项目 + 路径段。这里直接走远程，picker 数据是临时态，不必走 Room 流。
            val itemsResult = driveRepository.refreshFolder(parentId)
            val items = (itemsResult as? CnResult.Success)?.data ?: emptyList()
            val pathResult = driveRepository.getPath(parentId)
            val segments = (pathResult as? CnResult.Success)?.data ?: emptyList()
            _picker.value = _picker.value?.copy(
                currentItems = items,
                pathSegments = segments,
                loading = false,
            )
        }
    }

    // -------------------- 工具 --------------------

    private fun emit(event: UiEvent) {
        _events.tryEmit(event)
    }

    private data class LocalState(
        val path: List<DrivePathSegment> = emptyList(),
        val refreshing: Boolean = false,
        val errorMessage: String? = null,
        val selectionMode: SelectionMode = SelectionMode.Single,
        val selectedIds: Set<String> = emptySet(),
    )

    companion object {
        const val ARG_ITEM_ID = "itemId"
    }
}
