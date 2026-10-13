package com.x.cloudnext.android.common

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 把 ISO-8601 时间字符串格式化为人类可读相对时间，对齐 cloudnext-web 的 formatTime：
 *   < 1 分钟  → 刚刚
 *   < 1 小时  → N 分钟前
 *   < 1 天    → N 小时前
 *   < 7 天    → N 天前
 *   今年内   → MM-dd
 *   其他     → yyyy-MM-dd
 */
fun formatRelativeTime(
    iso: String?,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    if (iso.isNullOrBlank()) return ""
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val diff = now - instant
    return when {
        diff.inWholeSeconds < 60 -> "刚刚"
        diff.inWholeMinutes < 60 -> "${diff.inWholeMinutes} 分钟前"
        diff.inWholeHours < 24 -> "${diff.inWholeHours} 小时前"
        diff.inWholeDays < 7 -> "${diff.inWholeDays} 天前"
        else -> {
            val local = instant.toLocalDateTime(timeZone)
            val nowLocal = now.toLocalDateTime(timeZone)
            if (local.year == nowLocal.year) {
                "%02d-%02d".format(local.monthNumber, local.dayOfMonth)
            } else {
                "%04d-%02d-%02d".format(local.year, local.monthNumber, local.dayOfMonth)
            }
        }
    }
}

fun formatDateTime(
    iso: String?,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    if (iso.isNullOrBlank()) return ""
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val dt: LocalDateTime = instant.toLocalDateTime(timeZone)
    return "%04d-%02d-%02d %02d:%02d".format(
        dt.year, dt.monthNumber, dt.dayOfMonth, dt.hour, dt.minute,
    )
}
