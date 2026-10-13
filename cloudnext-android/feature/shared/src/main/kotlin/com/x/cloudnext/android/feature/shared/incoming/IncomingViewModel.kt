package com.x.cloudnext.android.feature.shared.incoming

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

data class IncomingUiState(
    val items: List<DriveShare> = emptyList(),
    val page: Int = 1,
    val hasNext: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class IncomingViewModel @Inject constructor(
    private val shareRepository: ShareRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(IncomingUiState(loading = true))
    val state: StateFlow<IncomingUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, errorMessage = null, page = 1) }
        viewModelScope.launch {
            when (val r = shareRepository.listIncoming(page = 1)) {
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
            when (val r = shareRepository.listIncoming(page = s.page + 1)) {
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
}
