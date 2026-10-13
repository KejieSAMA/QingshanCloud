package com.x.cloudnext.android.feature.preview.text

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.x.cloudnext.android.network.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

sealed interface TextUiState {
    data object Loading : TextUiState
    data class Ready(
        val content: String,
        val truncated: Boolean,
        val sizeBytes: Long,
    ) : TextUiState
    data class Failed(val message: String) : TextUiState
}

/**
 * 纯文本类预览 ViewModel。直接 OkHttp 拉 /api/drive/items/{id}/content，
 * 读 byte 流 → UTF-8 解码（失败 fallback GBK，覆盖中文 Windows 文件场景）。
 *
 * 1MB 截断防 OOM：超出只读前 1MB + 设 truncated=true 让 UI 显示警告。
 *
 * 不缓存到本地（与 PdfPreview 不同）：文本量小，每次重进直接拉，避免缓存失效问题。
 */
@HiltViewModel
class TextPreviewViewModel @Inject constructor(
    private val okHttpClient: OkHttpClient,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId: String = savedStateHandle.get<String>(ARG_ITEM_ID).orEmpty()

    private val _state = MutableStateFlow<TextUiState>(TextUiState.Loading)
    val state: StateFlow<TextUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        _state.value = TextUiState.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { fetchAndDecode() }
            }
            _state.value = result.fold(
                onSuccess = { it },
                onFailure = { TextUiState.Failed(it.message ?: "文本加载失败") },
            )
        }
    }

    private fun fetchAndDecode(): TextUiState.Ready {
        val url = BuildConfig.API_BASE_URL.trimEnd('/') + "/api/drive/items/" + itemId + "/content"
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("响应体为空")
            val totalBytes = body.contentLength().takeIf { it >= 0 } ?: -1L
            // 截断：只读前 MAX_TEXT_BYTES 个字节
            val bytes = body.source().use { src ->
                val limit = MAX_TEXT_BYTES.toInt()
                val buf = ByteArray(limit + 1)
                var off = 0
                while (off < buf.size) {
                    val n = src.read(buf, off, buf.size - off)
                    if (n <= 0) break
                    off += n
                }
                if (off > limit) buf.copyOf(limit) else buf.copyOf(off)
            }
            val truncated = totalBytes > MAX_TEXT_BYTES || bytes.size >= MAX_TEXT_BYTES
            // UTF-8 优先；带不可解码字节序列时 fallback GBK
            val text = decodePreferUtf8(bytes)
            return TextUiState.Ready(
                content = text,
                truncated = truncated,
                sizeBytes = if (totalBytes >= 0) totalBytes else bytes.size.toLong(),
            )
        }
    }

    /**
     * UTF-8 严格解码；任何字节解码失败 fallback GBK。
     * 简单可靠，不引重型字符集探测库。
     */
    private fun decodePreferUtf8(bytes: ByteArray): String {
        return runCatching {
            val decoder = Charsets.UTF_8.newDecoder().apply {
                onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            }
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }.getOrElse {
            // GBK fallback；如果连 GBK 也失败（极少见），用 ISO-8859-1 兜底（不会抛）
            runCatching { String(bytes, charset("GBK")) }
                .getOrElse { String(bytes, Charsets.ISO_8859_1) }
        }
    }

    fun retry() = load()

    companion object {
        const val ARG_ITEM_ID = "itemId"
        // 1MB 字节流。UTF-16 String 约占 2MB，加 Compose 渲染峰值 ~5MB，对中端机安全。
        private const val MAX_TEXT_BYTES = 1L * 1024 * 1024
    }
}
