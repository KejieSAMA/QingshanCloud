package com.x.cloudnext.android.feature.shared.incoming

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.data.share.DriveShare
import com.x.cloudnext.android.data.share.ShareRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * incoming 子目录 ViewModel。通过 SavedStateHandle 拿到 shareId + parentItemId。
 */
@HiltViewModel
class IncomingFolderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val shareRepository: ShareRepository,
) : ViewModel() {

    private val shareId: String = savedStateHandle.get<String>(ARG_SHARE_ID).orEmpty()
    private val parentItemId: String = savedStateHandle.get<String>(ARG_ITEM_ID).orEmpty()

    private val _state = MutableStateFlow(IncomingUiState(loading = true))
    val state: StateFlow<IncomingUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, errorMessage = null, page = 1) }
        viewModelScope.launch {
            when (val r = shareRepository.listIncoming(page = 1, status = "")) {
                is CnResult.Success -> _state.update { ui ->
                    // 客户端按 shareId + parentId 过滤
                    val filtered = r.data.items.filter { share ->
                        share.id == shareId && share.item?.parentId == parentItemId.takeIf { it.isNotEmpty() }
                    }
                    ui.copy(
                        items = filtered.ifEmpty { r.data.items.filter { it.id == shareId } },
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

    companion object {
        const val ARG_SHARE_ID = "shareId"
        const val ARG_ITEM_ID = "itemId"
    }
}
