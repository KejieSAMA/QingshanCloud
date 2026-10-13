package com.x.cloudnext.android.data.search

import com.x.cloudnext.android.data.drive.DriveItem

/**
 * 搜索分页结果。
 */
data class SearchPage(
    val items: List<DriveItem>,
    val page: Int,
    val size: Int,
    val total: Long,
    val totalPages: Int,
    val hasNext: Boolean,
)

/**
 * 文件类型筛选选项。对应后端 DriveSearchRequest.fileType。
 */
enum class SearchFileType(val value: String, val label: String) {
    All("", "全部"),
    Word("word", "Word"),
    Excel("excel", "Excel"),
    Ppt("ppt", "PPT"),
    Pdf("pdf", "PDF"),
    Image("image", "图片"),
    Video("video", "视频"),
    Archive("archive", "压缩"),
    Other("other", "其他"),
}
