package com.x.cloudnext.android.feature.transfer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.data.transfer.ConflictStrategy
import com.x.cloudnext.android.data.transfer.TransferStatus
import com.x.cloudnext.android.data.transfer.TransferTask
import com.x.cloudnext.android.data.transfer.TransferType
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.ui.CnEmpty
import com.x.cloudnext.android.ui.CnTopBar
import com.x.cloudnext.android.ui.CnTransferActions
import com.x.cloudnext.android.ui.CnTransferTaskItem
import com.x.cloudnext.android.ui.CnUploadConflictSheet
import com.x.cloudnext.android.ui.UploadConflictChoice

/**
 * 传输任务中心。列出全部上传/下载任务，支持取消、重试、解决冲突、清空已完成。
 */
@Composable
fun TransferCenterScreen(
    onBack: () -> Unit,
    onOpenDetail: (taskId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransferViewModel = hiltViewModel(),
) {
    val tasks by viewModel.tasks.collectAsState()
    var conflictTaskId by remember { mutableStateOf<String?>(null) }
    val conflictTask = tasks.firstOrNull { it.id == conflictTaskId }

    Column(modifier = modifier.fillMaxSize()) {
        CnTopBar(
            title = "传输任务",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = CnPalette.TextPrimary,
                    )
                }
            },
            actions = {
                if (tasks.any { it.status.isFinal || it.status == TransferStatus.FAILED }) {
                    TextButton(onClick = { viewModel.clearFinished() }) {
                        Text("清空已完成", color = CnPalette.TextSecondary)
                    }
                }
            },
        )
        HorizontalDivider(color = CnPalette.OutlineSoft)

        if (tasks.isEmpty()) {
            CnEmpty(message = "还没有任务", hint = "在文件页用 + 上传文件后会在这里显示")
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(tasks, key = { it.id }) { task ->
                        TransferRow(
                            task = task,
                            onCancel = { viewModel.cancel(task.id) },
                            onRetry = { viewModel.retry(task.id) },
                            onClickConflict = { conflictTaskId = task.id },
                            onClickRow = { onOpenDetail(task.id) },
                        )
                        HorizontalDivider(color = CnPalette.DividerHairline)
                    }
                }
            }
        }
    }

    if (conflictTask != null) {
        CnUploadConflictSheet(
            fileName = conflictTask.displayName,
            onChoose = { choice ->
                val strategy = when (choice) {
                    UploadConflictChoice.KeepBoth -> ConflictStrategy.KeepBoth
                    UploadConflictChoice.Replace -> ConflictStrategy.Replace
                    UploadConflictChoice.Abort -> ConflictStrategy.Abort
                }
                viewModel.resolveConflict(conflictTask.id, strategy)
                conflictTaskId = null
            },
            onDismiss = { conflictTaskId = null },
        )
    }
}

@Composable
private fun TransferRow(
    task: TransferTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onClickConflict: () -> Unit,
    onClickRow: () -> Unit,
) {
    val isDownload = task.type == TransferType.DOWNLOAD
    val (label, color) = when (task.status) {
        TransferStatus.QUEUED -> "等待中" to CnPalette.TextTertiary
        TransferStatus.RUNNING -> (if (isDownload) "下载中" else "上传中") to CnPalette.Primary
        TransferStatus.SUCCEEDED -> (if (isDownload) "已下载到 Downloads/CloudNext" else "已上传") to CnPalette.Success
        TransferStatus.FAILED -> (task.errorMessage ?: "失败") to CnPalette.Error
        TransferStatus.CONFLICT -> "需选择冲突处理" to CnPalette.Warning
        TransferStatus.CANCELED -> "已取消" to CnPalette.TextTertiary
    }
    val (primaryIcon, primaryDesc, primaryAction) = when (task.status) {
        TransferStatus.QUEUED, TransferStatus.RUNNING -> Triple(CnTransferActions.Cancel, "取消", onCancel)
        TransferStatus.FAILED -> Triple(CnTransferActions.Retry, "重试", onRetry)
        else -> Triple(null, null, null)
    }
    val rowOnClick = if (task.status == TransferStatus.CONFLICT) onClickConflict else onClickRow

    CnTransferTaskItem(
        fileName = task.displayName,
        statusLabel = label,
        statusColor = color,
        progressFraction = task.progressFraction,
        showProgress = task.status == TransferStatus.RUNNING || task.status == TransferStatus.QUEUED,
        primaryActionIcon = primaryIcon,
        primaryActionDesc = primaryDesc,
        onPrimaryAction = primaryAction,
        onClick = rowOnClick,
        sizeBytes = task.totalBytes,
    )
}
