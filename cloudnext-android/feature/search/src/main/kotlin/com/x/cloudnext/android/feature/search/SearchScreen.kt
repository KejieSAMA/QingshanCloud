package com.x.cloudnext.android.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.data.drive.DriveItem
import com.x.cloudnext.android.data.search.SearchFileType
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens
import com.x.cloudnext.android.ui.CnEmpty
import com.x.cloudnext.android.ui.CnError
import com.x.cloudnext.android.ui.CnFileCard
import com.x.cloudnext.android.ui.CnFilterChipItem
import com.x.cloudnext.android.ui.CnFilterChips
import com.x.cloudnext.android.ui.CnLoading
import com.x.cloudnext.android.ui.CnSearchTopBar
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 搜索页（顶层 fullscreen）。
 *
 * 三态：
 *  - 关键词为空：显示历史词列表
 *  - 搜索中：CnLoading
 *  - 搜索结果：CnFilterChips（fileType）+ LazyColumn
 *
 * 单击文件结果通过 [onOpenItem] 派发（app 模块路由到 PreviewRouter）。
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenItem: (DriveItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val history by viewModel.history.collectAsState()
    val listState = rememberLazyListState()

    // 自动加载下一页
    val loadMoreSignal by remember {
        derivedStateOf {
            val total = state.items.size
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
        // 搜索专属 Screen：始终 expanded；点击返回直接 onBack
        CnSearchTopBar(
            expanded = true,
            keyword = state.keyword,
            placeholder = "搜索文件名或文件夹",
            onExpandedChange = { expanded -> if (!expanded) onBack() },
            onKeywordChange = { viewModel.setKeyword(it) },
            onSearch = { viewModel.submitKeyword(state.keyword) },
            title = "",
            navigationIcon = {},
        )
        HorizontalDivider(color = CnPalette.DividerHairline)

        when (state.mode) {
            SearchMode.History -> HistoryPanel(
                history = history,
                onPick = { viewModel.pickHistory(it) },
                onRemove = { viewModel.removeHistory(it) },
                onClear = { viewModel.clearHistory() },
            )

            SearchMode.Searching -> CnLoading(modifier = Modifier.fillMaxSize())

            SearchMode.Results -> ResultsPanel(
                items = state.items,
                fileType = state.fileType,
                errorMessage = state.errorMessage,
                onSetFileType = { viewModel.setFileType(it) },
                onOpenItem = onOpenItem,
                listState = listState,
                onRetry = { viewModel.submitKeyword(state.keyword) },
            )
        }
    }
}

@Composable
private fun HistoryPanel(
    history: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    val tokens = LocalCnTokens.current
    if (history.isEmpty()) {
        CnEmpty(message = "还没有搜索记录", hint = "上方输入关键词后按搜索")
        return
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "搜索历史",
                    style = MaterialTheme.typography.labelLarge,
                    color = CnPalette.TextSecondary,
                )
                TextButton(onClick = onClear) {
                    Text("清空", color = CnPalette.TextTertiary)
                }
            }
            HorizontalDivider(color = CnPalette.DividerHairline)
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(history) { keyword ->
                    HistoryRow(
                        keyword = keyword,
                        onPick = { onPick(keyword) },
                        onRemove = { onRemove(keyword) },
                    )
                    HorizontalDivider(color = CnPalette.DividerHairline)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    keyword: String,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    val tokens = LocalCnTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        Icon(
            Icons.Outlined.History,
            contentDescription = null,
            tint = CnPalette.TextTertiary,
        )
        Text(
            text = keyword,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = tokens.spacing.xs),
            style = MaterialTheme.typography.bodyLarge,
            color = CnPalette.TextPrimary,
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "删除该历史",
                tint = CnPalette.TextTertiary,
            )
        }
        TextButton(onClick = onPick) {
            Text("搜索", color = CnPalette.Primary)
        }
    }
}

@Composable
private fun ResultsPanel(
    items: List<DriveItem>,
    fileType: SearchFileType,
    errorMessage: String?,
    onSetFileType: (SearchFileType) -> Unit,
    onOpenItem: (DriveItem) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        CnFilterChips(
            chips = SearchFileType.values().map { CnFilterChipItem(it.value, it.label) },
            selectedKey = fileType.value,
            onSelect = { key ->
                onSetFileType(SearchFileType.values().firstOrNull { it.value == key } ?: SearchFileType.All)
            },
        )
        HorizontalDivider(color = CnPalette.OutlineSoft)

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when {
                items.isEmpty() && errorMessage != null ->
                    CnError(message = errorMessage, onRetry = onRetry)

                items.isEmpty() ->
                    CnEmpty(message = "未找到匹配文件", hint = "试试更换关键词或切换文件类型")

                else -> Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                            CnFileCard(
                                name = item.name,
                                isFolder = item.isFolder,
                                fileType = item.fileType,
                                sizeBytes = item.sizeBytes,
                                updatedAt = item.updatedAt,
                                showDivider = index < items.lastIndex,
                                onClick = { onOpenItem(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}
