package com.x.cloudnext.android.feature.preview.text

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.common.formatFileSize
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens
import com.x.cloudnext.android.ui.CnError
import com.x.cloudnext.android.ui.CnLoading
import com.x.cloudnext.android.ui.CnTopBar

/**
 * 纯文本类原生预览。覆盖 txt / md / log / toml / yaml / json / sh / kt / java / py 等
 * 所有 FileKind.TEXT 与 FileKind.CODE。
 *
 * 设计要点：
 * - 等宽字体（Monospace）便于代码/配置阅读
 * - 水平滚动 + softWrap=false，长行不强制换行（代码场景必需）
 * - SelectionContainer 允许选择复制
 * - 1MB 截断 + 警告提示（防 OOM）
 */
@Composable
fun TextPreviewScreen(
    name: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TextPreviewViewModel = hiltViewModel(),
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
            TextUiState.Loading -> CnLoading()
            is TextUiState.Failed -> CnError(message = s.message, onRetry = { viewModel.retry() })
            is TextUiState.Ready -> TextBody(s)
        }
    }
}

@Composable
private fun TextBody(s: TextUiState.Ready) {
    val tokens = LocalCnTokens.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(tokens.spacing.md),
        ) {
            if (s.truncated) {
                Text(
                    text = "⚠ 文件较大（${formatFileSize(s.sizeBytes)}），仅显示前 1 MB。如需完整内容请下载到本地查看。",
                    style = MaterialTheme.typography.labelSmall,
                    color = CnPalette.Warning,
                    modifier = Modifier.padding(bottom = tokens.spacing.sm),
                )
            }
            SelectionContainer(modifier = Modifier.fillMaxSize()) {
                // 双向滚动：纵向看更多行 / 横向看长行不换行
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = s.content,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = CnPalette.TextPrimary,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
