package com.x.cloudnext.android.feature.transfer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.data.transfer.TransferRepository
import com.x.cloudnext.android.data.transfer.TransferTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransferDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transferRepository: TransferRepository,
) : ViewModel() {

    private val taskId: String = savedStateHandle.get<String>(ARG_TASK_ID).orEmpty()

    val task: StateFlow<TransferTask?> = transferRepository.observeAll()
        .map { list -> list.firstOrNull { it.id == taskId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _closed = MutableStateFlow(false)
    val closed: StateFlow<Boolean> = _closed.asStateFlow()

    fun retry() {
        viewModelScope.launch { transferRepository.retry(taskId) }
    }

    fun cancel() {
        viewModelScope.launch { transferRepository.cancel(taskId) }
    }

    /** 真正删除任务记录（区别于 cancel，cancel 仅改状态）。 */
    fun delete() {
        viewModelScope.launch {
            // TransferRepository 没暴露 delete 单条 API，借助 cancel 标记 + clearFinished 清理
            transferRepository.cancel(taskId)
            transferRepository.clearFinished()
            _closed.value = true
        }
    }

    companion object {
        const val ARG_TASK_ID = "taskId"
    }
}
