package com.x.cloudnext.android.feature.shared.outgoing

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MoreHoriz
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.data.share.DriveShare
import com.x.cloudnext.android.data.share.ShareStatus
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens
import com.x.cloudnext.android.ui.CnActionItem
import com.x.cloudnext.android.ui.CnActionRole
import com.x.cloudnext.android.ui.CnActionSheet
import com.x.cloudnext.android.ui.CnEmpty
import com.x.cloudnext.android.ui.CnError
import com.x.cloudnext.android.ui.CnSelectionBar
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 我发出的分享面板。
 *
 * - 每行：文件名 + target chip + 权限 + 状态徽章 + ⋯
 * - 单击行 ⋯ → ActionSheet：撤销
 * - 长按 → 多选模式 + 底部 SelectionBar 批量撤销
 */
@Composable
fun OutgoingPanel(
    modifier: Modifier = Modifier,
    viewModel: OutgoingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var sheetTarget by remember { mutableStateOf<DriveShare?>(null) }
    var revokeConfirm by remember { mutableStateOf<Set<String>?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OutgoingEvent.Toast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

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

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when {
                state.items.isEmpty() && state.errorMessage != null ->
                    CnError(message = state.errorMessage!!, onRetry = { viewModel.refresh() })
                state.items.isEmpty() && !state.loading ->
                    CnEmpty(message = "暂无我发出的共享", hint = "在文件页选择「分享」即可发起")
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id }) { share ->
                        OutgoingRow(
                            share = share,
                            selecting = state.selecting,
                            selected = share.id in state.selectedIds,
                            onClick = {
                                if (state.selecting) {
                                    viewModel.toggleSelected(share.id)
                                } else {
                                    sheetTarget = share
                                }
                            },
                            onLongPress = {
                                if (!state.selecting) viewModel.enterMulti(share.id)
                            },
                            onMore = if (state.selecting) null else ({ sheetTarget = share }),
                        )
                        HorizontalDivider(color = CnPalette.DividerHairline)
                    }
                }
            }
        }

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
                    onPurge = {
                        if (state.selectedIds.isNotEmpty()) revokeConfirm = state.selectedIds
                    },
                )
            }
        }
    }

    sheetTarget?.let { share ->
        CnActionSheet(
            title = share.item?.name ?: "分享详情",
            actions = listOf(
                CnActionItem(
                    icon = Icons.Outlined.LinkOff,
                    title = "撤销分享",
                    role = CnActionRole.Destructive,
                ),
            ),
            onActionClick = { _ ->
                sheetTarget = null
                revokeConfirm = setOf(share.id)
            },
            onDismiss = { sheetTarget = null },
        )
    }

    revokeConfirm?.let { ids ->
        AlertDialog(
            onDismissRequest = { revokeConfirm = null },
            title = { Text("撤销分享") },
            text = {
                Text(
                    text = if (ids.size <= 1) {
                        "确定要撤销这一项分享？对方将立即无法访问。"
                    } else {
                        "确定要撤销选中的 ${ids.size} 项分享？对方将立即无法访问。"
                    },
                    color = CnPalette.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = ids
                    revokeConfirm = null
                    if (target.size == 1) viewModel.revokeSingle(target.first())
                    else viewModel.revokeBatch(target)
                }) {
                    Text("撤销", color = CnPalette.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { revokeConfirm = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun OutgoingRow(
    share: DriveShare,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onMore: (() -> Unit)?,
) {
    val tokens = LocalCnTokens.current
    val rowBg = if (selected) CnPalette.PrimarySoft.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg),
        color = androidx.compose.ui.graphics.Color.Transparent,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = share.item?.name ?: "未知文件",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CnPalette.TextPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.size(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val targetName = share.target?.name?.takeIf { it.isNotBlank() } ?: "—"
                    Text(
                        text = "→ $targetName",
                        style = MaterialTheme.typography.labelSmall,
                        color = CnPalette.TextSecondary,
                    )
                    Text(text = "·", color = CnPalette.TextTertiary, style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = share.permission.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = CnPalette.TextSecondary,
                    )
                    StatusBadge(status = share.status)
                }
            }
            if (!selecting && onMore != null) {
                IconButton(onClick = onMore) {
                    Icon(
                        Icons.Outlined.MoreHoriz,
                        contentDescription = "更多",
                        tint = CnPalette.TextTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ShareStatus) {
    val (bg, fg) = when (status) {
        ShareStatus.Active -> CnPalette.PrimarySoft to CnPalette.Primary
        ShareStatus.Expired,
        ShareStatus.Consumed -> CnPalette.SurfaceVariant to CnPalette.TextTertiary
        ShareStatus.Revoked -> CnPalette.DangerSoft to CnPalette.Error
        ShareStatus.Unknown -> CnPalette.SurfaceVariant to CnPalette.TextTertiary
    }
    Box(
        modifier = Modifier
            .background(bg, MaterialTheme.shapes.small)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
        )
    }
}
