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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens

/**
 * 工作空间列表卡片。视觉对齐 cloudnext-web MobileWorkspaceCard.vue：
 * 左 icon | 名称 + 元数据行（成员数 · 创建人 · 修改时间） | 角色徽章 + ›
 */
@Composable
fun CnWorkspaceCard(
    name: String,
    memberCount: Long,
    creatorName: String?,
    updatedAtLabel: String?,
    roleLabel: String,
    roleColorKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalCnTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(tokens.dimensions.fileIconBox)
                .background(CnPalette.PrimarySoft, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Groups,
                contentDescription = null,
                tint = CnPalette.Primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = CnPalette.TextPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildMeta(memberCount, creatorName, updatedAtLabel),
                style = MaterialTheme.typography.labelSmall,
                color = CnPalette.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CnRoleChip(role = roleLabel, colorKey = roleColorKey)
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = CnPalette.TextTertiary,
        )
    }
}

private fun buildMeta(memberCount: Long, creatorName: String?, updatedAtLabel: String?): String {
    val parts = mutableListOf<String>()
    if (memberCount > 0) parts += "$memberCount 位成员"
    creatorName?.takeIf { it.isNotBlank() }?.let { parts += "创建人 $it" }
    updatedAtLabel?.takeIf { it.isNotBlank() }?.let { parts += it }
    return parts.joinToString(" · ")
}
