package com.x.cloudnext.android.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * MaterialTheme 之外的扩展令牌，对应 mobile.css 中的非颜色/字号/形状部分（间距、触控目标、阴影、文件图标色板）。
 *
 * 通过 LocalCnTokens 在 Composable 树中读取：
 *   val tokens = LocalCnTokens.current
 *   Modifier.padding(tokens.spacing.md)
 *
 * 这套设计选自 mobile.css，是 Web 与 Android 视觉对齐的唯一权威来源；
 * 当 Web 调整 mobile.css 时，需同步更新此处。
 */
@Immutable
data class CnSpacing(
    val xs: Dp = 4.dp,   // --m-sp-1
    val sm: Dp = 8.dp,   // --m-sp-2
    val md: Dp = 12.dp,  // --m-sp-3
    val lg: Dp = 16.dp,  // --m-sp-4
    val xl: Dp = 20.dp,  // --m-sp-5
)

@Immutable
data class CnTouchTarget(
    val min: Dp = 44.dp,      // --m-touch-h
    val comfort: Dp = 48.dp,  // --m-tap-h
)

@Immutable
data class CnShadowSpec(
    val color: Color,
    val offsetX: Dp,
    val offsetY: Dp,
    val blur: Dp,
)

@Immutable
data class CnShadows(
    val bar: CnShadowSpec = CnShadowSpec(
        color = Color(0x0F0F172A),
        offsetX = 0.dp,
        offsetY = 1.dp,
        blur = 0.dp,
    ),
    val fab: CnShadowSpec = CnShadowSpec(
        color = Color(0x470057FF),  // rgba(0,87,255,0.28) — 0x47 = 71/255 ≈ 0.28
        offsetX = 0.dp,
        offsetY = 6.dp,
        blur = 16.dp,
    ),
    val sheet: CnShadowSpec = CnShadowSpec(
        color = Color(0x1F0F172A),  // rgba(15,23,42,0.12)
        offsetX = 0.dp,
        offsetY = (-8).dp,
        blur = 24.dp,
    ),
)

/**
 * 文件类型 → (背景色, 前景色) 映射。命名与 cloudnext-web 的 fileType 值对齐。
 */
@Immutable
data class CnFileIconColors(
    val backgrounds: Map<String, Color> = mapOf(
        "folder" to CnPalette.FolderBg,
        "word" to CnPalette.WordBg,
        "excel" to CnPalette.ExcelBg,
        "ppt" to CnPalette.PptBg,
        "pdf" to CnPalette.PdfBg,
        "video" to CnPalette.VideoBg,
        "image" to CnPalette.ImageBg,
        "audio" to CnPalette.VideoBg,
        "archive" to CnPalette.GenericBg,
        "code" to CnPalette.GenericBg,
        "text" to CnPalette.GenericBg,
        "file" to CnPalette.GenericBg,
    ),
    val foregrounds: Map<String, Color> = mapOf(
        "folder" to CnPalette.FolderFg,
        "word" to CnPalette.WordFg,
        "excel" to CnPalette.ExcelFg,
        "ppt" to CnPalette.PptFg,
        "pdf" to CnPalette.PdfFg,
        "video" to CnPalette.VideoFg,
        "image" to CnPalette.ImageFg,
        "audio" to CnPalette.VideoFg,
        "archive" to CnPalette.GenericFg,
        "code" to CnPalette.GenericFg,
        "text" to CnPalette.GenericFg,
        "file" to CnPalette.GenericFg,
    ),
) {
    fun background(kind: String): Color =
        backgrounds[kind] ?: CnPalette.GenericBg

    fun foreground(kind: String): Color =
        foregrounds[kind] ?: CnPalette.GenericFg
}

@Immutable
data class CnDimensions(
    val topBarHeight: Dp = 48.dp,
    val bottomBarHeight: Dp = 56.dp,
    val fileIconBox: Dp = 40.dp,
    val fileCardMinHeight: Dp = 60.dp,
    val iconSize: Dp = 22.dp,
    // ==================== M6 风格统一新增 ====================
    /** ActionSheet 顶部圆角 = mobile.css --m-rad-sheet */
    val sheetCornerRadius: Dp = 16.dp,
    /** ActionSheet 拖把手宽度 */
    val sheetDragHandleWidth: Dp = 40.dp,
    /** ActionSheet 拖把手高度 */
    val sheetDragHandleHeight: Dp = 4.dp,
    /** 搜索 chip 高度 = mobile.css .search-input 38px */
    val searchChipHeight: Dp = 38.dp,
)

@Immutable
data class CnTokens(
    val spacing: CnSpacing = CnSpacing(),
    val touch: CnTouchTarget = CnTouchTarget(),
    val shadows: CnShadows = CnShadows(),
    val fileIcon: CnFileIconColors = CnFileIconColors(),
    val dimensions: CnDimensions = CnDimensions(),

    /** 长按多选阈值 ms，严格对齐 MobileFileCard.vue:59 LONG_PRESS_MS. */
    val longPressMs: Long = 480,

    /** 长按时允许的最大手指移动像素，对齐 MobileFileCard.vue:60 MOVE_TOLERANCE. */
    val longPressSlopDp: Dp = 10.dp,
)

val LocalCnTokens = compositionLocalOf { CnTokens() }
