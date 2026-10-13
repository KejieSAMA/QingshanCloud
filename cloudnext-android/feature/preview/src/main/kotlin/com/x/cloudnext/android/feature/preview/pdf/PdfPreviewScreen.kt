package com.x.cloudnext.android.feature.preview.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.ui.CnError
import com.x.cloudnext.android.ui.CnLoading
import com.x.cloudnext.android.ui.CnTopBar

@Composable
fun PdfPreviewScreen(
    name: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PdfPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        CnTopBar(
            title = name,
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

        when (val s = state) {
            PdfUiState.Loading -> CnLoading()
            is PdfUiState.Failed -> CnError(message = s.message, onRetry = { viewModel.retry() })
            is PdfUiState.Ready -> PdfPagesList(file = s.file)
        }
    }
}

@Composable
private fun PdfPagesList(file: java.io.File) {
    val controller = remember(file) { PdfRendererController(file) }
    DisposableEffect(controller) {
        onDispose { controller.close() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFE5E7EB),  // PDF 灰底
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(controller.pageCount, key = { it }) { index ->
                PdfPage(
                    controller = controller,
                    index = index,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PdfPage(
    controller: PdfRendererController,
    index: Int,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(controller, index) { mutableStateOf<Bitmap?>(null) }
    val density = LocalDensity.current

    LaunchedEffect(controller, index) {
        // 假设页宽按 480dp 渲染，足够流畅且不爆内存。可在 polish 时按屏宽实测。
        val widthPx = with(density) { 480.dp.toPx() }.toInt()
        bitmap = controller.renderPageAsync(index, widthPx)
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "PDF page ${index + 1}",
            modifier = modifier
                .fillMaxWidth()
                .background(Color.White),
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.414f)
                .background(Color.White),
        )
    }
}
