package com.x.cloudnext.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.x.cloudnext.android.network.BuildConfig
import com.x.cloudnext.android.ui.CnTopBar

/**
 * 关于页。静态信息：App 版本、后端 API 地址（仅 debug 显示）、开源信息占位。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    appVersion: String,
    versionCode: Int,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalCnTokens.current

    Column(modifier = modifier.fillMaxSize()) {
        CnTopBar(
            title = "关于",
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
                    .padding(tokens.spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            ) {
                Spacer(modifier = Modifier.height(tokens.spacing.xl))

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(CnPalette.PrimarySoft, MaterialTheme.shapes.large),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Cloud,
                        contentDescription = null,
                        tint = CnPalette.Primary,
                        modifier = Modifier.size(48.dp),
                    )
                }

                Text(
                    text = "CloudNext",
                    style = MaterialTheme.typography.headlineSmall,
                    color = CnPalette.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "版本 $appVersion ($versionCode)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CnPalette.TextSecondary,
                )

                Spacer(modifier = Modifier.height(tokens.spacing.md))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(modifier = Modifier.padding(tokens.spacing.md)) {
                        InfoRow("应用 ID", "com.x.cloudnext.android")
                        if (BuildConfig.DEBUG) {
                            InfoRow("后端 API", BuildConfig.API_BASE_URL)
                        }
                        InfoRow("构建类型", if (BuildConfig.DEBUG) "Debug" else "Release")
                    }
                }

                Spacer(modifier = Modifier.height(tokens.spacing.lg))

                Text(
                    text = "© 2025 O2OA · 内部协作云盘",
                    style = MaterialTheme.typography.labelSmall,
                    color = CnPalette.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val tokens = LocalCnTokens.current
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = CnPalette.TextSecondary,
            modifier = Modifier.padding(end = tokens.spacing.md),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = CnPalette.TextPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}
