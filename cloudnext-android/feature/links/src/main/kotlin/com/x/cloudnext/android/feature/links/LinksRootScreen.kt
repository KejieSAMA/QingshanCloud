package com.x.cloudnext.android.feature.links

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.ui.CnTopBar

/**
 * 外链管理顶层 Screen（Drawer 入口）。
 *
 * 与 Web 移动端 LinksPage 对齐：标题"外链管理" + 返回 + LinksPanel 内容。
 * 不再嵌入 SharedHostScreen 的 chip slot。
 */
@Composable
fun LinksRootScreen(
    onBack: () -> Unit,
    onOpenAccessLogs: (linkId: String, fileName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        CnTopBar(
            title = "外链管理",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = CnPalette.TextPrimary,
                    )
                }
            },
        )
        HorizontalDivider(color = CnPalette.OutlineSoft)
        LinksPanel(
            onOpenEdit = { _, _ -> /* M5b 暂未实现编辑；M5c 完善 */ },
            onOpenAccessLogs = onOpenAccessLogs,
        )
    }
}
