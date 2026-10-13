package com.x.cloudnext.android.feature.shared.incoming

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.data.drive.DriveItem
import com.x.cloudnext.android.ui.CnEmpty
import com.x.cloudnext.android.ui.CnError
import com.x.cloudnext.android.ui.CnFileCard
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 收到的共享面板。每行显示被分享的 DriveItem，单击文件夹下钻、文件预览。
 *
 * @param onOpenFolder (shareId, itemId, name) → app 模块导航 IncomingFolderScreen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomingPanel(
    onOpenFolder: (shareId: String, itemId: String, name: String) -> Unit,
    onOpenFile: (DriveItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncomingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

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

    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = { viewModel.refresh() },
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when {
                state.items.isEmpty() && state.errorMessage != null ->
                    CnError(message = state.errorMessage!!, onRetry = { viewModel.refresh() })
                state.items.isEmpty() && !state.loading ->
                    CnEmpty(message = "暂无收到的共享文件", hint = "同事分享文件给你时会显示在这里")
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(state.items, key = { _, it -> it.id }) { index, share ->
                        val item = share.item
                        if (item != null) {
                            CnFileCard(
                                name = item.name,
                                isFolder = item.isFolder,
                                fileType = item.fileType,
                                sizeBytes = item.sizeBytes,
                                updatedAt = item.updatedAt,
                                showDivider = index < state.items.lastIndex,
                                onClick = {
                                    if (item.isFolder) {
                                        onOpenFolder(share.id, item.id, item.name)
                                    } else {
                                        onOpenFile(item)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
