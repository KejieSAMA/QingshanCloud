package com.x.cloudnext.android.feature.myfiles

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.data.drive.DriveItem
import com.x.cloudnext.android.designsystem.CnPalette

/**
 * 「我的文件」根目录列表。委托给 [FolderContent] 处理全部交互。
 */
@Composable
fun MyFilesScreen(
    onOpenFolder: (DriveItem) -> Unit,
    onOpenFile: (DriveItem) -> Unit,
    onOpenDrawer: () -> Unit,
    onRequestShare: (itemIds: List<String>) -> Unit,
    onRequestCreateLink: (itemId: String, fileName: String) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyFilesViewModel = hiltViewModel(),
) {
    FolderContent(
        titleSingle = "我的文件",
        showBreadcrumb = false,
        leadingIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Outlined.Menu, contentDescription = "菜单", tint = CnPalette.TextPrimary)
            }
        },
        onOpenFolder = onOpenFolder,
        onOpenFile = onOpenFile,
        onBreadcrumb = { /* 根目录不展示面包屑，永不调用 */ },
        onRequestShare = onRequestShare,
        onRequestCreateLink = onRequestCreateLink,
        onOpenSearch = onOpenSearch,
        modifier = modifier,
        viewModel = viewModel,
    )
}
