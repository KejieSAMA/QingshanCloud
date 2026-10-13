package com.x.cloudnext.android.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 颜色定义严格映射 cloudnext-web/src/styles/mobile.css 与全局色板。
 *
 * 主色 `#0057FF`、底色 `#F7F8FA`、surface `#FFFFFF`、文本灰度从 mobile 全局 token 推导。
 * 文件类型图标的背景色与前景色，1:1 复刻 MobileFileCard.vue 中的 .m-file-icon-*。
 */
object CnPalette {
    val Primary = Color(0xFF0057FF)
    val OnPrimary = Color.White
    val PrimarySoft = Color(0xFFE6EEFF)

    val Background = Color(0xFFF7F8FA)
    val Surface = Color.White
    val SurfaceVariant = Color(0xFFF1F3F7)

    val OutlineSoft = Color(0x0F0F172A)      // rgba(15,23,42,0.06)
    val OutlineStrong = Color(0x290F172A)    // rgba(15,23,42,0.16)

    val TextPrimary = Color(0xFF0F172A)
    val TextSecondary = Color(0xFF475569)
    val TextTertiary = Color(0xFF94A3B8)

    val Error = Color(0xFFE53935)
    val Success = Color(0xFF16A34A)
    val Warning = Color(0xFFF59E0B)

    // ==================== M6 风格统一新增（mobile.css 对照） ====================

    /** 容器背景层。mobile.css --bg-panel-soft，搜索 chip 背景 / FAB 取消卡背景。 */
    val PanelSoft = Color(0xFFF1F3F7)

    /** 列表项激活态背景。mobile.css --bg-nav-hover。 */
    val NavHover = Color(0x0F0F172A)

    /** 危险操作软背景。mobile.css --bg-danger-soft，删除按压态 / 撤销卡片。 */
    val DangerSoft = Color(0xFFFFEBEE)

    /** 成功状态软背景。badge 类组件 active 态 / 任务成功标记。 */
    val SuccessSoft = Color(0xFFDCFCE7)

    /** 卡内 inner divider 专用极淡分隔色。mobile.css rgba(15,23,42,0.04)。 */
    val DividerHairline = Color(0x0A0F172A)

    // 文件类型背景与前景色（MobileFileCard.vue:199-232）
    val FolderBg = Color(0xFFFFC107).copy(alpha = 0.12f)
    val FolderFg = Color(0xFFD97706)
    val WordBg = Color(0xFF2F6BFF).copy(alpha = 0.10f)
    val WordFg = Color(0xFF2F6BFF)
    val ExcelBg = Color(0xFF22C55E).copy(alpha = 0.12f)
    val ExcelFg = Color(0xFF16A34A)
    val PptBg = Color(0xFFF56527).copy(alpha = 0.12f)
    val PptFg = Color(0xFFEA580C)
    val PdfBg = Color(0xFFDC2626).copy(alpha = 0.10f)
    val PdfFg = Color(0xFFDC2626)
    val VideoBg = Color(0xFFA855F7).copy(alpha = 0.12f)
    val VideoFg = Color(0xFF9333EA)
    val ImageBg = Color(0xFF0EA5E9).copy(alpha = 0.12f)
    val ImageFg = Color(0xFF0284C7)
    val GenericBg = Color(0xFFF1F3F7)
    val GenericFg = Color(0xFF475569)
}

internal val CnLightColorScheme: ColorScheme = lightColorScheme(
    primary = CnPalette.Primary,
    onPrimary = CnPalette.OnPrimary,
    primaryContainer = CnPalette.PrimarySoft,
    onPrimaryContainer = CnPalette.Primary,
    background = CnPalette.Background,
    onBackground = CnPalette.TextPrimary,
    surface = CnPalette.Surface,
    onSurface = CnPalette.TextPrimary,
    surfaceVariant = CnPalette.SurfaceVariant,
    onSurfaceVariant = CnPalette.TextSecondary,
    outline = CnPalette.OutlineSoft,
    outlineVariant = CnPalette.OutlineSoft,
    error = CnPalette.Error,
    onError = Color.White,
)

internal val CnDarkColorScheme: ColorScheme = darkColorScheme(
    primary = CnPalette.Primary,
    onPrimary = CnPalette.OnPrimary,
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0x29FFFFFF),
    error = CnPalette.Error,
    onError = Color.White,
)
