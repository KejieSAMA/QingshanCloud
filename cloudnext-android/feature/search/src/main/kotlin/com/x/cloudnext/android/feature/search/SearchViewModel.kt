package com.x.cloudnext.android.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.data.drive.DriveItem
import com.x.cloudnext.android.data.search.SearchFileType
import com.x.cloudnext.android.data.search.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 搜索状态机：
 *  - History：未提交关键词，显示历史词列表
 *  - Searching：调远程中
 *  - Results：有结果或空结果
 *
 * 触发条件：用户在 SearchTextField 按 IME Search、或点击历史词项目。
 * filter（fileType）切换实时重搜（只要 keyword 非空）。
 */
enum class SearchMode { History, Searching, Results }

data class SearchUiState(
    val keyword: String = "",
    val fileType: SearchFileType = SearchFileType.All,
    val mode: SearchMode = SearchMode.History,
    val items: List<DriveItem> = emptyList(),
    val page: Int = 1,
    val hasNext: Boolean = false,
    val loadingMore: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    val history: StateFlow<List<String>> = searchRepository.historyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // -------------------- TextField 输入 --------------------

    fun setKeyword(text: String) {
        _state.update {
            // 清空回到 History 模式；非空则保留当前模式，等用户 commit 才搜
            it.copy(
                keyword = text,
                mode = if (text.isBlank()) SearchMode.History else it.mode,
            )
        }
    }

    fun submitKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { searchRepository.rememberKeyword(trimmed) }
        runSearch(trimmed, _state.value.fileType, page = 1, replace = true)
    }

    fun setFileType(type: SearchFileType) {
        if (type == _state.value.fileType) return
        _state.update { it.copy(fileType = type) }
        val keyword = _state.value.keyword.trim()
        if (keyword.isNotEmpty()) runSearch(keyword, type, page = 1, replace = true)
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || !s.hasNext) return
        _state.update { it.copy(loadingMore = true) }
        runSearch(s.keyword.trim(), s.fileType, page = s.page + 1, replace = false)
    }

    private fun runSearch(keyword: String, fileType: SearchFileType, page: Int, replace: Boolean) {
        if (replace) _state.update { it.copy(mode = SearchMode.Searching, errorMessage = null) }
        viewModelScope.launch {
            when (val r = searchRepository.search(keyword, fileType = fileType, page = page)) {
                is CnResult.Success -> _state.update {
                    it.copy(
                        items = if (replace) r.data.items else it.items + r.data.items,
                        page = r.data.page,
                        hasNext = r.data.hasNext,
                        mode = SearchMode.Results,
                        loadingMore = false,
                        errorMessage = null,
                    )
                }
                is CnResult.Failure -> _state.update {
                    it.copy(
                        mode = if (replace) SearchMode.Results else it.mode,
                        loadingMore = false,
                        errorMessage = r.error.message,
                    )
                }
            }
        }
    }

    // -------------------- 历史 --------------------

    fun removeHistory(keyword: String) {
        viewModelScope.launch { searchRepository.removeKeyword(keyword) }
    }

    fun clearHistory() {
        viewModelScope.launch { searchRepository.clearHistory() }
    }

    /** 点击历史词：填入 + 立即搜。 */
    fun pickHistory(keyword: String) {
        _state.update { it.copy(keyword = keyword) }
        submitKeyword(keyword)
    }

    /** 清空当前关键词，回 History 模式。 */
    fun clearKeyword() {
        _state.update {
            it.copy(
                keyword = "",
                mode = SearchMode.History,
                items = emptyList(),
                page = 1,
                hasNext = false,
                errorMessage = null,
            )
        }
    }
}
