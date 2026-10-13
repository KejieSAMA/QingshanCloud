package com.x.cloudnext.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens

/**
 * M1 验收页：把所有 mobile.css 令牌渲染成可视化矩阵，方便与 Web 移动端同屏对照。
 */
@Composable
fun DesignSystemPreview() {
    val tokens = LocalCnTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(tokens.spacing.md),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg),
    ) {
        SectionHeader("Typography")
        TypographyDemo()

        SectionHeader("Colors")
        ColorSwatchRow(
            listOf(
                "Primary" to CnPalette.Primary,
                "Primary Soft" to CnPalette.PrimarySoft,
                "Background" to CnPalette.Background,
                "Surface" to CnPalette.Surface,
            ),
        )
        ColorSwatchRow(
            listOf(
                "Text Primary" to CnPalette.TextPrimary,
                "Text Secondary" to CnPalette.TextSecondary,
                "Text Tertiary" to CnPalette.TextTertiary,
                "Outline Soft" to CnPalette.OutlineSoft,
            ),
        )
        ColorSwatchRow(
            listOf(
                "Error" to CnPalette.Error,
                "Success" to CnPalette.Success,
                "Warning" to CnPalette.Warning,
            ),
        )

        SectionHeader("File Type Icons")
        FileIconGrid()

        SectionHeader("Spacing")
        SpacingDemo()

        SectionHeader("Touch Targets / Heights")
        TouchTargetDemo()

        SectionHeader("Shapes")
        ShapesDemo()
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = CnPalette.TextPrimary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun TypographyDemo() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("titleLarge · 17sp SemiBold · 标题样式", style = MaterialTheme.typography.titleLarge)
        Text("titleMedium · 15sp · 章节标题", style = MaterialTheme.typography.titleMedium)
        Text("bodyLarge · 14sp · 文件名 / 主文本", style = MaterialTheme.typography.bodyLarge)
        Text("bodyMedium · 14sp", style = MaterialTheme.typography.bodyMedium)
        Text(
            "labelSmall · 12sp · 元数据",
            style = MaterialTheme.typography.labelSmall,
            color = CnPalette.TextTertiary,
        )
    }
}

@Composable
private fun ColorSwatchRow(entries: List<Pair<String, Color>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        entries.forEach { (label, color) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(color, RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun FileIconGrid() {
    val tokens = LocalCnTokens.current
    val kinds = listOf("folder", "word", "excel", "ppt", "pdf", "video", "image")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        kinds.forEach { kind ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(tokens.dimensions.fileIconBox)
                        .background(
                            tokens.fileIcon.background(kind),
                            shape = MaterialTheme.shapes.medium,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        kind.first().uppercase(),
                        color = tokens.fileIcon.foreground(kind),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(kind, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SpacingDemo() {
    val tokens = LocalCnTokens.current
    val items = listOf(
        "xs · 4" to tokens.spacing.xs,
        "sm · 8" to tokens.spacing.sm,
        "md · 12" to tokens.spacing.md,
        "lg · 16" to tokens.spacing.lg,
        "xl · 20" to tokens.spacing.xl,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, dp) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(dp)
                        .height(16.dp)
                        .background(CnPalette.Primary, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun TouchTargetDemo() {
    val tokens = LocalCnTokens.current
    val items = listOf(
        "touch.min · 44" to tokens.touch.min,
        "touch.comfort · 48" to tokens.touch.comfort,
        "topBar · 48" to tokens.dimensions.topBarHeight,
        "bottomBar · 56" to tokens.dimensions.bottomBarHeight,
        "fileIconBox · 40" to tokens.dimensions.fileIconBox,
        "fileCardMin · 60" to tokens.dimensions.fileCardMinHeight,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, dp) ->
            DimensionBar(label, dp)
        }
    }
}

@Composable
private fun DimensionBar(label: String, height: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier
                .width(120.dp)
                .height(height),
            color = CnPalette.PrimarySoft,
            shape = RoundedCornerShape(8.dp),
        ) {}
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ShapesDemo() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = MaterialTheme.shapes.small,
            color = CnPalette.Surface,
            shadowElevation = 2.dp,
        ) { Center("small") }
        Surface(
            modifier = Modifier.size(64.dp),
            shape = MaterialTheme.shapes.medium,
            color = CnPalette.Surface,
            shadowElevation = 2.dp,
        ) { Center("medium") }
        Surface(
            modifier = Modifier.size(64.dp),
            shape = MaterialTheme.shapes.large,
            color = CnPalette.Surface,
            shadowElevation = 2.dp,
        ) { Center("large") }
        Surface(
            modifier = Modifier.size(width = 96.dp, height = 32.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = CnPalette.PrimarySoft,
        ) { Center("pill") }
    }
}

@Composable
private fun Center(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = CnPalette.TextSecondary)
    }
}
