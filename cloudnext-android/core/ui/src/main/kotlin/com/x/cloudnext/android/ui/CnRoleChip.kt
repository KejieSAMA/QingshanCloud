package com.x.cloudnext.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.x.cloudnext.android.designsystem.CnPalette

/**
 * 角色徽章。对齐 cloudnext-web 移动端：OWNER/ADMIN/MEMBER 都用主色软底（一致设计），
 * 仅 VIEWER 用灰色（强调只读语义）。
 *
 * @param role 已格式化的标签文案，如 "所有者" / "管理员" / "成员" / "只读"
 * @param colorKey "owner" | "admin" | "member" | "viewer"，用于派生底色和字色
 */
@Composable
fun CnRoleChip(
    role: String,
    colorKey: String,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = colorPair(colorKey)
    Text(
        text = role,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = modifier
            .background(bg, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private fun colorPair(key: String): Pair<Color, Color> = when (key.lowercase()) {
    // 有写权限的角色（OWNER/ADMIN/MEMBER）统一主色软背景，与 Web 端"成员/所有者"视觉一致
    "owner", "admin", "member" -> CnPalette.PrimarySoft to CnPalette.Primary
    // 只读角色用灰色突出"无写权限"
    "viewer" -> CnPalette.SurfaceVariant to CnPalette.TextTertiary
    else -> CnPalette.SurfaceVariant to CnPalette.TextTertiary
}
