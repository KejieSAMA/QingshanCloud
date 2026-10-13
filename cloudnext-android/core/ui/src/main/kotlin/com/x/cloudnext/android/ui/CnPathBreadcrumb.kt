package com.x.cloudnext.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens

data class BreadcrumbSegment(
    val id: String?,
    val name: String,
)

/**
 * 路径面包屑：[根] › 文件夹A › 文件夹B
 *
 * 最后一段加粗、不可点击；其余段点击触发 onSegmentClick。
 */
@Composable
fun CnPathBreadcrumb(
    segments: List<BreadcrumbSegment>,
    modifier: Modifier = Modifier,
    onSegmentClick: (BreadcrumbSegment) -> Unit = {},
) {
    if (segments.isEmpty()) return
    val tokens = LocalCnTokens.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
        ) {
            items(segments) { seg ->
                val isLast = seg === segments.last()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = seg.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isLast) CnPalette.TextPrimary else CnPalette.TextSecondary,
                        fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = if (!isLast) Modifier.clickable { onSegmentClick(seg) } else Modifier,
                    )
                    if (!isLast) {
                        Text(
                            text = " › ",
                            style = MaterialTheme.typography.labelMedium,
                            color = CnPalette.TextTertiary,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
