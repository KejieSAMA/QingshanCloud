package com.x.cloudnext.android.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

/**
 * CloudNext 主题入口。包裹根 Composable：
 *
 *   CnTheme {
 *     NavHost(...)
 *   }
 *
 * 通过 LocalCnTokens.current 读取 mobile.css 扩展令牌。
 *
 * M6：darkTheme 由 caller（MainActivity）从 ThemeMode + isSystemInDarkTheme 派生。
 * SideEffect 内同步 WindowInsetsControllerCompat 状态栏图标颜色（浅 = 黑字 / 深 = 白字）。
 */
@Composable
fun CnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) CnDarkColorScheme else CnLightColorScheme

    // 状态栏 / 导航栏图标颜色随主题切换。EdgeToEdge 已让 system bars 透明，
    // 背景由 Compose 自身画；这里只需要切前景图标的明暗。
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowInsetsControllerCompat(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalCnTokens provides CnTokens()) {
        MaterialTheme(
            colorScheme = colors,
            typography = CnTypography,
            shapes = CnShapes,
            content = content,
        )
    }
}
