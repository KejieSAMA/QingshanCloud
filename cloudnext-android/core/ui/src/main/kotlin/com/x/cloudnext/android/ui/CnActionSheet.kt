package com.x.cloudnext.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens

enum class CnActionRole { Default, Destructive }

data class CnActionItem(
    val icon: ImageVector,
    val title: String,
    val role: CnActionRole = CnActionRole.Default,
    val enabled: Boolean = true,
)

/**
 * 底部操作菜单。1:1 视觉对照 cloudnext-web MobileActionSheet.vue：
 *
 * 结构：
 *  - 顶部拖把手（40×4dp 圆角灰条）
 *  - 可选标题（小字 Tertiary）
 *  - 操作列表（48dp 高 + icon + 文字 + 触觉热区）
 *  - 可选独立"取消"卡片（与列表 12dp 间距，PanelSoft 背景）
 *  - 底部 navigation bar inset 自动避让
 *
 * 修复（M6）：
 *  - 加 drag handle，与 Web 一致
 *  - 加可选取消卡（showCancelButton 默认 true）
 *  - windowInsets fix：内容用 windowInsetsPadding(navigationBars)，避免被系统导航栏遮挡
 *  - shape = MaterialTheme.shapes.large（16dp 顶部圆角）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CnActionSheet(
    title: String?,
    actions: List<CnActionItem>,
    onActionClick: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showCancelButton: Boolean = true,
    cancelLabel: String = "取消",
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = LocalCnTokens.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large.copy(
            bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
            bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp),
        ),
        // 自定义 drag handle（40×4dp 圆角灰条），与 mobile.css .m-action-sheet 一致
        dragHandle = { CnSheetDragHandle() },
        contentWindowInsets = { WindowInsets(0) },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        ) {
            // 标题（可选）
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = CnPalette.TextTertiary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.sm),
                )
            }

            // 操作列表 - 整体 Surface 卡片
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column {
                    actions.forEachIndexed { index, action ->
                        ActionRow(action = action) { onActionClick(index) }
                        if (index < actions.lastIndex) {
                            HorizontalDivider(
                                color = CnPalette.DividerHairline,
                                modifier = Modifier.padding(start = tokens.spacing.lg),
                            )
                        }
                    }
                }
            }

            // 取消卡（独立 Surface，浅灰底）
            if (showCancelButton) {
                Spacer(modifier = Modifier.height(tokens.spacing.md))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = CnPalette.PanelSoft,
                    shape = MaterialTheme.shapes.medium,
                    onClick = onDismiss,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(tokens.touch.comfort),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = cancelLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = CnPalette.TextPrimary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(tokens.spacing.sm))
        }
    }
}

/**
 * Sheet 顶部拖把手。40×4dp 圆角灰条，与 mobile.css 一致。
 */
@Composable
private fun CnSheetDragHandle() {
    val tokens = LocalCnTokens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = tokens.dimensions.sheetDragHandleWidth,
                    height = tokens.dimensions.sheetDragHandleHeight,
                )
                .background(
                    color = CnPalette.OutlineStrong,
                    shape = RoundedCornerShape(2.dp),
                ),
        )
    }
}

@Composable
private fun ActionRow(action: CnActionItem, onClick: () -> Unit) {
    val tokens = LocalCnTokens.current
    val color = when {
        !action.enabled -> CnPalette.TextTertiary
        action.role == CnActionRole.Destructive -> CnPalette.Error
        else -> CnPalette.TextPrimary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(tokens.touch.comfort)
            .clickable(enabled = action.enabled, onClick = onClick)
            .padding(horizontal = tokens.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        Icon(imageVector = action.icon, contentDescription = action.title, tint = color)
        Text(text = action.title, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}
