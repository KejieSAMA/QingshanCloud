package com.x.cloudnext.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens

/**
 * 可展开搜索的顶栏组件。M6 风格统一新增 —— 替换 OutlinedTextField + AnimatedVisibility 拼装方案。
 *
 * 视觉对照 cloudnext-web mobile.css .m-topbar 与 .search-input：
 *  - **折叠态**：等价 CnTopBar（48dp + status bar inset + navigationIcon + 标题 + actions）
 *  - **展开态**：左 IconButton 返回 + 中部 38dp 圆角搜索 chip + 右 IconButton 清除/收回
 *
 * 搜索 chip 用 BasicTextField + Surface decoration（不用 Material3 OutlinedTextField，
 * 因后者 label-aware 自带 56dp 高度，会撑爆 TopBar 区域 — 这是 user 反馈"搜索框与
 * TopBar 重叠"的根因）。
 *
 * 自动 focus：LaunchedEffect(expanded) 监听切到展开时 requestFocus。
 *
 * 调用方持 expanded state（rememberSaveable 跨旋转保留）。
 */
@Composable
fun CnSearchTopBar(
    expanded: Boolean,
    keyword: String,
    placeholder: String,
    onExpandedChange: (Boolean) -> Unit,
    onKeywordChange: (String) -> Unit,
    onSearch: (String) -> Unit = {},
    title: String,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
    showBackOnExpand: Boolean = true,
) {
    val tokens = LocalCnTokens.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(expanded) {
        if (expanded) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(tokens.dimensions.topBarHeight),
        ) {
            if (expanded) {
                // 展开态：返回 + 搜索 chip + 清除
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = tokens.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
                ) {
                    if (showBackOnExpand) {
                        IconButton(onClick = {
                            onKeywordChange("")
                            onExpandedChange(false)
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "返回",
                                tint = CnPalette.TextPrimary,
                            )
                        }
                    }
                    SearchChip(
                        keyword = keyword,
                        placeholder = placeholder,
                        onKeywordChange = onKeywordChange,
                        onSearch = onSearch,
                        focusRequester = focusRequester,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                // 折叠态：左 navigationIcon + 中标题 + 右 actions
                if (true) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 4.dp),
                    ) { navigationIcon() }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions?.invoke()
                    IconButton(onClick = { onExpandedChange(true) }) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "搜索",
                            tint = CnPalette.TextPrimary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 38dp 圆角搜索 chip。BasicTextField + Box decoration。
 *
 * 对照 mobile.css .search-input：
 *   height 38px, border-radius 19px, background var(--bg-panel-soft)
 *   左 16dp 搜索 icon + flex-grow input + 右 24dp 清除按钮（输入非空时显示）
 */
@Composable
private fun SearchChip(
    keyword: String,
    placeholder: String,
    onKeywordChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalCnTokens.current
    Surface(
        modifier = modifier.height(tokens.dimensions.searchChipHeight),
        color = CnPalette.PanelSoft,
        // 显式 pill：shapes.extraLarge 已改回 28dp（Dialog 默认），这里不能跟。
        shape = RoundedCornerShape(percent = 50),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = CnPalette.TextTertiary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(tokens.spacing.sm))
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = keyword,
                    onValueChange = onKeywordChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        color = CnPalette.TextPrimary,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    ),
                    cursorBrush = SolidColor(CnPalette.Primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(keyword) }),
                )
                if (keyword.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = CnPalette.TextTertiary,
                    )
                }
            }
            if (keyword.isNotEmpty()) {
                Spacer(modifier = Modifier.size(tokens.spacing.sm))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(CnPalette.OutlineStrong, RoundedCornerShape(percent = 50)),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = { onKeywordChange("") },
                        modifier = Modifier.size(20.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Cancel,
                            contentDescription = "清除",
                            tint = CnPalette.Surface,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}
