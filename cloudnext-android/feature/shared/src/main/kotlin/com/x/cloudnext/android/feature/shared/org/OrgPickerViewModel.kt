package com.x.cloudnext.android.feature.shared.org

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.data.org.OrgRepository
import com.x.cloudnext.android.data.org.OrgSearchResult
import com.x.cloudnext.android.data.share.ShareTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OrgPickerType(val raw: String) {
    Person("person"),
    Department("department"),
    ;

    fun toResultType(): OrgSearchResult.Type = when (this) {
        Person -> OrgSearchResult.Type.Person
        Department -> OrgSearchResult.Type.Department
    }
}

data class OrgPickerUiState(
    val type: OrgPickerType = OrgPickerType.Person,
    val keyword: String = "",
    val candidates: List<OrgSearchResult> = emptyList(),
    val selected: List<ShareTarget> = emptyList(),
    val loading: Boolean = false,
    val errorMessage: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class OrgPickerViewModel @Inject constructor(
    private val orgRepository: OrgRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OrgPickerUiState())
    val state: StateFlow<OrgPickerUiState> = _state.asStateFlow()

    /** keyword 输入触发的搜索 SharedFlow，debounce 300ms。 */
    private val keywordFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            keywordFlow
                .debounce(300L)
                .distinctUntilChanged()
                .collect { keyword ->
                    if (keyword.isBlank()) {
                        _state.update { it.copy(candidates = emptyList(), loading = false) }
                    } else {
                        runSearch(keyword)
                    }
                }
        }
    }

    fun setType(type: OrgPickerType) {
        if (type == _state.value.type) return
        _state.update { it.copy(type = type) }
        val k = _state.value.keyword
        if (k.isNotBlank()) runSearch(k)
    }

    fun setKeyword(text: String) {
        _state.update { it.copy(keyword = text) }
        keywordFlow.value = text
    }

    private fun runSearch(keyword: String) {
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val r = orgRepository.search(_state.value.type.toResultType(), keyword)) {
                is CnResult.Success -> _state.update {
                    it.copy(candidates = r.data, loading = false)
                }
                is CnResult.Failure -> _state.update {
                    it.copy(loading = false, errorMessage = r.error.message)
                }
            }
        }
    }

    fun add(result: OrgSearchResult) {
        val target = ShareTarget(
            type = when (result.type) {
                OrgSearchResult.Type.Person -> ShareTarget.TargetType.Person
                OrgSearchResult.Type.Department -> ShareTarget.TargetType.Department
            },
            id = result.id,
            name = result.name,
        )
        _state.update {
            // 同 id 去重
            if (it.selected.any { s -> s.id == target.id && s.type == target.type }) it
            else it.copy(selected = it.selected + target)
        }
    }

    fun remove(target: ShareTarget) {
        _state.update {
            it.copy(selected = it.selected.filterNot { s -> s.id == target.id && s.type == target.type })
        }
    }

    fun isSelected(result: OrgSearchResult): Boolean {
        val state = _state.value
        return state.selected.any { it.id == result.id && it.type.raw == result.type.raw }
    }

    val selectedSnapshot: List<ShareTarget> get() = _state.value.selected
}
