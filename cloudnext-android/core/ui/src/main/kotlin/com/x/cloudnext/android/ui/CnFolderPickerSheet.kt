package com.x.cloudnext.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens

/**
 * 文件夹选择器条目（轻量化，避免 :core:ui 反向依赖 :data:drive）。
 */
data class PickerFolder(val id: String, val name: String)

/**
 * 移动目标选择器。caller 持有 ViewModel 维护：
 * - [pathLabels]：当前层级的面包屑（最末位 = 当前）。第一段一般是"我的文件"。
 * - [folders]：当前 parentId 下的子文件夹（已过滤掉文件 + 已过滤掉正在移动的项目）
 * - [loading]：列表加载中
 * - [canGoUp]：是否还能返回上一级（已经在根时为 false）
 * - [confirmEnabled]：是否允许在当前位置确认（通常根目录也允许）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CnFolderPickerSheet(
    pathLabels: List<String>,
    folders: List<PickerFolder>,
    loading: Boolean,
    canGoUp: Boolean,
    confirmEnabled: Boolean,
    onEnter: (PickerFolder) -> Unit,
    onUp: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "移动到",
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = LocalCnTokens.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = tokens.spacing.md, top = tokens.spacing.sm, bottom = tokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onUp, enabled = canGoUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "上一级",
                        tint = if (canGoUp) CnPalette.TextPrimary else CnPalette.TextTertiary,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = CnPalette.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("取消") }
            }
            HorizontalDivider(color = CnPalette.OutlineSoft)

            // 当前路径
            Text(
                text = pathLabels.joinToString(" / ").ifBlank { "我的文件" },
                style = MaterialTheme.typography.labelMedium,
                color = CnPalette.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
            )

            HorizontalDivider(color = CnPalette.DividerHairline)

            // 列表
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(min = 180.dp, max = 360.dp),
            ) {
                when {
                    loading -> CnLoading()
                    folders.isEmpty() -> CnEmpty(message = "这里没有子文件夹", hint = "可以「选择此处」直接移动到当前位置")
                    else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(folders, key = { it.id }) { folder ->
                            FolderRow(folder = folder, onClick = { onEnter(folder) })
                            HorizontalDivider(color = CnPalette.DividerHairline)
                        }
                    }
                }
            }

            // 底部按钮
            HorizontalDivider(color = CnPalette.OutlineSoft)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(tokens.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    modifier = Modifier.weight(1f),
                ) { Text("选择此处") }
            }
            Spacer(modifier = Modifier.height(tokens.spacing.sm))
        }
    }
}

@Composable
private fun FolderRow(folder: PickerFolder, onClick: () -> Unit) {
    val tokens = LocalCnTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(tokens.dimensions.fileIconBox)
                .background(tokens.fileIcon.background("folder"), MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = tokens.fileIcon.foreground("folder"),
            )
        }
        Text(
            text = folder.name,
            style = MaterialTheme.typography.bodyLarge,
            color = CnPalette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
