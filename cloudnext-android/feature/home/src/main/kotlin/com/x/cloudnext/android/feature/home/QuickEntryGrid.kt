package com.x.cloudnext.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens
import com.x.cloudnext.android.ui.CnTab

/**
 * 首页 2×2 快捷入口网格。视觉对齐 cloudnext-web HomePage.vue 的 .quick-entries：
 * 4 个彩色图标 + 主标 + 副标，点击切换 Tab 或跳转回收站。
 *
 * 4 项颜色与 mobile.css 一致：
 *  - 我的文件 蓝 #2F6BFF
 *  - 共享文件 绿 #16A34A
 *  - 协作空间 橙 #EA580C
 *  - 回收站   红 #DC2626
 */
@Composable
internal fun QuickEntryGrid(
    onSwitchTab: (CnTab) -> Unit,
    onOpenTrash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalCnTokens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.md),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            QuickEntryItem(
                icon = Icons.Outlined.Folder,
                iconBg = CnPalette.WordBg,
                iconFg = CnPalette.WordFg,
                title = "我的文件",
                subtitle = "我创建/上传的内容",
                onClick = { onSwitchTab(CnTab.Files) },
                modifier = Modifier.weight(1f),
            )
            QuickEntryItem(
                icon = Icons.Outlined.People,
                iconBg = CnPalette.ExcelBg,
                iconFg = CnPalette.ExcelFg,
                title = "共享文件",
                subtitle = "他人共享给我",
                onClick = { onSwitchTab(CnTab.Shared) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            QuickEntryItem(
                icon = Icons.Outlined.Groups,
                iconBg = CnPalette.PptBg,
                iconFg = CnPalette.PptFg,
                title = "协作空间",
                subtitle = "团队多人协作",
                onClick = { onSwitchTab(CnTab.Workspaces) },
                modifier = Modifier.weight(1f),
            )
            QuickEntryItem(
                icon = Icons.Outlined.DeleteOutline,
                iconBg = CnPalette.PdfBg,
                iconFg = CnPalette.PdfFg,
                title = "回收站",
                subtitle = "误删可恢复",
                onClick = onOpenTrash,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickEntryItem(
    icon: ImageVector,
    iconBg: Color,
    iconFg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalCnTokens.current
    Surface(
        modifier = modifier
            .height(72.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconBg, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconFg,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CnPalette.TextPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = CnPalette.TextTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}
