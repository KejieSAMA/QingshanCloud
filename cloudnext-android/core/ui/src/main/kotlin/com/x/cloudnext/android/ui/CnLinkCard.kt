package com.x.cloudnext.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.x.cloudnext.android.common.FileKind
import com.x.cloudnext.android.common.guessFileKind
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens

/**
 * 外链卡片。M5b 用于 LinksPanel 与 SharedHost 的「我的外链」chip。
 *
 * 视觉：头部（文件图标 + 名称 + 状态徽章）+ 标签行（权限 / 密码 / 下载次数 / 有效期）+ 操作行（复制 / ⋯）。
 */
@Composable
fun CnLinkCard(
    fileName: String,
    statusLabel: String,
    statusColorKey: String,
    permissionLabel: String,
    requirePassword: Boolean,
    downloadLimit: Long,
    downloadCount: Long,
    expiresLabel: String?,
    onCopy: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalCnTokens.current
    val kind = guessFileKind(fileName)
    val iconBg = tokens.fileIcon.background(kind)
    val iconFg = tokens.fileIcon.foreground(kind)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(tokens.dimensions.fileIconBox)
                    .background(iconBg, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = kind.first().uppercase(),
                    color = iconFg,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyLarge,
                color = CnPalette.TextPrimary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusBadge(label = statusLabel, colorKey = statusColorKey)
        }
        // 标签行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 52.dp),  // 与文件名对齐（图标占 40 + 12 间距）
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetaText(permissionLabel)
            if (requirePassword) {
                MetaText("有密码")
            }
            MetaText(downloadLabel(downloadLimit, downloadCount))
            expiresLabel?.takeIf { it.isNotBlank() }?.let { MetaText(it) }
        }
        // 操作行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Text("复制链接", modifier = Modifier.padding(start = 4.dp))
            }
            IconButton(onClick = onMore) {
                Icon(Icons.Outlined.MoreHoriz, contentDescription = "更多", tint = CnPalette.TextTertiary)
            }
        }
    }
}

@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = CnPalette.TextTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StatusBadge(label: String, colorKey: String) {
    val (bg, fg) = badgeColors(colorKey)
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .background(bg, MaterialTheme.shapes.small)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun badgeColors(key: String): Pair<Color, Color> = when (key.lowercase()) {
    "active" -> CnPalette.SuccessSoft to CnPalette.Success
    "expired", "consumed" -> CnPalette.SurfaceVariant to CnPalette.TextTertiary
    "revoked" -> CnPalette.DangerSoft to CnPalette.Error
    else -> CnPalette.SurfaceVariant to CnPalette.TextTertiary
}

private fun downloadLabel(limit: Long, count: Long): String = when {
    limit < 0 -> "不限次数（已下 $count）"
    limit == 0L -> "已下 $count"
    else -> "已下 $count / $limit"
}
