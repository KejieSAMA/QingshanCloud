package com.x.cloudnext.android.feature.workspaces

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.data.drive.DriveItem
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.ui.CnActionItem
import com.x.cloudnext.android.ui.CnActionRole
import com.x.cloudnext.android.ui.CnActionSheet
import com.x.cloudnext.android.ui.CnEmpty
import com.x.cloudnext.android.ui.CnError
import com.x.cloudnext.android.ui.CnFAB
import com.x.cloudnext.android.ui.CnFabAction
import com.x.cloudnext.android.ui.CnFileCard
import com.x.cloudnext.android.ui.CnSelectionBar
import com.x.cloudnext.android.ui.CnTopBar
import com.x.cloudnext.android.ui.DeleteConfirmDialog
import com.x.cloudnext.android.ui.NewFolderDialog
import com.x.cloudnext.android.ui.RenameDialog

/**
 * 工作空间内容浏览（详情页 + 子目录通用）。
 *
 * - TopBar 标题 = 文件夹名（根目录 = 工作空间名）；右上 ⋯ 菜单（成员管理 / 退出空间）
 * - 长按 480ms 进入多选 → SelectionBar 批量删除
 * - FAB 上传 / 新建文件夹（仅 canWrite）
 * - 单击文件 → onOpenFile（PreviewRouter）；单击文件夹 → onOpenFolder（嵌套）
 * - VIEWER 角色：FAB 隐藏，ActionSheet 仅展示「文件信息」placeholder
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceDetailScreen(
    titleFallback: String,
    onBack: () -> Unit,
    onOpenFolder: (workspaceId: String, item: DriveItem) -> Unit,
    onOpenFile: (DriveItem) -> Unit,
    onOpenMembers: (workspaceId: String, workspaceName: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkspaceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var renameTarget by remember { mutableStateOf<DriveItem?>(null) }
    var deleteTarget by remember { mutableStateOf<Set<String>?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var sheetTarget by remember { mutableStateOf<DriveItem?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        viewModel.enqueueUploads(uris)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkspaceDetailEvent.Toast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val isMulti = state.selecting
    val title = state.workspace?.name ?: titleFallback

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isMulti) {
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
                    title = title,
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
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = "更多",
                                tint = CnPalette.TextPrimary,
                            )
                        }
                    },
                )
            }
            HorizontalDivider(color = CnPalette.OutlineSoft)

            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = { viewModel.refreshItems() },
                modifier = Modifier.fillMaxSize(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    when {
                        state.items.isEmpty() && state.errorMessage != null ->
                            CnError(message = state.errorMessage!!, onRetry = { viewModel.refreshItems() })
                        state.items.isEmpty() && !state.loading ->
                            CnEmpty(
                                message = "空文件夹",
                                hint = if (state.canWrite) "点击右下角 + 上传文件或新建文件夹" else null,
                            )
                        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(state.items, key = { _, it -> it.id }) { index, item ->
                                CnFileCard(
                                    name = item.name,
                                    isFolder = item.isFolder,
                                    fileType = item.fileType,
                                    sizeBytes = item.sizeBytes,
                                    updatedAt = item.updatedAt,
                                    selecting = isMulti,
                                    selected = item.id in state.selectedIds,
                                    showDivider = index < state.items.lastIndex,
                                    onClick = {
                                        if (isMulti) {
                                            viewModel.toggleSelected(item.id)
                                        } else if (item.isFolder) {
                                            onOpenFolder(viewModel.workspaceId, item)
                                        } else {
                                            onOpenFile(item)
                                        }
                                    },
                                    onLongPress = {
                                        if (!isMulti && state.canWrite) viewModel.enterMulti(item.id)
                                    },
                                    onMore = if (isMulti || !state.canWrite) null else ({ sheetTarget = item }),
                                )
                            }
                        }
                    }
                }
            }
        }

        // FAB（仅 canWrite + 非 Multi 模式）
        if (!isMulti && state.canWrite) {
            CnFAB(
                actions = listOf(
                    CnFabAction(
                        icon = Icons.Outlined.CreateNewFolder,
                        label = "新建文件夹",
                        onClick = { showNewFolderDialog = true },
                    ),
                    CnFabAction(
                        icon = Icons.Outlined.UploadFile,
                        label = "上传文件",
                        onClick = { safLauncher.launch(arrayOf("*/*")) },
                    ),
                ),
            )
        }

        // SelectionBar
        if (isMulti) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                CnSelectionBar(
                    selectedCount = state.selectedIds.size,
                    allSelected = state.allSelected,
                    onToggleSelectAll = { viewModel.toggleSelectAll() },
                    onDelete = {
                        if (state.selectedIds.isNotEmpty()) deleteTarget = state.selectedIds
                    },
                )
            }
        }
    }

    // 顶部 ⋯ 菜单（占位 ActionSheet：成员管理）
    if (showMenu) {
        CnActionSheet(
            title = state.workspace?.name,
            actions = listOfNotNull(
                CnActionItem(Icons.Outlined.Groups, "成员管理"),
            ),
            onActionClick = { index ->
                showMenu = false
                when (index) {
                    0 -> onOpenMembers(viewModel.workspaceId, state.workspace?.name ?: titleFallback)
                }
            },
            onDismiss = { showMenu = false },
        )
    }

    // 单文件 ActionSheet
    sheetTarget?.let { item ->
        CnActionSheet(
            title = item.name,
            actions = listOf(
                CnActionItem(Icons.Outlined.DriveFileRenameOutline, "重命名"),
                CnActionItem(Icons.Outlined.DeleteOutline, "删除", role = CnActionRole.Destructive),
            ),
            onActionClick = { index ->
                when (index) {
                    0 -> {
                        sheetTarget = null
                        renameTarget = item
                    }
                    1 -> {
                        sheetTarget = null
                        deleteTarget = setOf(item.id)
                    }
                }
            },
            onDismiss = { sheetTarget = null },
        )
    }

    if (showNewFolderDialog) {
        NewFolderDialog(
            onConfirm = { name ->
                showNewFolderDialog = false
                viewModel.createFolder(name)
            },
            onDismiss = { showNewFolderDialog = false },
        )
    }

    renameTarget?.let { item ->
        RenameDialog(
            currentName = item.name,
            onConfirm = { newName ->
                renameTarget = null
                viewModel.rename(item.id, newName)
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { ids ->
        DeleteConfirmDialog(
            count = ids.size,
            onConfirm = {
                deleteTarget = null
                viewModel.delete(ids)
            },
            onDismiss = { deleteTarget = null },
        )
    }
}
