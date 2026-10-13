package com.x.cloudnext.android.feature.preview.image

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.x.cloudnext.android.designsystem.LocalCnTokens
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage

/**
 * 全屏图片预览。黑底 + ZoomableAsyncImage（双指缩放 / 双击重置）+ 顶部 TopBar 自动隐藏（点击切换）。
 *
 * 图片 URL 直接走 /api/drive/items/{itemId}/content（不调 getPreview API）。
 * Coil ImageLoader 全局注入了共享 OkHttp，自动带 X-CloudNext-Token。
 */
@Composable
fun ImagePreviewScreen(
    itemId: String,
    name: String,
    baseUrl: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val url = remember(baseUrl, itemId) {
        baseUrl.trimEnd('/') + "/api/drive/items/" + itemId + "/content"
    }
    var chromeVisible by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { chromeVisible = !chromeVisible },
    ) {
        ZoomableAsyncImage(
            model = url,
            contentDescription = name,
            modifier = Modifier.fillMaxSize(),
        )

        if (chromeVisible) {
            ChromeOverlay(name = name, onBack = onBack)
        }
    }
}

@Composable
private fun ChromeOverlay(name: String, onBack: () -> Unit) {
    val tokens = LocalCnTokens.current
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xCC000000),
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(end = tokens.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White,
                    )
                }
                Text(
                    text = name,
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Box(modifier = Modifier.weight(1f))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xCC000000),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "点击屏幕显示/隐藏控制",
                    color = Color.White.copy(alpha = 0.7f),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
