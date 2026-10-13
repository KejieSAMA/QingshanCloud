package com.x.cloudnext.android.data.transfer.upload

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.InputStream

/**
 * OkHttp RequestBody 包装，按 8KB 分块从 [streamProvider] 读取并写入网络，
 * 每写一定字节回调 [onProgress] 报告进度。
 *
 * 关键点：
 * - 用 Okio source().buffer() 流式拷贝，**绝不** 一次性把整文件读到内存
 * - contentLength() 必须返回真实大小（multipart 校验依赖）；-1 表示未知会被 OkHttp 用 chunked
 * - writeTo 可能被 OkHttp 重试调用多次 → streamProvider 每次必须返回新的 InputStream
 *
 * @param streamProvider 每次调用返回新的 InputStream（用 ContentResolver.openInputStream 或 file.inputStream()）
 * @param length 文件大小（字节），-1 表示未知
 * @param contentType MIME type
 * @param onProgress 回调（已传 bytes，总 bytes） — 在网络线程，调用方需自己切线程更新 UI
 */
internal class ProgressRequestBody(
    private val streamProvider: () -> InputStream,
    private val length: Long,
    private val contentType: MediaType?,
    private val onProgress: (transferred: Long, total: Long) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = contentType
    override fun contentLength(): Long = length

    override fun writeTo(sink: BufferedSink) {
        streamProvider().use { input ->
            input.source().use { source ->
                var transferred = 0L
                val buffer = okio.Buffer()
                while (true) {
                    val read = source.read(buffer, BUFFER_SIZE)
                    if (read == -1L) break
                    sink.write(buffer, read)
                    transferred += read
                    onProgress(transferred, length)
                }
            }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 8L * 1024L
    }
}
