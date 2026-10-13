package com.x.cloudnext.android.feature.shared.org

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.data.org.OrgSearchResult
import com.x.cloudnext.android.data.share.ShareTarget
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens
import com.x.cloudnext.android.ui.CnEmpty
import com.x.cloudnext.android.ui.CnError
import com.x.cloudnext.android.ui.CnFilterChipItem
import com.x.cloudnext.android.ui.CnFilterChips
import com.x.cloudnext.android.ui.CnLoading

/**
 * 选择模式。
 * - Multi：多选，确定按钮在 TopBar 右上，下方显示已选 chips
 * - Single：单选，点击候选立即 confirm + 关闭；不显示已选 chips；常用于 Workspace 添加成员
 */
enum class OrgPickerMode { Multi, Single }

/**
 * 共享对象选择器（全屏）。
 *
 * 退出方式：
 *  - Multi 模式「确定」→ onConfirm(selected)，由 caller 把 selected 编码进 savedStateHandle 传回
 *  - Single 模式点击候选 → onConfirm(listOf(target)) 立即返回
 *  - 左 X / 系统返回 → onCancel
 *
 * UI：上 chip（联系人/部门），下搜索框 + 已选 chips（横滚，仅 Multi），下方候选列表。
 * 候选搜索 debounce 300ms（ViewModel 处理）。
 */
@Composable
fun OrgPickerScreen(
    onCancel: () -> Unit,
    onConfirm: (List<ShareTarget>) -> Unit,
    modifier: Modifier = Modifier,
    mode: OrgPickerMode = OrgPickerMode.Multi,
    title: String = if (mode == OrgPickerMode.Single) "选择人员" else "选择共享对象",
    showDepartmentTab: Boolean = mode == OrgPickerMode.Multi,
    viewModel: OrgPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val tokens = LocalCnTokens.current

    Column(modifier = modifier.fillMaxSize()) {
        // TopBar：左 X / 标题 / 右"确定"
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = tokens.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Outlined.Close, contentDescription = "取消", tint = CnPalette.TextPrimary)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = CnPalette.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (mode == OrgPickerMode.Multi) {
                    TextButton(
                        onClick = { onConfirm(state.selected) },
                        enabled = state.selected.isNotEmpty(),
                    ) {
                        Text(
                            text = if (state.selected.isEmpty()) "确定" else "确定 (${state.selected.size})",
                            color = if (state.selected.isEmpty()) CnPalette.TextTertiary else CnPalette.Primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = CnPalette.OutlineSoft)

        // Type chips（Single 模式默认只人员，不显示部门 tab）
        if (showDepartmentTab) {
            CnFilterChips(
                chips = listOf(
                    CnFilterChipItem("person", "联系人"),
                    CnFilterChipItem("department", "部门"),
                ),
                selectedKey = state.type.raw,
                onSelect = { key ->
                    val t = if (key == "department") OrgPickerType.Department else OrgPickerType.Person
                    viewModel.setType(t)
                },
            )
        }

        // Keyword input
        OutlinedTextField(
            value = state.keyword,
            onValueChange = { viewModel.setKeyword(it) },
            placeholder = { Text("搜索${if (state.type == OrgPickerType.Person) "联系人" else "部门"}名称") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.md),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            trailingIcon = if (state.keyword.isNotEmpty()) {
                {
                    IconButton(onClick = { viewModel.setKeyword("") }) {
                        Icon(
                            Icons.Outlined.Cancel,
                            contentDescription = "清空",
                            tint = CnPalette.TextTertiary,
                        )
                    }
                }
            } else null,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        Spacer(modifier = Modifier.size(tokens.spacing.sm))

        // 已选 chips（横滚，仅 Multi 模式）
        if (mode == OrgPickerMode.Multi && state.selected.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = tokens.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            ) {
                state.selected.forEach { target ->
                    SelectedChip(target = target, onRemove = { viewModel.remove(target) })
                }
            }
            Spacer(modifier = Modifier.size(tokens.spacing.sm))
        }

        HorizontalDivider(color = CnPalette.OutlineSoft)

        // 候选列表
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when {
                state.loading -> CnLoading()
                state.errorMessage != null && state.candidates.isEmpty() ->
                    CnError(message = state.errorMessage!!)
                state.candidates.isEmpty() && state.keyword.isBlank() ->
                    CnEmpty(message = "输入关键词搜索", hint = "上方框输入姓名或部门名称")
                state.candidates.isEmpty() ->
                    CnEmpty(message = "未找到匹配项")
                else -> LazyColumn {
                    items(state.candidates, key = { "${it.type.raw}-${it.id}" }) { candidate ->
                        val selected = remember(state.selected, candidate, mode) {
                            mode == OrgPickerMode.Multi &&
                                state.selected.any { it.id == candidate.id && it.type.raw == candidate.type.raw }
                        }
                        CandidateRow(
                            candidate = candidate,
                            selected = selected,
                            showCheckmark = mode == OrgPickerMode.Multi,
                            onClick = {
                                val target = ShareTarget(
                                    type = when (candidate.type) {
                                        OrgSearchResult.Type.Person -> ShareTarget.TargetType.Person
                                        OrgSearchResult.Type.Department -> ShareTarget.TargetType.Department
                                    },
                                    id = candidate.id,
                                    name = candidate.name,
                                )
                                when (mode) {
                                    OrgPickerMode.Multi -> {
                                        if (selected) viewModel.remove(target) else viewModel.add(candidate)
                                    }
                                    OrgPickerMode.Single -> {
                                        // 单选：立即返回并退出
                                        onConfirm(listOf(target))
                                    }
                                }
                            },
                        )
                        HorizontalDivider(color = CnPalette.DividerHairline)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedChip(target: ShareTarget, onRemove: () -> Unit) {
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

@Composable
private fun CandidateRow(
    candidate: OrgSearchResult,
    selected: Boolean,
    showCheckmark: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalCnTokens.current
    val isPerson = candidate.type == OrgSearchResult.Type.Person
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        Icon(
            imageVector = if (isPerson) Icons.Outlined.AccountCircle else Icons.Outlined.Apartment,
            contentDescription = null,
            tint = if (isPerson) CnPalette.Primary else CnPalette.PptFg,
            modifier = Modifier.size(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.name,
                style = MaterialTheme.typography.bodyLarge,
                color = CnPalette.TextPrimary,
            )
            candidate.path?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = CnPalette.TextTertiary,
                )
            }
        }
        if (showCheckmark) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(CnPalette.Primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "已选",
                        tint = CnPalette.OnPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(CnPalette.Surface, CircleShape)
                        .padding(2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Transparent, CircleShape),
                    )
                }
            }
        }
    }
}
