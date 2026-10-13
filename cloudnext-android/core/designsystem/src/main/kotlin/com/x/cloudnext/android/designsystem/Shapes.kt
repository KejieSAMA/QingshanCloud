package com.x.cloudnext.android.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 全工程统一圆角系统。所有 Composable 都用 MaterialTheme.shapes.X，禁止直接 RoundedCornerShape(N.dp) 硬编码。
 *
 * 对应 mobile.css 与现代 UI 实践：
 *   extraSmall = 4dp    微小 chip / badge
 *   small      = 8dp    小按钮 / role chip / 状态徽章
 *   medium     = 12dp   卡片 / 图标方块（mobile.css --m-rad-card）
 *   large      = 16dp   Sheet 顶部 / Login card / About logo box
 *   extraLarge = 28dp   M3 AlertDialog 默认槽位（Material spec for Dialog）
 *
 * 注意：**extraLarge 不再是 pill**。Material 3 把 AlertDialog 的默认 shape 放在
 * `shapes.extraLarge` 槽位上（spec 推荐 28.dp）；如果这里用 percent=50，所有 AlertDialog
 * 都会变药丸形。需要 pill / chip 视觉的地方请**显式**用 `RoundedCornerShape(percent = 50)`
 * 或 `CircleShape`（如 CnSearchTopBar.SearchChip、LoginScreen.StatusPill）。
 */
internal val CnShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
