package com.x.cloudnext.android.feature.myfiles

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.data.drive.DriveItem
import com.x.cloudnext.android.designsystem.CnPalette

/**
 * 子目录页（嵌套层）。委托给 [FolderContent]，TopBar 显示返回按钮 + 当前文件夹名 +
 * 面包屑。
 */
@Composable
fun FolderScreen(
    folderName: String,
    onOpenFolder: (DriveItem) -> Unit,
    onOpenFile: (DriveItem) -> Unit,
    onBack: () -> Unit,
    onBreadcrumb: (segmentId: String?) -> Unit,
    onRequestShare: (itemIds: List<String>) -> Unit,
    onRequestCreateLink: (itemId: String, fileName: String) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyFilesViewModel = hiltViewModel(),
) {
    FolderContent(
        titleSingle = folderName,
        showBreadcrumb = true,
        leadingIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = CnPalette.TextPrimary)
            }
        },
        onOpenFolder = onOpenFolder,
        onOpenFile = onOpenFile,
        onBreadcrumb = onBreadcrumb,
        onRequestShare = onRequestShare,
        onRequestCreateLink = onRequestCreateLink,
        onOpenSearch = onOpenSearch,
        modifier = modifier,
        viewModel = viewModel,
    )
}
