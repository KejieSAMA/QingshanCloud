package com.x.cloudnext.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.x.cloudnext.android.common.formatFileSize
import com.x.cloudnext.android.common.guessFileKind
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens

/**
 * 传输任务卡。展示文件图标 + 名称 + 进度条 + 状态 + 操作按钮。
 *
 * 操作按钮按 [status] 切换：
 * - QUEUED / RUNNING：取消
 * - CONFLICT：点击行体（外部处理）打开冲突弹窗，此处不显示操作按钮
 * - FAILED：重试
 * - SUCCEEDED / CANCELED：不显示按钮（终态）
 */
@Composable
fun CnTransferTaskItem(
    fileName: String,
    statusLabel: String,
    statusColor: androidx.compose.ui.graphics.Color,
    progressFraction: Float,
    showProgress: Boolean,
    primaryActionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    primaryActionDesc: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    sizeBytes: Long? = null,
) {
    val tokens = LocalCnTokens.current
    val kind = guessFileKind(fileName)
    val iconBg = tokens.fileIcon.background(kind)
    val iconFg = tokens.fileIcon.foreground(kind)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(tokens.dimensions.fileIconBox)
                .background(iconBg, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = kind.first().uppercase(),
                color = iconFg,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyLarge,
                color = CnPalette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showProgress) {
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = CnPalette.Primary,
                    trackColor = CnPalette.PrimarySoft,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                )
                if (sizeBytes != null && sizeBytes > 0) {
                    Text(
                        text = "·  ${formatFileSize(sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CnPalette.TextTertiary,
                    )
                }
            }
        }

        if (primaryActionIcon != null && onPrimaryAction != null) {
            IconButton(onClick = onPrimaryAction) {
                Icon(
                    imageVector = primaryActionIcon,
                    contentDescription = primaryActionDesc,
                    tint = CnPalette.TextSecondary,
                )
            }
        } else {
            Spacer(modifier = Modifier.size(4.dp))
        }
    }
}

/** 取消按钮的预设。 */
object CnTransferActions {
    val Cancel = Icons.Outlined.Close
    val Retry = Icons.Outlined.Refresh
}
