package com.x.cloudnext.android.data.transfer.download

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 下载落盘到 `Downloads/CloudNext/`，用 MediaStore（API 29+）。
 *
 * 流程：
 * 1. [createPendingUri] 创建带 IS_PENDING=1 的 row + URI
 * 2. [openOutputStream] 拿 stream 写入
 * 3. 写完调 [finish] 清 IS_PENDING（其它 App 才能看到）
 * 4. 中途失败调 [delete] 回滚（删 row + 文件）
 *
 * minSdk=24，API 24-28 用 [isSupported] 拒绝；M3b 不做向后兼容（市场份额 < 1%）。
 */
@Singleton
class MediaStoreWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** API 29+ 创建 pending row 并返回内容 URI。 */
    fun createPendingUri(displayName: String, mimeType: String?): Uri {
        require(isSupported) { "MediaStore Downloads requires API 29+" }
        val resolver: ContentResolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            mimeType?.takeIf { it.isNotBlank() }?.let {
                put(MediaStore.Downloads.MIME_TYPE, it)
            }
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/CloudNext")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        return resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert failed for $displayName")
    }

    fun openOutputStream(uri: Uri): OutputStream =
        context.contentResolver.openOutputStream(uri)
            ?: error("openOutputStream returned null for $uri")

    /** 完成后清 IS_PENDING，其它 App 可见。 */
    fun finish(uri: Uri) {
        if (!isSupported) return
        val values = ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }
        context.contentResolver.update(uri, values, null, null)
    }

    /** 删除 row + 实际文件。 */
    fun delete(uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    /** 查询实际写入后的 DISPLAY_NAME（系统冲突时可能加 (1) 后缀）。 */
    fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Downloads.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
            }
        }.getOrNull()
    }
}
