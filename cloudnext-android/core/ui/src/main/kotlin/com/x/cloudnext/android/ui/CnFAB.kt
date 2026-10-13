package com.x.cloudnext.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens

/**
 * FAB 操作项。
 * @param enabled false 时灰色 + 不响应。
 */
data class CnFabAction(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit = {},
)

/**
 * 浮动按钮。M6 风格统一：点击弹出 CnActionSheet 操作菜单（与 "..." 更多菜单视觉一致）。
 *
 * 摒弃之前的"悬浮按钮卡片组 + AnimatedVisibility"自实现，统一为底部 ActionSheet 模式。
 * 对齐 cloudnext-web MobileFAB.vue 点击后弹 .m-action-sheet 列表的行为。
 *
 * 用法：在外层 Box 的右下角放置；本组件自带 scrim + sheet。
 */
@Composable
fun CnFAB(
    actions: List<CnFabAction>,
    modifier: Modifier = Modifier,
    sheetTitle: String? = null,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val tokens = LocalCnTokens.current

    Box(modifier = modifier.fillMaxSize()) {
        FloatingActionButton(
            onClick = { sheetOpen = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = tokens.spacing.lg, bottom = tokens.spacing.lg),
            containerColor = CnPalette.Primary,
            contentColor = CnPalette.OnPrimary,
            shape = CircleShape,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "新建",
            )
        }
    }

    if (sheetOpen) {
        CnActionSheet(
            title = sheetTitle,
            actions = actions.map {
                CnActionItem(
                    icon = it.icon,
                    title = it.label,
                    role = CnActionRole.Default,
                    enabled = it.enabled,
                )
            },
            onActionClick = { index ->
                sheetOpen = false
                val action = actions.getOrNull(index)
                if (action != null && action.enabled) action.onClick()
            },
            onDismiss = { sheetOpen = false },
            showCancelButton = true,
        )
    }
}
