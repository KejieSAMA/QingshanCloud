package com.x.cloudnext.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.designsystem.LocalCnTokens

/**
 * 新建文件夹弹窗。AlertDialog + OutlinedTextField，进入自动聚焦键盘。
 */
@Composable
fun NewFolderDialog(
    onConfirm: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    NamePromptDialog(
        title = "新建文件夹",
        placeholder = "文件夹名称",
        initial = "",
        confirmLabel = "创建",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * 重命名弹窗。预填当前名称。
 */
@Composable
fun RenameDialog(
    currentName: String,
    onConfirm: (newName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    NamePromptDialog(
        title = "重命名",
        placeholder = "新名称",
        initial = currentName,
        confirmLabel = "确定",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * 删除二次确认。注意文案"已移至回收站"——后端是软删。
 */
@Composable
fun DeleteConfirmDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除确认") },
        text = {
            Text(
                text = if (count <= 1) "确定要删除该项？删除后可在回收站恢复。"
                else "确定要删除选中的 $count 项？删除后可在回收站恢复。",
                color = CnPalette.TextSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = CnPalette.Error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun NamePromptDialog(
    title: String,
    placeholder: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalCnTokens.current
    var name by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    val trimmed = name.trim()
    val canSubmit = trimmed.isNotEmpty() &&
        trimmed.length <= 255 &&
        (initial.isEmpty() || trimmed != initial.trim())

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (canSubmit) onConfirm(trimmed)
                    }),
                )
                if (trimmed.length > 255) {
                    Spacer(modifier = Modifier.size(tokens.spacing.xs))
                    Text(
                        text = "名称不能超过 255 个字符",
                        color = CnPalette.Error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = canSubmit,
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
