package com.x.cloudnext.android.feature.trash

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.data.trash.TrashFilter
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.ui.CnActionItem
import com.x.cloudnext.android.ui.CnActionRole
import com.x.cloudnext.android.ui.CnActionSheet
import com.x.cloudnext.android.ui.CnEmpty
import com.x.cloudnext.android.ui.CnError
import com.x.cloudnext.android.ui.CnFileCard
import com.x.cloudnext.android.ui.CnFilterChipItem
import com.x.cloudnext.android.ui.CnFilterChips
import com.x.cloudnext.android.ui.CnSelectionBar
import com.x.cloudnext.android.ui.CnTopBar
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 回收站。三筛切换 + 长按多选 + 恢复/彻底删除 + 顶部「清空」。
 *
 * 数据来自 [TrashRepository.query]（GET /api/drive/trash/items/query 分页）。
 */
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // 事件 → Toast
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TrashEvent.Toast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 自动加载下一页：滚到底部时触发
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

    // 二次确认对话框
    var pendingPurge by remember { mutableStateOf<Set<String>?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var sheetTarget by remember { mutableStateOf<com.x.cloudnext.android.data.drive.DriveItem?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.selecting) {
                CnTopBar(
                    title = "已选 ${state.selectedIds.size} 项",
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitMulti() }) {
                            Icon(Icons.Outlined.Close, contentDescription = "取消多选", tint = CnPalette.TextPrimary)
                        }
                    },
                )
            } else {
                CnTopBar(
                    title = "回收站",
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "返回",
                                tint = CnPalette.TextPrimary,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showClearAllConfirm = true },
                            enabled = state.items.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Outlined.DeleteSweep,
                                contentDescription = "清空回收站",
                                tint = if (state.items.isNotEmpty()) CnPalette.Error else CnPalette.TextTertiary,
                            )
                        }
                    },
                )
            }
            HorizontalDivider(color = CnPalette.OutlineSoft)

            // FilterChips
            if (!state.selecting) {
                CnFilterChips(
                    chips = listOf(
                        CnFilterChipItem("all", "全部"),
                        CnFilterChipItem("folder", "文件夹"),
                        CnFilterChipItem("file", "文件"),
                    ),
                    selectedKey = when (state.filter) {
                        TrashFilter.All -> "all"
                        TrashFilter.Folders -> "folder"
                        TrashFilter.Files -> "file"
                    },
                    onSelect = { key ->
                        val target = when (key) {
                            "folder" -> TrashFilter.Folders
                            "file" -> TrashFilter.Files
                            else -> TrashFilter.All
                        }
                        viewModel.setFilter(target)
                    },
                )
                HorizontalDivider(color = CnPalette.OutlineSoft)
            }

            // 内容
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                when {
                    state.items.isEmpty() && state.errorMessage != null ->
                        CnError(message = state.errorMessage!!, onRetry = { viewModel.refresh() })
                    state.items.isEmpty() && !state.loading ->
                        CnEmpty(message = "回收站为空", hint = "删除的文件会保留在这里，可恢复或彻底清除")
                    else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(state.items, key = { _, it -> it.id }) { index, item ->
                            CnFileCard(
                                name = item.name,
                                isFolder = item.isFolder,
                                fileType = item.fileType,
                                sizeBytes = item.sizeBytes,
                                updatedAt = item.updatedAt,
                                selecting = state.selecting,
                                selected = item.id in state.selectedIds,
                                showDivider = index < state.items.lastIndex,
                                onClick = {
                                    if (state.selecting) {
                                        viewModel.toggleSelected(item.id)
                                    } else {
                                        sheetTarget = item
                                    }
                                },
                                onLongPress = {
                                    if (!state.selecting) viewModel.enterMulti(item.id)
                                },
                                onMore = if (state.selecting) null else ({ sheetTarget = item }),
                            )
                        }
                    }
                }
            }
        }

        // SelectionBar
        if (state.selecting) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                CnSelectionBar(
                    selectedCount = state.selectedIds.size,
                    allSelected = state.allSelected,
                    onToggleSelectAll = { viewModel.toggleSelectAll() },
                    onRestore = {
                        if (state.selectedIds.isNotEmpty()) viewModel.restore(state.selectedIds)
                    },
                    onPurge = {
                        if (state.selectedIds.isNotEmpty()) pendingPurge = state.selectedIds
                    },
                )
            }
        }
    }

    // 单项 ActionSheet
    sheetTarget?.let { item ->
        CnActionSheet(
            title = item.name,
            actions = listOf(
                CnActionItem(icon = Icons.Outlined.RestoreFromTrash, title = "恢复"),
                CnActionItem(
                    icon = Icons.Outlined.DeleteForever,
                    title = "彻底删除",
                    role = CnActionRole.Destructive,
                ),
            ),
            onActionClick = { index ->
                when (index) {
                    0 -> {
                        sheetTarget = null
                        viewModel.restore(setOf(item.id))
                    }
                    1 -> {
                        sheetTarget = null
                        pendingPurge = setOf(item.id)
                    }
                }
            },
            onDismiss = { sheetTarget = null },
        )
    }

    // 彻底删除二次确认
    pendingPurge?.let { ids ->
        AlertDialog(
            onDismissRequest = { pendingPurge = null },
            title = { Text("彻底删除") },
            text = {
                Text(
                    text = if (ids.size <= 1) {
                        "确定要彻底删除这一项？此操作不可恢复。"
                    } else {
                        "确定要彻底删除选中的 ${ids.size} 项？此操作不可恢复。"
                    },
                    color = CnPalette.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = ids
                    pendingPurge = null
                    viewModel.purge(target)
                }) {
                    Text("彻底删除", color = CnPalette.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPurge = null }) { Text("取消") }
            },
        )
    }

    // 清空回收站二次确认
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("清空回收站") },
            text = {
                Text(
                    text = "确定要清空回收站？所有项目将被彻底删除，此操作不可恢复。",
                    color = CnPalette.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearAllConfirm = false
                    viewModel.clearAll()
                }) {
                    Text("清空", color = CnPalette.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("取消") }
            },
        )
    }
}
