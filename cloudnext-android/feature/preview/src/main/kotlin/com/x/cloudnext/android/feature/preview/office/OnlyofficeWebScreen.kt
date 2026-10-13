package com.x.cloudnext.android.feature.preview.office

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.x.cloudnext.android.designsystem.CnPalette
import com.x.cloudnext.android.ui.CnError
import com.x.cloudnext.android.ui.CnLoading
import com.x.cloudnext.android.ui.CnTopBar

/**
 * OnlyOffice 预览 / 编辑 WebView。
 *
 * URL 是 H5 路径 `<webBase>/preview/office?ticket=XXX`，H5 页面会用 axios 调
 * `/api/onlyoffice/view-config` 拿 docserviceApi。但 WebView 默认无 token cookie /
 * localStorage → axios 拦截器 401 → 后续 OnlyOffice DocsAPI 脚本不加载 → 红字
 * "Failed to load the OnlyOffice script"。
 *
 * 修复（4 项）：
 *   1. **Cookie 注入**：loadUrl 前用 CookieManager 把 token 写到多个候选 key
 *      （x-token / token / Authorization / x-cloudnext-token），http+https schema 都写
 *   2. **localStorage 注入**：onPageStarted 用 evaluateJavascript 写 localStorage（同 4 个 key）
 *   3. **混合内容放开**：mixedContentMode = MIXED_CONTENT_ALWAYS_ALLOW —— 允许 HTTPS H5 加载
 *      HTTP OnlyOffice api.js。**仅本 Screen 用，不污染其它 WebView**。
 *   4. **诊断日志**：WebChromeClient.onConsoleMessage 把 H5 内部错误打 logcat（tag
 *      `OnlyOfficeJS`）；onReceivedError 主资源失败打 tag `OnlyOffice`。下次 debug 直接看 logcat。
 *
 * 安全约束保留：
 *   - 不 addJavascriptInterface
 *   - 主请求带 X-CloudNext-Token；子请求由后端 previewTicket 或注入的 cookie 验证
 *   - 退出 destroy WebView 防内存泄漏
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OnlyofficeWebScreen(
    name: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnlyofficeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // BackHandler 只在 WebView 还活着时启用。composition 离开后 webViewRef = null，
    // BackHandler 自动 disabled，避免 predictive back 二次触发导致根栈被多 pop（白屏根因）。
    BackHandler(enabled = webViewRef != null) {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            webViewRef = null
        }
    }

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
            OfficeUiState.Loading -> CnLoading()
            is OfficeUiState.Failed -> CnError(message = s.message, onRetry = { viewModel.retry() })
            is OfficeUiState.Ready -> AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val token = s.token?.takeIf { it.isNotBlank() }

                    // (0) 开启 Chrome DevTools 远程调试。电脑 Chrome 访问 chrome://inspect/#devices
                    //     即可看到本 WebView，点 inspect 进入完整 Network / Console / DOM 调试。
                    //     仅 debug 构建启用，release 自动关闭，无安全风险。
                    if (com.x.cloudnext.android.network.BuildConfig.DEBUG) {
                        WebView.setWebContentsDebuggingEnabled(true)
                    }

                    // (1) Cookie 注入：loadUrl 前。多个候选 key 都写（http+https schema 都设）
                    token?.let { t ->
                        val host = Uri.parse(s.url).host
                        if (!host.isNullOrBlank()) {
                            val cm = CookieManager.getInstance().apply { setAcceptCookie(true) }
                            COOKIE_TOKEN_KEYS.forEach { key ->
                                cm.setCookie("https://$host", "$key=$t; Path=/")
                                cm.setCookie("http://$host", "$key=$t; Path=/")
                            }
                            cm.flush()
                        }
                    }

                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            allowFileAccess = false
                            allowContentAccess = false
                            // (3) 混合内容放开：H5 HTTPS 加载 HTTP OnlyOffice api.js 必需
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            // UA 加移动端标识（H5 可能根据 UA 选移动版预览路径）
                            userAgentString = "$userAgentString CloudNextAndroid"
                        }
                        // OnlyOffice 跨域时需要第三方 cookie
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                // (2) localStorage 注入：最早时机，让 H5 axios 拦截器能取到 token
                                token?.let { t ->
                                    val js = buildString {
                                        append("try {")
                                        LOCAL_STORAGE_TOKEN_KEYS.forEach { key ->
                                            // 单引号包裹 token；token 来自 backend，假设无 ' 字符
                                            append("window.localStorage.setItem('$key', '$t');")
                                        }
                                        append("window.localStorage.setItem('Authorization', 'Bearer $t');")
                                        append("} catch(e) { console.error('CloudNext token inject failed', e); }")
                                    }
                                    view?.evaluateJavascript(js, null)
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    Log.w(
                                        TAG_NATIVE,
                                        "Main frame error: ${error?.description} url=${request.url}",
                                    )
                                }
                            }
                        }

                        // (4) 诊断日志：H5 内部 console.error / fetch 失败都打到 logcat
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                                if (msg != null) {
                                    Log.d(
                                        TAG_JS,
                                        "${msg.messageLevel()}: ${msg.message()} @ ${msg.sourceId()}:${msg.lineNumber()}",
                                    )
                                }
                                return true
                            }
                        }

                        webViewRef = this
                        val headers = mutableMapOf<String, String>()
                        token?.let { headers["X-CloudNext-Token"] = it }
                        loadUrl(s.url, headers)
                    }
                },
            )
        }
    }
}

/** Cookie & localStorage 候选 key 列表。命中一个即可生效。 */
private val COOKIE_TOKEN_KEYS = listOf("x-token", "token", "Authorization", "x-cloudnext-token")
private val LOCAL_STORAGE_TOKEN_KEYS = listOf("x-token", "token", "x-cloudnext-token")

private const val TAG_NATIVE = "OnlyOffice"
private const val TAG_JS = "OnlyOfficeJS"
