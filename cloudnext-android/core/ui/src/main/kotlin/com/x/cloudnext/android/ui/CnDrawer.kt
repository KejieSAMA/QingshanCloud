package com.x.cloudnext.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens

/**
 * 侧抽屉内容。包装在 [androidx.compose.material3.ModalNavigationDrawer] 内使用。
 *
 * 菜单结构（M6 UI 对齐 cloudnext-web MobileDrawer.vue）：
 *   - 用户卡（头像 + 名称）
 *   - 主功能：首页 / 我的文件 / 共享文件 / 协作空间
 *   - 系统功能：外链管理 / 回收站 / 传输任务 / 设置
 *   - 退出登录
 *
 * 主功能 1-4 通过 [onSwitchTab] 切换 Tab；其它走独立 fullscreen route。
 */
@Composable
fun CnDrawer(
    userName: String,
    userEmail: String? = null,
    onSwitchTab: ((CnTab) -> Unit)? = null,    // M6 加：切到主功能 Tab
    onLinksClick: (() -> Unit)? = null,        // M6 加：外链管理 fullscreen
    onTrashClick: (() -> Unit)? = null,
    onTransferClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    onLogoutClick: () -> Unit,
) {
    val tokens = LocalCnTokens.current
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(288.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            UserHeader(userName = userName, userEmail = userEmail)
            HorizontalDivider(color = CnPalette.DividerHairline)

            // ---- 主功能：4 Tab 入口 ----
            if (onSwitchTab != null) {
                DrawerItem(
                    icon = Icons.Outlined.Home,
                    label = "首页",
                    onClick = { onSwitchTab(CnTab.Home) },
                )
                DrawerItem(
                    icon = Icons.Outlined.Folder,
                    label = "我的文件",
                    onClick = { onSwitchTab(CnTab.Files) },
                )
                DrawerItem(
                    icon = Icons.Outlined.People,
                    label = "共享文件",
                    onClick = { onSwitchTab(CnTab.Shared) },
                )
                DrawerItem(
                    icon = Icons.Outlined.Groups,
                    label = "协作空间",
                    onClick = { onSwitchTab(CnTab.Workspaces) },
                )
                HorizontalDivider(color = CnPalette.DividerHairline)
            }

            // ---- 系统功能 ----
            DrawerItem(
                icon = Icons.Outlined.Link,
                label = "外链管理",
                enabled = onLinksClick != null,
                onClick = onLinksClick ?: {},
            )
            DrawerItem(
                icon = Icons.Outlined.DeleteOutline,
                label = "回收站",
                enabled = onTrashClick != null,
                onClick = onTrashClick ?: {},
            )
            DrawerItem(
                icon = Icons.Outlined.SwapVert,
                label = "传输任务",
                enabled = onTransferClick != null,
                onClick = onTransferClick ?: {},
            )
            DrawerItem(
                icon = Icons.Outlined.Settings,
                label = "设置",
                enabled = onSettingsClick != null,
                onClick = onSettingsClick ?: {},
            )

            Spacer(modifier = Modifier.weight(1f))

            HorizontalDivider(color = CnPalette.DividerHairline)
            DrawerItem(
                icon = Icons.Outlined.Logout,
                label = "退出登录",
                onClick = onLogoutClick,
                emphasize = true,
            )
            Spacer(modifier = Modifier.height(tokens.spacing.md))
        }
    }
}

@Composable
private fun UserHeader(userName: String, userEmail: String?) {
    val tokens = LocalCnTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(tokens.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(CnPalette.Primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // 头像首字母用白色字 + 主色背景，对齐 cloudnext-web MobileDrawer
            Text(
                text = (userName.firstOrNull()?.uppercase() ?: "C"),
                color = CnPalette.OnPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.size(tokens.spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = userName.ifBlank { "用户" },
                style = MaterialTheme.typography.titleMedium,
                color = CnPalette.TextPrimary,
            )
            if (!userEmail.isNullOrBlank()) {
                Text(
                    text = userEmail,
                    style = MaterialTheme.typography.labelSmall,
                    color = CnPalette.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    emphasize: Boolean = false,
    trailingHint: String? = null,
) {
    val tokens = LocalCnTokens.current
    val textColor = when {
        emphasize -> CnPalette.Primary
        !enabled -> CnPalette.TextTertiary
        else -> CnPalette.TextPrimary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(tokens.touch.comfort)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = tokens.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = textColor,
        )
        Spacer(modifier = Modifier.size(tokens.spacing.md))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            modifier = Modifier.weight(1f),
        )
        if (trailingHint != null) {
            Text(
                text = trailingHint,
                style = MaterialTheme.typography.labelSmall,
                color = CnPalette.TextTertiary,
            )
        }
    }
}
