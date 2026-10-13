package com.x.cloudnext.android.feature.myfiles

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Share
import androidx.core.content.FileProvider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.x.cloudnext.android.data.drive.DriveItem
import com.x.cloudnext.android.datastore.ViewMode
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens
import com.x.cloudnext.android.ui.BreadcrumbSegment
import com.x.cloudnext.android.ui.CnActionItem
import com.x.cloudnext.android.ui.CnActionRole
import com.x.cloudnext.android.ui.CnActionSheet
import com.x.cloudnext.android.ui.CnEmpty
import com.x.cloudnext.android.ui.CnError
import com.x.cloudnext.android.ui.CnFAB
import com.x.cloudnext.android.ui.CnFabAction
import com.x.cloudnext.android.ui.CnFileCard
import com.x.cloudnext.android.ui.CnFolderPickerSheet
import com.x.cloudnext.android.ui.CnPathBreadcrumb
import com.x.cloudnext.android.ui.CnSelectionBar
import com.x.cloudnext.android.ui.CnTopBar
import com.x.cloudnext.android.ui.DeleteConfirmDialog
import com.x.cloudnext.android.ui.NewFolderDialog
import com.x.cloudnext.android.ui.PickerFolder
import com.x.cloudnext.android.ui.RenameDialog

/**
 * 通用文件夹内容布局（MyFilesScreen 和 FolderScreen 复用）。
 *
 * 容纳所有 M2b 交互：长按多选、CRUD 弹窗、FAB / SelectionBar 切换、ActionSheet、网格视图、移动选择器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderContent(
    titleSingle: String,
    showBreadcrumb: Boolean,
    leadingIcon: @Composable () -> Unit,
    onOpenFolder: (DriveItem) -> Unit,
    onOpenFile: (DriveItem) -> Unit,
    onBreadcrumb: (segmentId: String?) -> Unit,
    onRequestShare: (itemIds: List<String>) -> Unit,
    onRequestCreateLink: (itemId: String, fileName: String) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyFilesViewModel,
) {
    val state by viewModel.state.collectAsState()
    val picker by viewModel.picker.collectAsState()
    val context = LocalContext.current
    val tokens = LocalCnTokens.current

    // 单文件 ActionSheet 目标
    var sheetTarget by remember { mutableStateOf<DriveItem?>(null) }
    // CRUD 对话框
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DriveItem?>(null) }
    var deleteTarget by remember { mutableStateOf<Set<String>?>(null) }

    // 上传入口（M3a）
    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        // SAF 返回的 URI 默认仅短期权限，takePersistableUriPermission 让 Worker 后台还能读
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
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) viewModel.enqueueUploads(listOf(uri))
    }

    // 事件 → Toast / RequestCreateShare / RequestCreateLink → 外部回调
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Toast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is UiEvent.RequestCreateShare -> onRequestShare(event.itemIds)
                is UiEvent.RequestCreateLink -> onRequestCreateLink(event.itemId, event.fileName)
            }
        }
    }

    val isMulti = state.selectionMode == SelectionMode.Multi

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
                    title = titleSingle,
                    navigationIcon = { leadingIcon() },
                    actions = {
                        IconButton(onClick = onOpenSearch) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "搜索",
                                tint = CnPalette.TextPrimary,
                            )
                        }
                        val nextMode = if (state.viewMode == ViewMode.List) ViewMode.Grid else ViewMode.List
                        IconButton(onClick = { viewModel.setViewMode(nextMode) }) {
                            Icon(
                                imageVector = if (state.viewMode == ViewMode.List) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.ViewList,
                                contentDescription = if (state.viewMode == ViewMode.List) "切换网格" else "切换列表",
                                tint = CnPalette.TextPrimary,
                            )
                        }
                    },
                )
            }
            HorizontalDivider(color = CnPalette.OutlineSoft)

            if (!isMulti && showBreadcrumb) {
                val segments = remember(state.path) {
                    listOf(BreadcrumbSegment(id = null, name = "我的文件")) +
                        state.path.map { BreadcrumbSegment(id = it.id, name = it.name) }
                }
                if (segments.size > 1) {
                    CnPathBreadcrumb(
                        segments = segments,
                        onSegmentClick = { seg -> onBreadcrumb(seg.id) },
                    )
                    HorizontalDivider(color = CnPalette.OutlineSoft)
                }
            }

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                when {
                    state.items.isEmpty() && state.errorMessage != null ->
                        CnError(message = state.errorMessage!!, onRetry = { viewModel.refresh() })
                    state.items.isEmpty() && !state.refreshing ->
                        CnEmpty(message = "还没有文件", hint = "点击右下 + 新建文件夹")
                    else -> ItemsList(
                        items = state.items,
                        viewMode = state.viewMode,
                        selecting = isMulti,
                        selectedIds = state.selectedIds,
                        onCardClick = { item ->
                            if (isMulti) {
                                viewModel.toggleSelected(item.id)
                            } else if (item.isFolder) {
                                onOpenFolder(item)
                            } else {
                                onOpenFile(item)
                            }
                        },
                        onCardLongPress = { item ->
                            if (!isMulti) viewModel.enterMulti(item.id)
                        },
                        onCardMore = { item ->
                            sheetTarget = item
                        },
                    )
                }
            }
        }

        // FAB (Single 模式)
        if (!isMulti) {
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
                    CnFabAction(
                        icon = Icons.Outlined.PhotoCamera,
                        label = "拍照上传",
                        onClick = {
                            val uri = createCameraUri(context)
                            pendingCameraUri = uri
                            cameraLauncher.launch(uri)
                        },
                    ),
                ),
            )
        }

        // SelectionBar (Multi 模式)
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
                    onMove = {
                        if (state.selectedIds.isNotEmpty()) {
                            viewModel.openMovePicker(state.selectedIds)
                        }
                    },
                    onDelete = {
                        if (state.selectedIds.isNotEmpty()) {
                            deleteTarget = state.selectedIds
                        }
                    },
                    onDownload = {
                        val selected = state.selectedItems
                        if (selected.isNotEmpty()) viewModel.enqueueDownloads(selected)
                    },
                    onShare = {
                        val selected = state.selectedItems
                        if (selected.isNotEmpty()) viewModel.requestShare(selected)
                    },
                )
            }
        }
    }

    // 单文件 ActionSheet
    sheetTarget?.let { item ->
        // 文件夹不允许下载 / 创建外链（后端只支持单文件）
        val isFolder = item.isFolder
        val actions = buildList {
            add(CnActionItem(icon = Icons.Outlined.DriveFileRenameOutline, title = "重命名"))
            add(CnActionItem(icon = Icons.AutoMirrored.Outlined.DriveFileMove, title = "移动"))
            add(CnActionItem(icon = Icons.Outlined.Share, title = "分享"))
            if (!isFolder) {
                add(CnActionItem(icon = Icons.Outlined.Link, title = "创建外链"))
                add(CnActionItem(icon = Icons.Outlined.Download, title = "下载"))
            }
            add(CnActionItem(icon = Icons.Outlined.DeleteOutline, title = "删除", role = CnActionRole.Destructive))
        }
        CnActionSheet(
            title = item.name,
            actions = actions,
            onActionClick = { index ->
                when (actions[index].title) {
                    "重命名" -> {
                        sheetTarget = null
                        renameTarget = item
                    }
                    "移动" -> {
                        sheetTarget = null
                        viewModel.openMovePicker(setOf(item.id))
                    }
                    "分享" -> {
                        sheetTarget = null
                        viewModel.requestShare(listOf(item))
                    }
                    "创建外链" -> {
                        sheetTarget = null
                        viewModel.requestCreateLink(item)
                    }
                    "下载" -> {
                        sheetTarget = null
                        viewModel.enqueueDownloads(listOf(item))
                    }
                    "删除" -> {
                        sheetTarget = null
                        deleteTarget = setOf(item.id)
                    }
                }
            },
            onDismiss = { sheetTarget = null },
        )
    }

    // NewFolderDialog
    if (showNewFolderDialog) {
        NewFolderDialog(
            onConfirm = { name ->
                showNewFolderDialog = false
                viewModel.createFolder(name)
            },
            onDismiss = { showNewFolderDialog = false },
        )
    }

    // RenameDialog
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

    // DeleteConfirmDialog
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

    // FolderPickerSheet
    picker?.let { pickerState ->
        val pathLabels = remember(pickerState.pathSegments) {
            listOf("我的文件") + pickerState.pathSegments.map { it.name }
        }
        CnFolderPickerSheet(
            pathLabels = pathLabels,
            folders = pickerState.visibleFolders.map { PickerFolder(id = it.id, name = it.name) },
            loading = pickerState.loading,
            canGoUp = pickerState.currentParentId != null,
            confirmEnabled = !pickerState.loading,
            onEnter = { folder -> viewModel.pickerEnter(folder.id) },
            onUp = { viewModel.pickerUp() },
            onConfirm = { viewModel.moveConfirm() },
            onDismiss = { viewModel.closePicker() },
        )
    }
}

@Composable
private fun ItemsList(
    items: List<DriveItem>,
    viewMode: ViewMode,
    selecting: Boolean,
    selectedIds: Set<String>,
    onCardClick: (DriveItem) -> Unit,
    onCardLongPress: (DriveItem) -> Unit,
    onCardMore: (DriveItem) -> Unit,
) {
    val tokens = LocalCnTokens.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        when (viewMode) {
            ViewMode.List -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    CnFileCard(
                        name = item.name,
                        isFolder = item.isFolder,
                        fileType = item.fileType,
                        sizeBytes = item.sizeBytes,
                        updatedAt = item.updatedAt,
                        selecting = selecting,
                        selected = item.id in selectedIds,
                        showDivider = index < items.lastIndex,
                        onClick = { onCardClick(item) },
                        onLongPress = { onCardLongPress(item) },
                        onMore = if (selecting) null else ({ onCardMore(item) }),
                    )
                }
            }
            ViewMode.Grid -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                contentPadding = PaddingValues(tokens.spacing.sm),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { item ->
                    GridFileTile(
                        item = item,
                        selecting = selecting,
                        selected = item.id in selectedIds,
                        onClick = { onCardClick(item) },
                        onLongPress = { onCardLongPress(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GridFileTile(
    item: DriveItem,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val tokens = LocalCnTokens.current
    Surface(
        modifier = Modifier
            .padding(tokens.spacing.xs)
            .fillMaxWidth(),
        color = if (selected) CnPalette.PrimarySoft else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(tokens.spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val kind = if (item.isFolder) "folder" else item.fileType ?: "file"
            val bg = tokens.fileIcon.background(kind)
            val fg = tokens.fileIcon.foreground(kind)
            Box(
                modifier = Modifier
                    .padding(top = tokens.spacing.sm)
                    .background(bg, MaterialTheme.shapes.medium)
                    .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
            ) {
                Text(
                    text = kind.first().uppercase(),
                    color = fg,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = item.name,
                style = MaterialTheme.typography.labelMedium,
                color = CnPalette.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = tokens.spacing.xs),
            )
            if (selecting && selected) {
                Text(
                    text = "✓ 已选",
                    color = CnPalette.Primary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * 给 TakePicture 创建一个 FileProvider URI。文件存在 cacheDir/upload_camera/。
 * 必须与 AndroidManifest 里的 FileProvider authority + file_paths.xml 匹配。
 */
private fun createCameraUri(context: android.content.Context): Uri {
    val dir = java.io.File(context.cacheDir, "upload_camera").apply { mkdirs() }
    val file = java.io.File(dir, "IMG_${System.currentTimeMillis()}.jpg")
    val authority = "${context.applicationContext.packageName}.fileprovider"
    return FileProvider.getUriForFile(context, authority, file)
}
