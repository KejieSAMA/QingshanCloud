package com.x.cloudnext.android.feature.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.data.transfer.ConflictStrategy
import com.x.cloudnext.android.data.transfer.TransferRepository
import com.x.cloudnext.android.data.transfer.TransferTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransferViewModel @Inject constructor(
    private val transferRepository: TransferRepository,
) : ViewModel() {

    val tasks: StateFlow<List<TransferTask>> = transferRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cancel(taskId: String) {
        viewModelScope.launch { transferRepository.cancel(taskId) }
    }

    fun retry(taskId: String) {
        viewModelScope.launch { transferRepository.retry(taskId) }
    }

    fun resolveConflict(taskId: String, strategy: ConflictStrategy) {
        viewModelScope.launch { transferRepository.resolveConflict(taskId, strategy) }
    }

    fun clearFinished() {
        viewModelScope.launch { transferRepository.clearFinished() }
    }
}
