package com.x.cloudnext.android.feature.workspaces

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.data.workspace.WorkspaceRole
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.ui.CnEmpty
import com.x.cloudnext.android.ui.CnError
import com.x.cloudnext.android.ui.CnFAB
import com.x.cloudnext.android.ui.CnFabAction
import com.x.cloudnext.android.ui.CnSearchTopBar
import com.x.cloudnext.android.ui.CnWorkspaceCard
import com.x.cloudnext.android.ui.NewFolderDialog

/**
 * 工作空间列表（Tab 根）。
 *
 * - TopBar：左 Menu + 标题 + 右搜索图标（点击展开 inline 搜索框）
 * - 搜索框默认折叠；点击 TopBar 搜索图标后展开，自动 focus；点 X 收回
 * - 客户端 filter（无 RPC）
 * - LazyColumn 渲染 CnWorkspaceCard
 * - FAB 创建新工作空间
 *
 * 视觉对齐 cloudnext-web 移动端 WorkspacesPage：默认 TopBar 不显示搜索框，
 * 仅当用户主动点击搜索图标才展开 inline 输入条。
 */
@Composable
fun WorkspacesScreen(
    onOpenDrawer: () -> Unit,
    onOpenWorkspace: (id: String, name: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkspacesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var showCreateDialog by remember { mutableStateOf(false) }
    // 搜索展开状态。rememberSaveable 跨旋转保留
    var searchExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WorkspacesEvent.Toast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CnSearchTopBar(
                expanded = searchExpanded,
                keyword = state.keyword,
                placeholder = "按名称搜索协作空间",
                onExpandedChange = { expanded ->
                    searchExpanded = expanded
                    if (!expanded) viewModel.setKeyword("")
                },
                onKeywordChange = { viewModel.setKeyword(it) },
                title = "协作空间",
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Outlined.Menu, contentDescription = "菜单", tint = CnPalette.TextPrimary)
                    }
                },
            )
            HorizontalDivider(color = CnPalette.DividerHairline)

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                val filtered = state.filtered
                when {
                    filtered.isEmpty() && state.errorMessage != null ->
                        CnError(message = state.errorMessage!!, onRetry = { viewModel.refresh() })
                    filtered.isEmpty() && state.keyword.isNotEmpty() ->
                        CnEmpty(message = "未找到匹配的协作空间")
                    filtered.isEmpty() && !state.loading ->
                        CnEmpty(
                            message = "暂未加入任何协作空间",
                            hint = "点击右下角 + 创建你的第一个空间",
                        )
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filtered, key = { it.id }) { ws ->
                            CnWorkspaceCard(
                                name = ws.name,
                                memberCount = ws.memberCount,
                                creatorName = ws.creatorName,
                                updatedAtLabel = ws.updatedAt,
                                roleLabel = ws.myRole.label,
                                roleColorKey = ws.myRole.colorKey(),
                                onClick = { onOpenWorkspace(ws.id, ws.name) },
                            )
                            HorizontalDivider(color = CnPalette.DividerHairline)
                        }
                    }
                }
            }
        }

        // FAB 创建
        CnFAB(
            actions = listOf(
                CnFabAction(
                    icon = Icons.Outlined.Add,
                    label = "创建协作空间",
                    onClick = { showCreateDialog = true },
                ),
            ),
        )
    }

    if (showCreateDialog) {
        NewFolderDialog(
            onConfirm = { name ->
                showCreateDialog = false
                viewModel.create(name)
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}

/** WorkspaceRole → CnRoleChip colorKey 映射。 */
internal fun WorkspaceRole.colorKey(): String = when (this) {
    WorkspaceRole.Owner -> "owner"
    WorkspaceRole.Admin -> "admin"
    WorkspaceRole.Member -> "member"
    WorkspaceRole.Viewer -> "viewer"
}
