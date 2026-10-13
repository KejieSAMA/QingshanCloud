package com.x.cloudnext.android.data.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * 上传 / 下载通用通知构建器。
 * - [buildProgress]：进行中通知（进度条 + setOngoing）
 * - [buildDone]：完成通知（点击 ACTION_VIEW 打开 outputUri，下载场景用）
 * - [buildFailed]：失败通知（点击打开 TransferCenter 看详情）
 *
 * 通道 ID `cloudnext.transfer`，重要性 LOW（无声）。
 */
object TransferNotification {
    const val CHANNEL_ID = "cloudnext.transfer"
    private const val CHANNEL_NAME = "传输任务"
    private const val CHANNEL_DESC = "上传和下载进度通知"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                description = CHANNEL_DESC
                setShowBadge(false)
            },
        )
    }

    fun buildProgress(
        context: Context,
        task: TransferTask,
        smallIconRes: Int,
    ): Notification {
        val progress = (task.progressFraction * 100).toInt().coerceIn(0, 100)
        val verb = when (task.type) {
            TransferType.UPLOAD -> "正在上传"
            TransferType.DOWNLOAD -> "正在下载"
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setContentTitle("$verb ${task.displayName}")
            .setContentText("$progress%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, progress, task.totalBytes <= 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun buildDone(
        context: Context,
        task: TransferTask,
        smallIconRes: Int,
        outputUri: Uri? = null,
    ): Notification {
        val title = when (task.type) {
            TransferType.UPLOAD -> "上传完成"
            TransferType.DOWNLOAD -> "下载完成"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setContentTitle(title)
            .setContentText(task.displayName)
            .setOngoing(false)
            .setAutoCancel(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // 下载完成 → ACTION_VIEW outputUri，让系统选 App 打开
        if (task.type == TransferType.DOWNLOAD && outputUri != null) {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(outputUri, task.contentTypeOrFallback())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val pending = runCatching {
                PendingIntent.getActivity(context, task.id.hashCode(), viewIntent, flags)
            }.getOrNull()
            if (pending != null) builder.setContentIntent(pending)
        }
        return builder.build()
    }

    private fun TransferTask.contentTypeOrFallback(): String {
        val ext = displayName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "doc", "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls", "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt", "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "zip" -> "application/zip"
            "txt", "md", "log" -> "text/plain"
            else -> "*/*"
        }
    }
}
