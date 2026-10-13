package com.x.cloudnext.android.feature.preview.office

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.data.preview.PreviewRepository
import com.x.cloudnext.android.datastore.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface OfficeUiState {
    data object Loading : OfficeUiState
    data class Ready(val url: String, val token: String?) : OfficeUiState
    data class Failed(val message: String) : OfficeUiState
}

@HiltViewModel
class OnlyofficeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val previewRepository: PreviewRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val itemId: String = savedStateHandle.get<String>(ARG_ITEM_ID).orEmpty()

    private val _state = MutableStateFlow<OfficeUiState>(OfficeUiState.Loading)
    val state: StateFlow<OfficeUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        _state.value = OfficeUiState.Loading
        viewModelScope.launch {
            when (val r = previewRepository.getPreview(itemId)) {
                is CnResult.Success -> {
                    val preview = r.data
                    val url = preview.previewPageUrl
                    if (url.isNullOrBlank()) {
                        _state.value = OfficeUiState.Failed(
                            preview.unavailableMessage ?: "未配置 OnlyOffice 预览",
                        )
                    } else {
                        _state.value = OfficeUiState.Ready(url, tokenStore.current)
                    }
                }
                is CnResult.Failure -> _state.value = OfficeUiState.Failed(r.error.message)
            }
        }
    }

    fun retry() = load()

    companion object {
        const val ARG_ITEM_ID = "itemId"
    }
}
