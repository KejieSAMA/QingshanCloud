package com.x.cloudnext.android.data.trash

import com.x.cloudnext.android.data.drive.DriveItem

/**
 * 回收站分页结果。
 *
 * @param items 当前页项目（DriveItem 领域模型；deletedAt / originalParentId 当前后端不在 DTO 中暴露，
 *              视觉上只能用 [DriveItem.updatedAt] 兼用为"删除时间"）
 * @param page 当前页号（1-起）
 * @param size 单页 size
 * @param total 总条数
 * @param hasNext 是否还有下一页
 */
data class TrashPage(
    val items: List<DriveItem>,
    val page: Int,
    val size: Int,
    val total: Long,
    val totalPages: Int,
    val hasNext: Boolean,
)

/**
 * 回收站三筛选项。前端用 chip 切换。
 */
enum class TrashFilter(val itemType: String) {
    All(""),
    Folders("folder"),
    Files("file"),
}
