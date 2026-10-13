package com.x.cloudnext.android.feature.links

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.data.link.LinkPermission
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens
import com.x.cloudnext.android.ui.CnFilterChipItem
import com.x.cloudnext.android.ui.CnFilterChips
import com.x.cloudnext.android.ui.CnTopBar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 创建外链页（全屏路由）。MyFiles ActionSheet「创建外链」触发，仅单文件。
 *
 * 字段：
 *  - 权限：仅预览 / 可下载
 *  - 密码（switch + 输入框）
 *  - 下载次数：不限 / 5 / 10 / 自定义
 *  - 有效期：永久 / 7 天 / 30 天 / 自定义（M5b 简化：M5c 加 DatePicker）
 *
 * 提交后由 LinksViewModel 创建 + Toast，自动 popBackStack。
 */
@Composable
fun CreateLinkScreen(
    itemId: String,
    fileName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LinksViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val tokens = LocalCnTokens.current

    var permission by remember { mutableStateOf(LinkPermission.Preview) }
    var enablePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var limitChoice by remember { mutableStateOf("unlimited") }
    var customLimit by remember { mutableStateOf("") }
    var expiryChoice by remember { mutableStateOf("forever") }

    // 创建成功后 Toast 触发返回
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LinksEvent.Toast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                    if (event.message == "已创建外链") onBack()
                }
                else -> Unit
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        CnTopBar(
            title = "创建外链",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = CnPalette.TextPrimary)
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
                FormCard(title = "被分享文件") {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = CnPalette.TextPrimary,
                    )
                }

                FormCard(title = "权限") {
                    CnFilterChips(
                        chips = LinkPermission.values().map { CnFilterChipItem(it.raw, it.label) },
                        selectedKey = permission.raw,
                        onSelect = { key ->
                            permission = LinkPermission.values().firstOrNull { it.raw == key } ?: LinkPermission.Preview
                        },
                    )
                }

                FormCard(title = "访问密码") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "启用密码保护",
                            style = MaterialTheme.typography.bodyLarge,
                            color = CnPalette.TextPrimary,
                        )
                        Switch(checked = enablePassword, onCheckedChange = { enablePassword = it })
                    }
                    if (enablePassword) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = { Text("4-32 字符") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        )
                    }
                }

                FormCard(title = "下载次数限制") {
                    CnFilterChips(
                        chips = listOf(
                            CnFilterChipItem("unlimited", "不限"),
                            CnFilterChipItem("5", "5 次"),
                            CnFilterChipItem("10", "10 次"),
                            CnFilterChipItem("custom", "自定义"),
                        ),
                        selectedKey = limitChoice,
                        onSelect = { limitChoice = it },
                    )
                    if (limitChoice == "custom") {
                        OutlinedTextField(
                            value = customLimit,
                            onValueChange = { customLimit = it.filter(Char::isDigit) },
                            placeholder = { Text("1 - 1000000") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }

                FormCard(title = "有效期") {
                    CnFilterChips(
                        chips = listOf(
                            CnFilterChipItem("forever", "永久"),
                            CnFilterChipItem("7", "7 天"),
                            CnFilterChipItem("30", "30 天"),
                        ),
                        selectedKey = expiryChoice,
                        onSelect = { expiryChoice = it },
                    )
                }

                Button(
                    onClick = {
                        viewModel.create(
                            itemId = itemId,
                            permission = permission,
                            password = if (enablePassword && password.isNotBlank()) password else null,
                            downloadLimit = resolveLimit(limitChoice, customLimit),
                            expiresAt = resolveExpiry(expiryChoice),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("创建外链")
                }
            }
        }
    }
}

@Composable
private fun FormCard(title: String, content: @Composable () -> Unit) {
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

private fun resolveLimit(choice: String, custom: String): Long? = when (choice) {
    "unlimited" -> -1L
    "5" -> 5L
    "10" -> 10L
    "custom" -> custom.toLongOrNull()?.takeIf { it in 1..1_000_000L }
    else -> null
}

private fun resolveExpiry(choice: String): String? {
    val days = when (choice) {
        "7" -> 7
        "30" -> 30
        else -> return null
    }
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.time = Date()
    cal.add(Calendar.DAY_OF_YEAR, days)
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(cal.time)
}
