package com.x.cloudnext.android.feature.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.x.cloudnext.android.data.drive.DriveItem
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.feature.shared.incoming.IncomingPanel
import com.x.cloudnext.android.feature.shared.outgoing.OutgoingPanel
import com.x.cloudnext.android.ui.CnFilterChipItem
import com.x.cloudnext.android.ui.CnFilterChips
import com.x.cloudnext.android.ui.CnTopBar

/**
 * Shared Tab 根容器。FilterChips 切换两个子面板（对齐 cloudnext-web SharedFilesPage 的 2 tab 设计）：
 *  - incoming：收到的共享（默认）
 *  - outgoing：我发出的
 *
 * 外链管理已独立为 Drawer 入口（[com.x.cloudnext.android.feature.links.LinksRootScreen]），
 * 不再作为本 Screen 的 chip。
 *
 * @param onOpenDrawer Tab 根需要打开主壳层 Drawer
 * @param onOpenSearch TopBar 搜索图标
 * @param onOpenIncomingFolder incoming 中点击文件夹下钻（嵌套路由）
 * @param onOpenFile incoming 中点击文件 → PreviewRouter
 */
@Composable
fun SharedHostScreen(
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenIncomingFolder: (shareId: String, itemId: String, name: String) -> Unit,
    onOpenFile: (DriveItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedKey by rememberSaveable { mutableStateOf("incoming") }

    Column(modifier = modifier.fillMaxSize()) {
        CnTopBar(
            title = "共享文件",
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

        CnFilterChips(
            chips = listOf(
                CnFilterChipItem("incoming", "收到的共享"),
                CnFilterChipItem("outgoing", "我发出的"),
            ),
            selectedKey = selectedKey,
            onSelect = { selectedKey = it },
        )
        HorizontalDivider(color = CnPalette.OutlineSoft)

        when (selectedKey) {
            "incoming" -> IncomingPanel(
                onOpenFolder = onOpenIncomingFolder,
                onOpenFile = onOpenFile,
                modifier = Modifier.fillMaxSize(),
            )
            "outgoing" -> OutgoingPanel(modifier = Modifier.fillMaxSize())
        }
    }
}
