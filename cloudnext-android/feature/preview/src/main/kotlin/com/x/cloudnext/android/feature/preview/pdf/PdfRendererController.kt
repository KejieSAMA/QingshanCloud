package com.x.cloudnext.android.feature.preview.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.collection.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.Closeable

/**
 * PdfRenderer 包装器。
 *
 * 关键约束：
 * - PdfRenderer 非线程安全：所有 openPage / render 必须在串行 dispatcher（[singleDispatcher]）
 * - 内存：LruCache 限制 Bitmap 数量，避免大 PDF OOM
 * - 关闭：用 [close] 在 onDispose 释放，否则文件描述符泄漏
 *
 * 使用：
 * ```
 * val ctrl = remember(file) { PdfRendererController(file) }
 * DisposableEffect(ctrl) { onDispose { ctrl.close() } }
 * val pageCount = ctrl.pageCount
 * // 在 LazyColumn { items(pageCount) { idx -> ... ctrl.renderPage(idx, width) } }
 * ```
 */
class PdfRendererController(file: File) : Closeable {

    private val pfd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = PdfRenderer(pfd)

    val pageCount: Int = renderer.pageCount

    // 简化版 LruCache，容量为可用内存的 1/16（保守，避免大图爆内存）
    private val cache: LruCache<Int, Bitmap> = object : LruCache<Int, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 16).toInt().coerceAtLeast(2 * 1024),
    ) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
    }

    /**
     * 渲染指定页到给定宽度（像素）。返回 Bitmap（可能从 cache 取）。
     *
     * 必须在 [singleDispatcher] 上调用：
     * ```
     * withContext(PdfRendererController.singleDispatcher) { ctrl.renderPage(0, widthPx) }
     * ```
     */
    fun renderPage(index: Int, widthPx: Int): Bitmap? {
        if (index < 0 || index >= pageCount || widthPx <= 0) return null
        cache.get(index)?.let { return it }
        return runCatching {
            renderer.openPage(index).use { page ->
                val ratio = widthPx.toFloat() / page.width.toFloat()
                val heightPx = (page.height * ratio).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)  // PDF 透明区域不显示成黑色
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache.put(index, bmp)
                bmp
            }
        }.onFailure { Log.w(TAG, "render page $index failed", it) }.getOrNull()
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
        cache.evictAll()
    }

    companion object {
        private const val TAG = "PdfRendererController"

        /** PdfRenderer 非线程安全 → 全部 IO 操作串行 */
        val singleDispatcher = Dispatchers.IO.limitedParallelism(1)
    }
}

/**
 * 在串行 dispatcher 上 render，封装 withContext 调用。
 */
suspend fun PdfRendererController.renderPageAsync(index: Int, widthPx: Int): Bitmap? =
    withContext(PdfRendererController.singleDispatcher) {
        renderPage(index, widthPx)
    }
