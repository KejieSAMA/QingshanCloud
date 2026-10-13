package com.x.cloudnext.android.feature.shared.create

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.data.share.SharePermission
import com.x.cloudnext.android.data.share.ShareTarget
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens
import com.x.cloudnext.android.ui.CnFilterChipItem
import com.x.cloudnext.android.ui.CnFilterChips
import com.x.cloudnext.android.ui.CnTopBar

/**
 * 创建分享（全屏路由）。
 *
 * 从 MyFiles ActionSheet「分享」/ SelectionBar「分享」进入；需要全屏才能干净地跳 OrgPicker 再回。
 *
 * 字段：共享对象 chips + 「添加」按钮 / 权限 / 有效期 / 允许转发
 *
 * @param itemIds 被分享的文件 id
 * @param presetTargets 从 OrgPicker 回传的目标列表（caller 通过 savedStateHandle 解码后传入）
 * @param onBack 关闭并返回（caller 应同时清理 savedStateHandle["share.targets"]）
 * @param onPickOrg 进 OrgPicker 选择目标；caller 拿到结果再以新 [presetTargets] 重新组合
 */
@Composable
fun CreateShareScreen(
    itemIds: List<String>,
    presetTargets: List<ShareTarget>,
    onBack: () -> Unit,
    onPickOrg: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateShareViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val tokens = LocalCnTokens.current

    LaunchedEffect(itemIds) { viewModel.setItemIds(itemIds) }
    LaunchedEffect(presetTargets) {
        if (presetTargets.isNotEmpty()) viewModel.setTargets(presetTargets)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CreateShareEvent.Toast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is CreateShareEvent.Submitted -> onBack()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        CnTopBar(
            title = "创建分享",
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

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(tokens.spacing.md),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier.padding(tokens.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "被分享文件",
                                style = MaterialTheme.typography.labelLarge,
                                color = CnPalette.TextSecondary,
                            )
                            Text(
                                text = "${itemIds.size} 项",
                                style = MaterialTheme.typography.labelMedium,
                                color = CnPalette.TextTertiary,
                            )
                        }
                    }
                }

                // 共享对象
                FormSection(title = "共享对象") {
                    if (state.targets.isEmpty()) {
                        TextButton(
                            onClick = onPickOrg,
                            colors = ButtonDefaults.textButtonColors(contentColor = CnPalette.Primary),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("添加共享对象")
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            state.targets.forEach { target ->
                                SelectedTargetChip(target = target, onRemove = { viewModel.removeTarget(target) })
                            }
                            TextButton(onClick = onPickOrg) {
                                Icon(Icons.Outlined.Add, contentDescription = null)
                                Spacer(modifier = Modifier.size(2.dp))
                                Text("继续添加")
                            }
                        }
                    }
                }

                FormSection(title = "权限") {
                    CnFilterChips(
                        chips = SharePermission.values().map { CnFilterChipItem(it.raw, it.label) },
                        selectedKey = state.permission.raw,
                        onSelect = { key ->
                            val target = SharePermission.values().firstOrNull { it.raw == key } ?: SharePermission.Read
                            viewModel.setPermission(target)
                        },
                    )
                }

                FormSection(title = "有效期") {
                    CnFilterChips(
                        chips = ExpiryPreset.values().map { CnFilterChipItem(it.name, it.label) },
                        selectedKey = state.expiry.name,
                        onSelect = { key ->
                            val target = ExpiryPreset.values().firstOrNull { it.name == key } ?: ExpiryPreset.Forever
                            viewModel.setExpiry(target)
                        },
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "允许接收方再分享",
                                style = MaterialTheme.typography.bodyLarge,
                                color = CnPalette.TextPrimary,
                            )
                            Text(
                                text = "对方可以把该文件转发给其他人",
                                style = MaterialTheme.typography.labelSmall,
                                color = CnPalette.TextTertiary,
                            )
                        }
                        Switch(
                            checked = state.canReshare,
                            onCheckedChange = { viewModel.toggleReshare() },
                        )
                    }
                }

                Button(
                    onClick = { viewModel.submit() },
                    enabled = !state.submitting && state.targets.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.submitting) "创建中..." else "创建分享")
                }
            }
        }
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    val tokens = LocalCnTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = CnPalette.TextSecondary,
            )
            content()
        }
    }
}

@Composable
private fun SelectedTargetChip(target: ShareTarget, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .background(CnPalette.PrimarySoft, CircleShape)
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = target.name,
            style = MaterialTheme.typography.labelSmall,
            color = CnPalette.Primary,
        )
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(CnPalette.Primary.copy(alpha = 0.18f), CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "移除",
                tint = CnPalette.Primary,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
