package com.x.cloudnext.android.data.preview

import com.x.cloudnext.android.network.model.DrivePreviewResponse

enum class PreviewKind { Image, Pdf, Office, Video, Unsupported }

/**
 * 预览领域模型。从 DrivePreviewResponse 转换：
 * - kind=image → Image
 * - kind=pdf → Pdf
 * - kind=office → Office
 * - kind=unsupported（含视频）→ Unsupported；客户端按 fileType 自行判断是否走 Video 分支
 */
data class DrivePreview(
    val itemId: String,
    val kind: PreviewKind,
    val supported: Boolean,
    val fileName: String?,
    val contentType: String?,
    val previewUrl: String?,
    val previewPageUrl: String?,
    val allowDownload: Boolean,
    val unavailableMessage: String?,
)

internal fun DrivePreviewResponse.toDomain(): DrivePreview = DrivePreview(
    itemId = itemId,
    kind = when (kind?.lowercase()) {
        "image" -> PreviewKind.Image
        "pdf" -> PreviewKind.Pdf
        "office" -> PreviewKind.Office
        else -> PreviewKind.Unsupported
    },
    supported = supported,
    fileName = fileName,
    contentType = contentType,
    previewUrl = previewUrl,
    previewPageUrl = previewPageUrl,
    allowDownload = allowDownload ?: false,
    unavailableMessage = unavailableMessage,
)
