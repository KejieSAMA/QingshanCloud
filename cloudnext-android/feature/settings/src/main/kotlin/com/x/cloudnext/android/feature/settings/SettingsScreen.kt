package com.x.cloudnext.android.feature.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.datastore.ThemeMode
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens
import com.x.cloudnext.android.ui.CnTopBar

/**
 * 设置页（Drawer 入口）。iOS-like 分组列表：
 *   每组独立白卡 + medium 圆角；组间 spacing.lg 间距；group title 小字 Tertiary 色。
 *   外观 / 传输 / 存储 / 关于 四组。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val tokens = LocalCnTokens.current

    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Toast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                SettingsEvent.CacheCleared -> Unit
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        CnTopBar(
            title = "设置",
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
        HorizontalDivider(color = CnPalette.DividerHairline)

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = tokens.spacing.md),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg),
            ) {
                item {
                    SettingsGroup(title = "外观") {
                        SettingsRow(
                            title = "主题",
                            subtitle = themeLabel(state.themeMode),
                            onClick = { showThemeDialog = true },
                            showDivider = false,
                        )
                    }
                }

                item {
                    SettingsGroup(title = "传输") {
                        SettingsSwitchRow(
                            title = "仅 WiFi 上传 / 下载",
                            subtitle = "仅影响新入队任务，运行中任务不受影响",
                            checked = state.wifiOnly,
                            onCheckedChange = { viewModel.setWifiOnly(it) },
                            showDivider = false,
                        )
                    }
                }

                item {
                    SettingsGroup(title = "存储") {
                        SettingsRow(
                            title = "清除缓存",
                            subtitle = "清除离线文件列表 / 图片缩略图 / 搜索历史；不影响登录与任务",
                            onClick = { showClearConfirm = true },
                            showDivider = false,
                        )
                    }
                }

                item {
                    SettingsGroup(title = "关于") {
                        SettingsRow(
                            title = "关于 CloudNext",
                            subtitle = null,
                            onClick = onOpenAbout,
                            showDivider = false,
                        )
                    }
                }

                item { Spacer(Modifier.height(tokens.spacing.lg)) }
            }
        }
    }

    if (showThemeDialog) {
        ThemeModeDialog(
            current = state.themeMode,
            onSelect = {
                viewModel.setThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除缓存") },
            text = {
                Text(
                    text = "将清除离线文件列表、图片缩略图、搜索历史。不影响登录状态和上传/下载任务。",
                    color = CnPalette.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    viewModel.clearCache()
                }) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ThemeModeDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalCnTokens.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("主题模式") },
        text = {
            Column {
                ThemeMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = tokens.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = current == mode,
                            onClick = { onSelect(mode) },
                        )
                        Text(
                            text = themeLabel(mode),
                            style = MaterialTheme.typography.bodyLarge,
                            color = CnPalette.TextPrimary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/**
 * iOS-like 分组：标题大写小字 + Tertiary 色 + 内容卡（medium 圆角 + surface 背景）。
 */
@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalCnTokens.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = CnPalette.TextTertiary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(
                start = tokens.spacing.lg,
                end = tokens.spacing.lg,
                bottom = tokens.spacing.sm,
            ),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.md),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    val tokens = LocalCnTokens.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = CnPalette.TextPrimary,
                    fontWeight = FontWeight.Medium,
                )
                subtitle?.let {
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = CnPalette.TextTertiary,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = CnPalette.TextTertiary,
            )
        }
        if (showDivider) {
            HorizontalDivider(
                color = CnPalette.DividerHairline,
                modifier = Modifier.padding(start = tokens.spacing.md),
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = true,
) {
    val tokens = LocalCnTokens.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = CnPalette.TextPrimary,
                    fontWeight = FontWeight.Medium,
                )
                subtitle?.let {
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = CnPalette.TextTertiary,
                    )
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (showDivider) {
            HorizontalDivider(
                color = CnPalette.DividerHairline,
                modifier = Modifier.padding(start = tokens.spacing.md),
            )
        }
    }
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.System -> "跟随系统"
    ThemeMode.Light -> "浅色"
    ThemeMode.Dark -> "深色"
}
