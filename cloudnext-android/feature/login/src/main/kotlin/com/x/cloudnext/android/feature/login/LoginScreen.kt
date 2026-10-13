package com.x.cloudnext.android.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens

/**
 * 登录页。视觉对齐 cloudnext-web/src/pages/LoginPage.vue 的 `.auth-page-mobile` 分支：
 *
 *  - 顶部 CloudNext 品牌徽章 + "企业云盘" 副标题
 *  - 标题 "登录" + 描述 "使用 O2OA 账号进入企业云盘工作台。"
 *  - 账号输入框（账号 / 工号 / 邮箱）
 *  - 密码输入框（可切换可见性）
 *  - 主按钮 "登录"
 *  - 错误/成功 toast 行
 *  - 底部认证模式 + O2 配置 pill 行
 *
 * 不复制 Web 桌面端的左侧 hero（按 isMobile 分支裁掉）。
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val tokens = LocalCnTokens.current

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) onLoginSuccess()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.md),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LoginCard(
                state = state,
                onCredentialChange = viewModel::onCredentialChange,
                onPasswordChange = viewModel::onPasswordChange,
                onSubmit = viewModel::submit,
            )
        }
    }
}

@Composable
private fun LoginCard(
    state: LoginUiState,
    onCredentialChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val tokens = LocalCnTokens.current
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .padding(vertical = tokens.spacing.sm),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(tokens.spacing.xl)) {
            BrandHeader()
            Spacer(Modifier.height(tokens.spacing.md))

            Text("登录", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "使用 O2OA 账号进入企业云盘工作台。",
                style = MaterialTheme.typography.bodyMedium,
                color = CnPalette.TextSecondary,
            )

            Spacer(Modifier.height(tokens.spacing.lg))

            CredentialField(value = state.credential, onValueChange = onCredentialChange)
            Spacer(Modifier.height(tokens.spacing.md))
            PasswordField(
                value = state.password,
                onValueChange = onPasswordChange,
                onSubmit = onSubmit,
            )

            Spacer(Modifier.height(tokens.spacing.lg))

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tokens.touch.comfort),
                onClick = onSubmit,
                enabled = state.canSubmit,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                if (state.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("登录中…", style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text("登录", style = MaterialTheme.typography.bodyLarge)
                }
            }

            state.errorMessage?.let { msg ->
                Spacer(Modifier.height(tokens.spacing.md))
                Text(msg, color = CnPalette.Error, style = MaterialTheme.typography.bodyMedium)
            }
            state.successMessage?.let { msg ->
                Spacer(Modifier.height(tokens.spacing.md))
                Text(msg, color = CnPalette.Success, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(tokens.spacing.lg))
            StatusRow(authMode = state.authMode, o2Configured = state.o2Configured)
        }
    }
}

@Composable
private fun BrandHeader() {
    val tokens = LocalCnTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = CnPalette.PrimarySoft,
                    shape = MaterialTheme.shapes.small,
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                "CloudNext",
                color = CnPalette.Primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.size(10.dp))
        Text(
            "企业云盘",
            style = MaterialTheme.typography.bodyLarge,
            color = CnPalette.TextSecondary,
        )
    }
}

@Composable
private fun CredentialField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column {
        Text("账号", style = MaterialTheme.typography.labelMedium, color = CnPalette.TextSecondary)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("账号 / 工号 / 邮箱") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    Column {
        Text("密码", style = MaterialTheme.typography.labelMedium, color = CnPalette.TextSecondary)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("请输入密码") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (visible) "隐藏密码" else "显示密码",
                        tint = CnPalette.TextTertiary,
                    )
                }
            },
        )
    }
}

@Composable
private fun StatusRow(authMode: String, o2Configured: Boolean) {
    val tokens = LocalCnTokens.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
        StatusPill(label = "认证模式", value = authMode.ifBlank { "--" }, valueColor = CnPalette.TextPrimary)
        StatusPill(
            label = "O2 配置",
            value = if (o2Configured) "已连接" else "未配置",
            valueColor = if (o2Configured) CnPalette.Success else CnPalette.Warning,
        )
    }
}

@Composable
private fun StatusPill(
    label: String,
    value: String,
    valueColor: Color,
) {
    val tokens = LocalCnTokens.current
    Surface(
        // 显式 pill：shapes.extraLarge 已改回 28dp（Dialog 默认），这里不能跟。
        shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = tokens.spacing.md, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = CnPalette.TextSecondary)
            Spacer(Modifier.size(6.dp))
            Text(value, style = MaterialTheme.typography.labelMedium, color = valueColor)
        }
    }
}
