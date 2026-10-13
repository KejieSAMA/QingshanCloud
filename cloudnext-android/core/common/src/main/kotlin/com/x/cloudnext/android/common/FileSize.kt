package com.x.cloudnext.android.common

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/**
 * 把字节数格式化成可读字符串，与 cloudnext-web 的 formatFileSize 对齐：
 * 1024 进制 + 一位小数 + 中文单位（如有）/ 英文单位（默认）。
 *
 * 1023 → "1023 B"
 * 1024 → "1.0 KB"
 * 1048576 → "1.0 MB"
 */
fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val unit = 1024.0
    val exp = (ln(abs(bytes.toDouble())) / ln(unit)).toInt().coerceAtMost(5)
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    val value = bytes / unit.pow(exp.toDouble())
    return "%.1f %s".format(value, units[exp])
}
