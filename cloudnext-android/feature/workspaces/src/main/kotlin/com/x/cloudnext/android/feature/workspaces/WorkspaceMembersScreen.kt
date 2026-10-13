package com.x.cloudnext.android.feature.workspaces

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.data.workspace.WorkspaceMember
import com.x.cloudnext.android.data.workspace.WorkspaceRole
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens
import com.x.cloudnext.android.ui.CnActionItem
import com.x.cloudnext.android.ui.CnActionRole
import com.x.cloudnext.android.ui.CnActionSheet
import com.x.cloudnext.android.ui.CnEmpty
import com.x.cloudnext.android.ui.CnError
import com.x.cloudnext.android.ui.CnFAB
import com.x.cloudnext.android.ui.CnFabAction
import com.x.cloudnext.android.ui.CnRoleChip
import com.x.cloudnext.android.ui.CnTopBar

/**
 * 工作空间成员管理。
 *
 * - OWNER/ADMIN：可改角色 + 移除成员 + 添加新成员
 * - MEMBER/VIEWER：只读
 *
 * 添加成员通过 OrgPicker SINGLE 模式；caller (NavHost) 在导航 OrgPicker 时 listen
 * `member.picked` savedStateHandle key，pop 回时调 [WorkspaceMembersViewModel.addMember]。
 */
@Composable
fun WorkspaceMembersScreen(
    workspaceName: String,
    onBack: () -> Unit,
    onPickPerson: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkspaceMembersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var menuTarget by remember { mutableStateOf<WorkspaceMember?>(null) }
    var roleChange by remember { mutableStateOf<Pair<WorkspaceMember, WorkspaceRole>?>(null) }
    var removeConfirm by remember { mutableStateOf<WorkspaceMember?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MembersEvent.Toast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val canManage = state.myRole.canManageMembers

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CnTopBar(
                title = "成员管理",
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
                    state.members.isEmpty() && state.errorMessage != null ->
                        CnError(message = state.errorMessage!!, onRetry = { viewModel.refresh() })
                    state.members.isEmpty() && !state.loading ->
                        CnEmpty(message = "暂无成员", hint = if (canManage) "点击右下角 + 添加" else null)
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.members, key = { it.id }) { member ->
                            MemberRow(
                                member = member,
                                canManage = canManage,
                                onMore = { menuTarget = member },
                            )
                            HorizontalDivider(color = CnPalette.DividerHairline)
                        }
                    }
                }
            }
        }

        if (canManage) {
            CnFAB(
                actions = listOf(
                    CnFabAction(
                        icon = Icons.Outlined.Add,
                        label = "添加成员",
                        onClick = onPickPerson,
                    ),
                ),
            )
        }
    }

    // 单成员菜单：改角色 / 移除
    menuTarget?.let { member ->
        val actions = buildList {
            WorkspaceRole.values().filter { it != member.role }.forEach { role ->
                add(CnActionItem(Icons.Outlined.MoreHoriz, "设为 ${role.label}"))
            }
            add(CnActionItem(Icons.Outlined.MoreHoriz, "移除成员", role = CnActionRole.Destructive))
        }
        CnActionSheet(
            title = member.personName,
            actions = actions,
            onActionClick = { index ->
                val title = actions[index].title
                menuTarget = null
                when {
                    title.startsWith("设为 ") -> {
                        val newRole = WorkspaceRole.values().firstOrNull { title.endsWith(it.label) } ?: return@CnActionSheet
                        roleChange = member to newRole
                    }
                    title == "移除成员" -> {
                        removeConfirm = member
                    }
                }
            },
            onDismiss = { menuTarget = null },
        )
    }

    roleChange?.let { (member, role) ->
        AlertDialog(
            onDismissRequest = { roleChange = null },
            title = { Text("更改角色") },
            text = {
                Text(
                    text = "将 ${member.personName} 的角色改为「${role.label}」？",
                    color = CnPalette.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = member to role
                    roleChange = null
                    viewModel.updateRole(target.first.id, target.second)
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { roleChange = null }) { Text("取消") }
            },
        )
    }

    removeConfirm?.let { member ->
        AlertDialog(
            onDismissRequest = { removeConfirm = null },
            title = { Text("移除成员") },
            text = {
                Text(
                    text = "确定移除 ${member.personName}？对方将立即失去访问此工作空间的权限。",
                    color = CnPalette.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = member.id
                    removeConfirm = null
                    viewModel.removeMember(id)
                }) {
                    Text("移除", color = CnPalette.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeConfirm = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MemberRow(member: WorkspaceMember, canManage: Boolean, onMore: () -> Unit) {
    val tokens = LocalCnTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(CnPalette.PrimarySoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (member.personName.firstOrNull()?.uppercase() ?: "?"),
                color = CnPalette.Primary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.personName.ifBlank { member.personOaId },
                style = MaterialTheme.typography.bodyLarge,
                color = CnPalette.TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            if (member.personName.isNotBlank() && member.personOaId.isNotBlank()) {
                Text(
                    text = member.personOaId,
                    style = MaterialTheme.typography.labelSmall,
                    color = CnPalette.TextTertiary,
                )
            }
        }
        CnRoleChip(role = member.role.label, colorKey = member.role.colorKey())
        if (canManage) {
            IconButton(onClick = onMore) {
                Icon(Icons.Outlined.MoreHoriz, contentDescription = "更多", tint = CnPalette.TextTertiary)
            }
        }
    }
}
