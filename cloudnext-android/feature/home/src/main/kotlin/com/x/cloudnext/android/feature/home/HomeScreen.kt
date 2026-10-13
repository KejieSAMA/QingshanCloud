package com.x.cloudnext.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.data.drive.DriveItem
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens
import com.x.cloudnext.android.ui.CnFileCard
import com.x.cloudnext.android.ui.CnTab
import com.x.cloudnext.android.ui.CnTopBar

/**
 * 首页。视觉对齐 cloudnext-web/src/pages/HomePage.vue：
 *
 * - TopBar 显示"云盘"标题 + 左侧菜单按钮（打开 Drawer）+ 右侧搜索
 * - 顶部欢迎卡（淡蓝渐变背景 + 大字"你好 + 用户名"）
 * - 2×2 快捷入口网格（我的文件 / 共享 / 协作 / 回收站）
 * - 两个章节："最近访问" / "最近打开"，每节横向滚动卡片列
 *
 * onSwitchTab / onOpenTrash 由 MainShell 通过 HomeTabHost 透传：
 *   - 前 3 项点击切到对应 Tab
 *   - 回收站走顶层 fullscreen route
 */
@Composable
fun HomeScreen(
    onOpenFolder: (DriveItem) -> Unit,
    onOpenFile: (DriveItem) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onSwitchTab: (CnTab) -> Unit,
    onOpenTrash: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val sessionInfo by viewModel.sessionInfo.collectAsState()
    val tokens = LocalCnTokens.current

    Column(modifier = modifier.fillMaxSize()) {
        CnTopBar(
            title = "云盘",
            navigationIcon = {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Outlined.Menu, contentDescription = "菜单", tint = CnPalette.TextPrimary)
                }
            },
            actions = {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = "搜索", tint = CnPalette.TextPrimary)
                }
            },
        )
        HorizontalDivider(color = CnPalette.OutlineSoft)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(vertical = tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg),
        ) {
            WelcomeCard(
                userName = sessionInfo?.displayName?.takeIf { it.isNotBlank() && !looksLikeUuid(it) } ?: "",
            )

            QuickEntryGrid(
                onSwitchTab = onSwitchTab,
                onOpenTrash = onOpenTrash,
            )

            Section(
                title = "最近访问",
                items = state.recent,
                emptyHint = "暂无最近访问",
                onOpenFolder = onOpenFolder,
                onOpenFile = onOpenFile,
            )

            Section(
                title = "最近打开",
                items = state.recentOpened,
                emptyHint = "暂无最近打开",
                onOpenFolder = onOpenFolder,
                onOpenFile = onOpenFile,
            )

            Spacer(modifier = Modifier.size(tokens.spacing.lg))
        }
    }
}

@Composable
private fun WelcomeCard(userName: String) {
    val tokens = LocalCnTokens.current
    // 淡蓝渐变背景，对齐 cloudnext-web mobile.css 的 home-welcome-card
    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFFF8FBFF), Color(0xFFEEF4FF)),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.md)
            .background(gradient, MaterialTheme.shapes.medium)
            .padding(tokens.spacing.lg),
    ) {
        Column {
            Text(
                text = if (userName.isBlank()) "你好" else "你好，$userName",
                style = MaterialTheme.typography.headlineSmall,
                color = CnPalette.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = "查看你最近的文件活动",
                style = MaterialTheme.typography.labelMedium,
                color = CnPalette.TextSecondary,
            )
        }
    }
}

/** 与 MainShellViewModel.looksLikeUuid 同步：避免 UUID 当用户名显示。 */
private fun looksLikeUuid(s: String): Boolean = UUID_REGEX.matches(s)
private val UUID_REGEX = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
)

@Composable
private fun Section(
    title: String,
    items: List<DriveItem>,
    emptyHint: String,
    onOpenFolder: (DriveItem) -> Unit,
    onOpenFile: (DriveItem) -> Unit,
) {
    val tokens = LocalCnTokens.current
    // 章节限制最多展示 5 项（与 Web 实际渲染策略一致）
    val visible = items.take(5)
    Column(modifier = Modifier.fillMaxWidth()) {
        // 章节标题（15sp Medium，对齐 mobile.css --m-fs-section）
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = CnPalette.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(
                start = tokens.spacing.md,
                end = tokens.spacing.md,
                top = tokens.spacing.sm,
                bottom = tokens.spacing.xs,
            ),
        )
        if (visible.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.md),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(tokens.spacing.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emptyHint,
                        style = MaterialTheme.typography.labelMedium,
                        color = CnPalette.TextTertiary,
                    )
                }
            }
        } else {
            // 垂直列表：直接复用 CnFileCard，与 MyFiles 视觉完全一致
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    visible.forEachIndexed { index, item ->
                        CnFileCard(
                            name = item.name,
                            isFolder = item.isFolder,
                            fileType = item.fileType,
                            sizeBytes = item.sizeBytes,
                            updatedAt = item.lastAccessedAt ?: item.updatedAt,
                            showDivider = index < visible.lastIndex,
                            onClick = {
                                if (item.isFolder) onOpenFolder(item) else onOpenFile(item)
                            },
                        )
                    }
                }
            }
        }
    }
}
