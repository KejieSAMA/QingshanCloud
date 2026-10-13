package com.x.cloudnext.android.feature.links

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.data.link.DrivePublicLink
import com.x.cloudnext.android.data.link.LinkStatus
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.network.BuildConfig
import com.x.cloudnext.android.ui.CnActionItem
import com.x.cloudnext.android.ui.CnActionRole
import com.x.cloudnext.android.ui.CnActionSheet
import com.x.cloudnext.android.ui.CnEmpty
import com.x.cloudnext.android.ui.CnError
import com.x.cloudnext.android.ui.CnLinkCard

/**
 * 外链管理面板。SharedHostScreen 的「我的外链」chip 内嵌。
 *
 * 后端 cloudnext.iteration.public-links.enabled=false 时显示 CnEmpty "未启用"。
 *
 * @param onOpenEdit 编辑链接：跳到 EditLinkScreen
 * @param onOpenAccessLogs 查看访问记录：跳到 AccessLogsScreen
 */
@Composable
fun LinksPanel(
    onOpenEdit: (linkId: String, fileName: String) -> Unit,
    onOpenAccessLogs: (linkId: String, fileName: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LinksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var sheetTarget by remember { mutableStateOf<DrivePublicLink?>(null) }
    var revokeConfirm by remember { mutableStateOf<DrivePublicLink?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LinksEvent.Toast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is LinksEvent.CopyToClipboard -> {
                    copyToClipboard(context, event.text)
                    Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    when {
        !state.featureEnabled -> CnEmpty(
            message = "外链功能未启用",
            hint = "请联系管理员在服务端配置 cloudnext.iteration.public-links.enabled=true",
            modifier = modifier,
        )
        state.links.isEmpty() && state.errorMessage != null ->
            CnError(message = state.errorMessage!!, onRetry = { viewModel.refresh() }, modifier = modifier)
        state.links.isEmpty() && !state.loading -> CnEmpty(
            message = "暂无外链",
            hint = "在文件详情中创建外链以分享给外部",
            modifier = modifier,
        )
        else -> Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.links, key = { it.id }) { link ->
                    CnLinkCard(
                        fileName = link.fileName ?: "未命名文件",
                        statusLabel = statusLabel(link),
                        statusColorKey = statusColorKey(link.status),
                        permissionLabel = link.permission.label,
                        requirePassword = link.requirePassword,
                        downloadLimit = link.downloadLimit,
                        downloadCount = link.downloadCount,
                        expiresLabel = link.expiresAt?.let { "至 $it" },
                        onCopy = { viewModel.copyShareUrl(link, BuildConfig.API_BASE_URL) },
                        onMore = { sheetTarget = link },
                    )
                    HorizontalDivider(color = CnPalette.DividerHairline)
                }
            }
        }
    }

    sheetTarget?.let { link ->
        CnActionSheet(
            title = link.fileName,
            actions = listOf(
                CnActionItem(Icons.Outlined.Edit, "修改"),
                CnActionItem(Icons.Outlined.Analytics, "访问记录"),
                CnActionItem(Icons.Outlined.LinkOff, "撤销外链", role = CnActionRole.Destructive),
            ),
            onActionClick = { index ->
                when (index) {
                    0 -> {
                        sheetTarget = null
                        onOpenEdit(link.id, link.fileName ?: "")
                    }
                    1 -> {
                        sheetTarget = null
                        onOpenAccessLogs(link.id, link.fileName ?: "")
                    }
                    2 -> {
                        sheetTarget = null
                        revokeConfirm = link
                    }
                }
            },
            onDismiss = { sheetTarget = null },
        )
    }

    revokeConfirm?.let { link ->
        AlertDialog(
            onDismissRequest = { revokeConfirm = null },
            title = { Text("撤销外链") },
            text = {
                Text(
                    text = "确定要撤销「${link.fileName ?: ""}」的外链？已发出的链接将立即失效。",
                    color = CnPalette.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = link.id
                    revokeConfirm = null
                    viewModel.revoke(id)
                }) {
                    Text("撤销", color = CnPalette.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { revokeConfirm = null }) { Text("取消") }
            },
        )
    }
}

private fun statusLabel(link: DrivePublicLink): String = link.status.label

private fun statusColorKey(status: LinkStatus): String = when (status) {
    LinkStatus.Active -> "active"
    LinkStatus.Expired -> "expired"
    LinkStatus.Consumed -> "consumed"
    LinkStatus.Revoked -> "revoked"
}

private fun copyToClipboard(context: Context, text: String) {
    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clip.setPrimaryClip(ClipData.newPlainText("cloudnext.share.url", text))
}
