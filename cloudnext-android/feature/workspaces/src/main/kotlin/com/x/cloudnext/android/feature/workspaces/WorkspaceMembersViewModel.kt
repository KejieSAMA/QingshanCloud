package com.x.cloudnext.android.feature.workspaces

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.data.workspace.WorkspaceMember
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

data class MembersUiState(
    val members: List<WorkspaceMember> = emptyList(),
    val myRole: WorkspaceRole = WorkspaceRole.Viewer,
    val myPersonOaId: String? = null,
    val loading: Boolean = true,
    val errorMessage: String? = null,
)

sealed interface MembersEvent {
    data class Toast(val message: String) : MembersEvent
}

@HiltViewModel
class WorkspaceMembersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workspaceRepository: WorkspaceRepository,
) : ViewModel() {

    val workspaceId: String = savedStateHandle.get<String>(ARG_WORKSPACE_ID).orEmpty()

    private val _state = MutableStateFlow(MembersUiState())
    val state: StateFlow<MembersUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<MembersEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<MembersEvent> = _events.asSharedFlow()

    init {
        refresh()
        loadMyRole()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val r = workspaceRepository.listMembers(workspaceId)) {
                is CnResult.Success -> _state.update {
                    it.copy(members = r.data, loading = false)
                }
                is CnResult.Failure -> _state.update {
                    it.copy(loading = false, errorMessage = r.error.message)
                }
            }
        }
    }

    /** 从 detail 拿 myRole（OWNER/ADMIN/MEMBER/VIEWER）。 */
    private fun loadMyRole() {
        viewModelScope.launch {
            when (val r = workspaceRepository.getDetail(workspaceId)) {
                is CnResult.Success -> _state.update {
                    it.copy(
                        myRole = r.data.myRole,
                        myPersonOaId = r.data.creatorPersonOaId,
                    )
                }
                is CnResult.Failure -> Unit
            }
        }
    }

    fun addMember(personOaId: String, personName: String) {
        viewModelScope.launch {
            // 默认添加为 MEMBER 角色
            when (val r = workspaceRepository.addMember(workspaceId, personOaId, personName, WorkspaceRole.Member)) {
                is CnResult.Success -> {
                    _events.tryEmit(MembersEvent.Toast("已添加：${r.data.personName}"))
                    refresh()
                }
                is CnResult.Failure -> _events.tryEmit(MembersEvent.Toast(r.error.message))
            }
        }
    }

    fun updateRole(memberId: String, role: WorkspaceRole) {
        viewModelScope.launch {
            when (val r = workspaceRepository.updateMemberRole(workspaceId, memberId, role)) {
                is CnResult.Success -> {
                    _events.tryEmit(MembersEvent.Toast("已更新角色"))
                    refresh()
                }
                is CnResult.Failure -> _events.tryEmit(MembersEvent.Toast(r.error.message))
            }
        }
    }

    fun removeMember(memberId: String) {
        viewModelScope.launch {
            when (val r = workspaceRepository.removeMember(workspaceId, memberId)) {
                is CnResult.Success -> {
                    _events.tryEmit(MembersEvent.Toast("已移除成员"))
                    refresh()
                }
                is CnResult.Failure -> _events.tryEmit(MembersEvent.Toast(r.error.message))
            }
        }
    }

    companion object {
        const val ARG_WORKSPACE_ID = "workspaceId"
    }
}
