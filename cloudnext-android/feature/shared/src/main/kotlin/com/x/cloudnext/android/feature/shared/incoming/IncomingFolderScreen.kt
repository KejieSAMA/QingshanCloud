package com.x.cloudnext.android.feature.shared.incoming

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.data.drive.DriveItem
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.ui.CnEmpty
import com.x.cloudnext.android.ui.CnError
import com.x.cloudnext.android.ui.CnFileCard
import com.x.cloudnext.android.ui.CnTopBar

/**
 * incoming 子目录浏览。当前 M5a 仅展示同一 share 下的其它项（后端在 incoming 列表里返回的同 shareId 项）。
 *
 * M5b 后会引入嵌套 shareApi.incoming(shareId, parentId) 真正的子树拉取。
 */
@Composable
fun IncomingFolderScreen(
    name: String,
    onBack: () -> Unit,
    onOpenFile: (DriveItem) -> Unit,
    onOpenFolder: (shareId: String, itemId: String, name: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncomingFolderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        CnTopBar(
            title = name,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = CnPalette.TextPrimary,
                    )
                }
            },
        )
        HorizontalDivider(color = CnPalette.OutlineSoft)

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when {
                state.items.isEmpty() && state.errorMessage != null ->
                    CnError(message = state.errorMessage!!, onRetry = { viewModel.refresh() })
                state.items.isEmpty() && !state.loading ->
                    CnEmpty(message = "目录为空")
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(state.items, key = { _, it -> it.id }) { index, share ->
                        val item = share.item ?: return@itemsIndexed
                        CnFileCard(
                            name = item.name,
                            isFolder = item.isFolder,
                            fileType = item.fileType,
                            sizeBytes = item.sizeBytes,
                            updatedAt = item.updatedAt,
                            showDivider = index < state.items.lastIndex,
                            onClick = {
                                if (item.isFolder) onOpenFolder(share.id, item.id, item.name)
                                else onOpenFile(item)
                            },
                        )
                    }
                }
            }
        }
    }
}
