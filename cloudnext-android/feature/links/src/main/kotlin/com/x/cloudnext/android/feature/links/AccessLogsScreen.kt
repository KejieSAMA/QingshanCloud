package com.x.cloudnext.android.feature.links

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.data.link.LinkAccessLog
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens
import com.x.cloudnext.android.ui.CnEmpty
import com.x.cloudnext.android.ui.CnError
import com.x.cloudnext.android.ui.CnTopBar
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 外链访问记录页（顶层 fullscreen）。
 *
 * 顶部摘要卡片：总访问 / 成功 / 失败 三个 chip。
 * 下方 LazyColumn 渲染日志条目，滚到底自动加载下一页。
 */
@Composable
fun AccessLogsScreen(
    fileName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccessLogsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val tokens = LocalCnTokens.current
    val listState = rememberLazyListState()

    val loadMoreSignal by remember {
        derivedStateOf {
            val total = state.logs.size
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 3 && state.hasNext && !state.loadingMore
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { loadMoreSignal }
            .distinctUntilChanged()
            .collect { signal -> if (signal) viewModel.loadMore() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        CnTopBar(
            title = "访问记录",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = CnPalette.TextPrimary)
                }
            },
        )
        HorizontalDivider(color = CnPalette.OutlineSoft)

        // 摘要卡
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.md),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(modifier = Modifier.padding(tokens.spacing.md)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = CnPalette.TextPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = tokens.spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                ) {
                    SummaryChip("总访问", state.totalCount, CnPalette.TextPrimary, CnPalette.SurfaceVariant)
                    SummaryChip("成功", state.successCount, CnPalette.Success, CnPalette.PrimarySoft)
                    SummaryChip("失败", state.failCount, CnPalette.Error, CnPalette.DangerSoft)
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when {
                state.logs.isEmpty() && state.errorMessage != null ->
                    CnError(message = state.errorMessage!!, onRetry = { viewModel.refresh() })
                state.logs.isEmpty() && !state.loading ->
                    CnEmpty(message = "暂无访问记录", hint = "外链被使用后会显示在这里")
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.logs, key = { it.id }) { log ->
                        LogRow(log = log)
                        HorizontalDivider(color = CnPalette.DividerHairline)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(label: String, value: Long, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    val tokens = LocalCnTokens.current
    Column(
        modifier = Modifier
            .background(bg, MaterialTheme.shapes.small)
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg)
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = fg,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LogRow(log: LinkAccessLog) {
    val tokens = LocalCnTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = log.actionType.ifBlank { "访问" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = CnPalette.TextPrimary,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (log.success) "  ✓ 成功" else "  ✗ ${log.failReason ?: "失败"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (log.success) CnPalette.Success else CnPalette.Error,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            log.ipAddress?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = CnPalette.TextTertiary)
            }
            log.userAgent?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = CnPalette.TextTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            log.attemptedAt?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = CnPalette.TextTertiary)
            }
        }
    }
}
