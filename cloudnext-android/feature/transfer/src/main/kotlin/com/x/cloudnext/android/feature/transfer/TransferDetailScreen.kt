package com.x.cloudnext.android.feature.transfer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.common.formatFileSize
import com.x.cloudnext.android.data.transfer.TransferStatus
import com.x.cloudnext.android.data.transfer.TransferTask
import com.x.cloudnext.android.data.transfer.TransferType
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens
import com.x.cloudnext.android.ui.CnEmpty
import com.x.cloudnext.android.ui.CnTopBar

/**
 * 单个传输任务详情。看完整字段、错误信息可复制、能重试/取消/删除。
 */
@Composable
fun TransferDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransferDetailViewModel = hiltViewModel(),
) {
    val task by viewModel.task.collectAsState()
    val closed by viewModel.closed.collectAsState()
    val context = LocalContext.current
    val tokens = LocalCnTokens.current

    LaunchedEffect(closed) { if (closed) onBack() }

    Column(modifier = modifier.fillMaxSize()) {
        CnTopBar(
            title = "任务详情",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = CnPalette.TextPrimary,
                    )
                }
            },
        )
        HorizontalDivider(color = CnPalette.OutlineSoft)

        val t = task
        if (t == null) {
            CnEmpty(message = "任务不存在或已被清理")
            return@Column
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(tokens.spacing.md),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            ) {
                InfoCard(task = t)
                ErrorCard(task = t, onCopy = { copy(context, it) })
                ActionsRow(
                    task = t,
                    onRetry = { viewModel.retry() },
                    onCancel = { viewModel.cancel() },
                    onDelete = { viewModel.delete() },
                )
            }
        }
    }
}

@Composable
private fun InfoCard(task: TransferTask) {
    val tokens = LocalCnTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(tokens.spacing.md)) {
            Text("基本信息", style = MaterialTheme.typography.titleMedium, color = CnPalette.TextPrimary)
            Row(modifier = Modifier.padding(top = tokens.spacing.sm)) {}
            InfoRow("文件名", task.displayName)
            InfoRow("类型", if (task.type == TransferType.DOWNLOAD) "下载" else "上传")
            InfoRow("状态", task.displayLabel())
            if (task.totalBytes > 0) {
                InfoRow("大小", formatFileSize(task.totalBytes))
            }
            InfoRow("已传输", formatFileSize(task.transferredBytes))
            if (task.type == TransferType.DOWNLOAD) {
                task.outputUri?.takeIf { it.isNotBlank() }?.let { InfoRow("落盘 URI", it) }
                InfoRow("落盘位置", "Downloads/CloudNext/")
            } else {
                InfoRow("目标目录", task.parentId.ifEmpty { "（根目录）" })
            }
            task.conflictStrategy?.let { InfoRow("冲突策略", it.raw) }
            InfoRow("任务 ID", task.id)
        }
    }
}

@Composable
private fun ErrorCard(task: TransferTask, onCopy: (String) -> Unit) {
    val message = task.errorMessage?.takeIf { it.isNotBlank() } ?: return
    val tokens = LocalCnTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(tokens.spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("错误信息", style = MaterialTheme.typography.titleMedium, color = CnPalette.Error)
                OutlinedButton(onClick = { onCopy(message) }) {
                    Text("复制")
                }
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = CnPalette.TextSecondary,
                modifier = Modifier.padding(top = tokens.spacing.sm),
            )
        }
    }
}

@Composable
private fun ActionsRow(
    task: TransferTask,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = LocalCnTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        val isDownload = task.type == TransferType.DOWNLOAD
        when (task.status) {
            TransferStatus.QUEUED, TransferStatus.RUNNING -> {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(if (isDownload) "取消下载" else "取消上传")
                }
            }
            TransferStatus.FAILED, TransferStatus.CONFLICT -> {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                ) { Text("重试") }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CnPalette.Error),
                ) { Text("删除") }
            }
            TransferStatus.SUCCEEDED, TransferStatus.CANCELED -> {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CnPalette.Error),
                ) { Text("删除") }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val tokens = LocalCnTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = CnPalette.TextSecondary,
            modifier = Modifier.padding(end = tokens.spacing.md),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = CnPalette.TextPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun TransferTask.displayLabel(): String {
    val isDownload = type == TransferType.DOWNLOAD
    return when (status) {
        TransferStatus.QUEUED -> "等待中"
        TransferStatus.RUNNING -> if (isDownload) "下载中" else "上传中"
        TransferStatus.SUCCEEDED -> if (isDownload) "已下载到 Downloads/CloudNext" else "已完成"
        TransferStatus.FAILED -> "失败"
        TransferStatus.CONFLICT -> "需选择冲突处理"
        TransferStatus.CANCELED -> "已取消"
    }
}

private fun copy(context: Context, text: String) {
    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clip.setPrimaryClip(ClipData.newPlainText("cloudnext.transfer.error", text))
    Toast.makeText(context, "已复制错误信息", Toast.LENGTH_SHORT).show()
}
