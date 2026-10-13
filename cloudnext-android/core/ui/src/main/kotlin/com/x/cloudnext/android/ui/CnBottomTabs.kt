package com.x.cloudnext.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens

enum class CnTab(val label: String, val outlined: ImageVector, val filled: ImageVector) {
    Home(label = "首页", outlined = Icons.Outlined.Home, filled = Icons.Rounded.Home),
    Files(label = "文件", outlined = Icons.Outlined.Folder, filled = Icons.Rounded.Folder),
    Shared(label = "共享", outlined = Icons.Outlined.PeopleOutline, filled = Icons.Rounded.PeopleAlt),
    Workspaces(label = "协作", outlined = Icons.Outlined.Group, filled = Icons.Rounded.Group),
}

/**
 * 底部导航，4 等分网格。对齐 cloudnext-web MobileBottomTabs.vue。
 *
 * 高 56dp + Safe Bottom；激活色 #0057FF；非激活 TextTertiary。
 * 触摸目标 48dp 高（满足 min target）。
 */
@Composable
fun CnBottomTabs(
    selected: CnTab,
    modifier: Modifier = Modifier,
    onSelect: (CnTab) -> Unit,
) {
    val tokens = LocalCnTokens.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(tokens.dimensions.bottomBarHeight),
        ) {
            CnTab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = selected == tab,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: CnTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) CnPalette.Primary else CnPalette.TextTertiary
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.size(0.dp))
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) tab.filled else tab.outlined,
                contentDescription = tab.label,
                tint = color,
            )
        }
        Spacer(modifier = Modifier.size(2.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
