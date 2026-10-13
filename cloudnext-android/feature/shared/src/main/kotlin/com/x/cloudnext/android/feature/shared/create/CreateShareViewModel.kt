package com.x.cloudnext.android.feature.shared.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.common.CnResult
import com.x.cloudnext.android.data.share.ShareRepository
import com.x.cloudnext.android.data.share.SharePermission
import com.x.cloudnext.android.data.share.ShareTarget
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/** 预设有效期。custom 仅占位，M5a 不实装 DatePicker（M5b 可加）。 */
enum class ExpiryPreset(val label: String, val days: Int) {
    Forever("永久", 0),
    Days7("7 天", 7),
    Days30("30 天", 30),
}

data class CreateShareUiState(
    val itemIds: List<String> = emptyList(),
    val targets: List<ShareTarget> = emptyList(),
    val permission: SharePermission = SharePermission.Read,
    val expiry: ExpiryPreset = ExpiryPreset.Forever,
    val canReshare: Boolean = false,
    val submitting: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface CreateShareEvent {
    data class Toast(val message: String) : CreateShareEvent
    data object Submitted : CreateShareEvent
}

@HiltViewModel
class CreateShareViewModel @Inject constructor(
    private val shareRepository: ShareRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateShareUiState())
    val state: StateFlow<CreateShareUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CreateShareEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<CreateShareEvent> = _events.asSharedFlow()

    fun setItemIds(ids: List<String>) {
        if (ids == _state.value.itemIds) return
        _state.update { it.copy(itemIds = ids) }
    }

    fun setTargets(targets: List<ShareTarget>) {
        _state.update { it.copy(targets = targets) }
    }

    fun removeTarget(target: ShareTarget) {
        _state.update {
            it.copy(targets = it.targets.filterNot { t -> t.id == target.id && t.type == target.type })
        }
    }

    fun setPermission(permission: SharePermission) {
        _state.update { it.copy(permission = permission) }
    }

    fun setExpiry(expiry: ExpiryPreset) {
        _state.update { it.copy(expiry = expiry) }
    }

    fun toggleReshare() {
        _state.update { it.copy(canReshare = !it.canReshare) }
    }

    fun submit() {
        val s = _state.value
        if (s.itemIds.isEmpty()) {
            emit(CreateShareEvent.Toast("缺少被分享的文件"))
            return
        }
        if (s.targets.isEmpty()) {
            emit(CreateShareEvent.Toast("请选择共享对象"))
            return
        }
        _state.update { it.copy(submitting = true, errorMessage = null) }
        viewModelScope.launch {
            val expiresAt = expiresAtIso(s.expiry)
            when (val r = shareRepository.createShares(
                itemIds = s.itemIds,
                targets = s.targets,
                permission = s.permission,
                expiresAt = expiresAt,
                canReshare = s.canReshare,
            )) {
                is CnResult.Success -> {
                    _state.update { it.copy(submitting = false) }
                    val ok = r.data.totalSucceeded
                    val fail = r.data.totalFailed
                    val msg = when {
                        fail == 0 && ok > 0 -> "已创建分享（$ok 条）"
                        ok == 0 -> "创建失败（$fail 条）"
                        else -> "成功 $ok / 失败 $fail"
                    }
                    emit(CreateShareEvent.Toast(msg))
                    emit(CreateShareEvent.Submitted)
                }
                is CnResult.Failure -> {
                    _state.update { it.copy(submitting = false, errorMessage = r.error.message) }
                    emit(CreateShareEvent.Toast(r.error.message))
                }
            }
        }
    }

    private fun expiresAtIso(preset: ExpiryPreset): String? {
        if (preset == ExpiryPreset.Forever) return null
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.time = Date()
        cal.add(Calendar.DAY_OF_YEAR, preset.days)
        // ISO-8601 with Z suffix
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(cal.time)
    }

    private fun emit(event: CreateShareEvent) {
        _events.tryEmit(event)
    }
}
