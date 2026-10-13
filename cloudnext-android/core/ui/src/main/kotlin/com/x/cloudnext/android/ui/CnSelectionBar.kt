package com.x.cloudnext.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens

/**
 * 多选模式底部操作栏。对齐 cloudnext-web MobileSelectionBar.vue。
 *
 * 左侧"全选/取消全选" + 中间"已选 N 项" + 右侧操作按钮（移动 / 删除）。
 * Safe Bottom 自动避开导航栏。
 */
/**
 * 多选模式底部操作栏。
 *
 * 支持的操作（可选注入）：
 * - onMove / onDelete：MyFiles / 共享 / 工作空间 通用
 * - onDownload：MyFiles 批量下载
 * - onShare：MyFiles 批量发起分享
 * - onRestore：回收站「恢复」
 * - onPurge：回收站「彻底删除」红色
 *
 * 不为 null 的回调才会渲染对应按钮，方便不同 Screen 复用同一组件。
 */
@Composable
fun CnSelectionBar(
    selectedCount: Int,
    allSelected: Boolean,
    modifier: Modifier = Modifier,
    onToggleSelectAll: () -> Unit,
    onMove: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    onPurge: (() -> Unit)? = null,
) {
    val tokens = LocalCnTokens.current
    val anySelected = selectedCount > 0
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        HorizontalDivider(color = CnPalette.OutlineSoft)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(tokens.dimensions.bottomBarHeight)
                .padding(horizontal = tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (allSelected) "取消全选" else "全选",
                style = MaterialTheme.typography.labelLarge,
                color = CnPalette.Primary,
                modifier = Modifier
                    .clickable(onClick = onToggleSelectAll)
                    .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xs),
            )
            Spacer(modifier = Modifier.size(tokens.spacing.md))
            Text(
                text = "已选 $selectedCount 项",
                style = MaterialTheme.typography.bodyMedium,
                color = CnPalette.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (onRestore != null) {
                IconButton(onClick = onRestore, enabled = anySelected) {
                    Icon(
                        imageVector = Icons.Outlined.RestoreFromTrash,
                        contentDescription = "恢复",
                        tint = if (anySelected) CnPalette.Primary else CnPalette.TextTertiary,
                    )
                }
            }
            if (onPurge != null) {
                IconButton(onClick = onPurge, enabled = anySelected) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteForever,
                        contentDescription = "彻底删除",
                        tint = if (anySelected) CnPalette.Error else CnPalette.TextTertiary,
                    )
                }
            }
            if (onShare != null) {
                IconButton(onClick = onShare, enabled = anySelected) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "分享",
                        tint = if (anySelected) CnPalette.TextPrimary else CnPalette.TextTertiary,
                    )
                }
            }
            if (onDownload != null) {
                IconButton(onClick = onDownload, enabled = anySelected) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = "下载",
                        tint = if (anySelected) CnPalette.TextPrimary else CnPalette.TextTertiary,
                    )
                }
            }
            if (onMove != null) {
                IconButton(onClick = onMove, enabled = anySelected) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.DriveFileMove,
                        contentDescription = "移动",
                        tint = if (anySelected) CnPalette.TextPrimary else CnPalette.TextTertiary,
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, enabled = anySelected) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "删除",
                        tint = if (anySelected) CnPalette.Error else CnPalette.TextTertiary,
                    )
                }
            }
        }
    }
}

