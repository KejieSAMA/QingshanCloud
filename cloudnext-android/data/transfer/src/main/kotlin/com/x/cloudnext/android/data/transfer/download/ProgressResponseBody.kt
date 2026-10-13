package com.x.cloudnext.android.data.transfer.download

import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer

/**
 * OkHttp ResponseBody 包装，下载时按字节回调进度。
 *
 * 镜像于 [com.x.cloudnext.android.data.transfer.upload.ProgressRequestBody]：
 * - 用 okio.ForwardingSource 透传读取
 * - 每读取一次回调 [onProgress]
 * - contentLength 来自原 body（chunked 时为 -1）
 *
 * caller 用 [source().buffer()] 流式读，**绝不** body.bytes() / body.string()。
 */
internal class ProgressResponseBody(
    private val delegate: ResponseBody,
    private val onProgress: (transferred: Long, total: Long) -> Unit,
) : ResponseBody() {

    private val bufferedSource: BufferedSource by lazy { source(delegate.source()).buffer() }

    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()
    override fun source(): BufferedSource = bufferedSource

    private fun source(delegateSource: Source): Source {
        val total = delegate.contentLength()
        return object : ForwardingSource(delegateSource) {
            private var totalRead = 0L
            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = super.read(sink, byteCount)
                if (read != -1L) {
                    totalRead += read
                    onProgress(totalRead, total)
                }
                return read
            }
        }
    }
}
