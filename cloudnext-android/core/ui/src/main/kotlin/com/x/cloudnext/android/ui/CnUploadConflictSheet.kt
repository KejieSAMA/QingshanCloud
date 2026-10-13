package com.x.cloudnext.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens

/** 用户在冲突弹层做的选择。Abort = 取消上传，KeepBoth = 保留两个，Replace = 替换。 */
enum class UploadConflictChoice { KeepBoth, Replace, Abort }

/**
 * 上传同名冲突 sheet。三选项：保留两个 / 替换原文件 / 取消上传。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CnUploadConflictSheet(
    fileName: String,
    onChoose: (UploadConflictChoice) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = LocalCnTokens.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.md)) {
                Text(
                    text = "文件已存在",
                    style = MaterialTheme.typography.titleMedium,
                    color = CnPalette.TextPrimary,
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.labelMedium,
                    color = CnPalette.TextSecondary,
                )
            }
            HorizontalDivider(color = CnPalette.OutlineSoft)
            ChoiceRow(
                icon = Icons.Outlined.ContentCopy,
                label = "保留两个",
                hint = "服务端自动重命名为 \"$fileName (1)\"",
                tintNormal = true,
            ) { onChoose(UploadConflictChoice.KeepBoth) }
            HorizontalDivider(color = CnPalette.DividerHairline)
            ChoiceRow(
                icon = Icons.Outlined.SwapHoriz,
                label = "替换原文件",
                hint = "原文件将被覆盖",
                tintNormal = false,
            ) { onChoose(UploadConflictChoice.Replace) }
            HorizontalDivider(color = CnPalette.DividerHairline)
            ChoiceRow(
                icon = Icons.Outlined.Close,
                label = "取消上传",
                hint = null,
                tintNormal = true,
            ) { onChoose(UploadConflictChoice.Abort) }
            Spacer(modifier = Modifier.height(tokens.spacing.lg))
        }
    }
}

@Composable
private fun ChoiceRow(
    icon: ImageVector,
    label: String,
    hint: String?,
    tintNormal: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalCnTokens.current
    val color = if (tintNormal) CnPalette.TextPrimary else CnPalette.Error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = color)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = color)
            if (!hint.isNullOrBlank()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = CnPalette.TextTertiary,
                )
            }
        }
    }
}

