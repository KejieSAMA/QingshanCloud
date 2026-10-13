package com.x.cloudnext.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens

/**
 * 单条 chip 的数据。
 *
 * @param key 唯一标识（onSelect 回传）
 * @param label 展示文案
 */
data class CnFilterChipItem(
    val key: String,
    val label: String,
)

/**
 * 横滚 Filter Chips。对齐 cloudnext-web `MobileFilterChips.vue`：
 *
 * - 圆角 999px（CircleShape）
 * - 选中态：Primary 主色 + 白字
 * - 未选中：灰底 + 文字主色
 *
 * 用于：回收站三筛、Shared Tab 切换、Search 文件类型筛选。
 */
@Composable
fun CnFilterChips(
    chips: List<CnFilterChipItem>,
    selectedKey: String,
    onSelect: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalCnTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
    ) {
        chips.forEach { chip ->
            val selected = chip.key == selectedKey
            val background = if (selected) CnPalette.Primary else CnPalette.SurfaceVariant
            val textColor = if (selected) CnPalette.OnPrimary else CnPalette.TextPrimary
            Text(
                text = chip.label,
                color = textColor,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .background(color = background, shape = CircleShape)
                    .clickable { onSelect(chip.key) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}
