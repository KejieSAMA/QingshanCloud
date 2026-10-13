package com.x.cloudnext.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.x.cloudnext.android.designsystem.LocalCnTokens

/**
 * 顶部导航栏。视觉对齐 cloudnext-web MobileTopbar.vue 的栅栏：
 *   `grid-template-columns: 44px 1fr 44px`（左槽 + 标题 + 右槽）。
 *
 * 高度 48dp + Safe Top；左槽固定 48dp，右槽 minWidth 48dp；
 * 标题严格限制在中间区域，maxLines=1 + ellipsis，**不会越界压到返回键**。
 *
 * 无 nav 且无 actions 的罕见调用（旧版兼容）：标题左对齐起始。
 */
@Composable
fun CnTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    val tokens = LocalCnTokens.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(tokens.dimensions.topBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左槽：固定 48dp。即使 navigationIcon == null 也保留占位，保持栅栏对齐。
            Box(
                modifier = Modifier.width(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (navigationIcon != null) navigationIcon()
            }
            // 中间标题：weight + maxLines + ellipsis，textAlign 居中
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (navigationIcon == null && actions == null) TextAlign.Start else TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = tokens.spacing.xs),
            )
            // 右槽：minWidth 48dp（保持栅栏），实际由 actions 内容自然撑开
            Box(
                modifier = Modifier.defaultMinSize(minWidth = 48.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (actions != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) { actions() }
                }
            }
        }
    }
}
