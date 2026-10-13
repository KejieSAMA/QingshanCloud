package com.x.cloudnext.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens

/** 空状态占位。 */
@Composable
fun CnEmpty(
    message: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    val tokens = LocalCnTokens.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(tokens.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = CnPalette.TextSecondary,
        )
        if (!hint.isNullOrBlank()) {
            Spacer(modifier = Modifier.size(tokens.spacing.sm))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = CnPalette.TextTertiary,
            )
        }
    }
}

/** 加载占位（居中）。 */
@Composable
fun CnLoading(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** 错误占位 + 重试。 */
@Composable
fun CnError(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val tokens = LocalCnTokens.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(tokens.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = CnPalette.Error,
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.size(tokens.spacing.sm))
            TextButton(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}
