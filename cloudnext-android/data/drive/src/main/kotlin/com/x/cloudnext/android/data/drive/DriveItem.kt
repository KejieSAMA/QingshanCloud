package com.x.cloudnext.android.data.drive

/**
 * 领域模型。和 Network DTO / Room Entity 解耦，UI/ViewModel 只依赖这个类型。
 */
data class DriveItem(
    val id: String,
    val parentId: String?,
    val name: String,
    val itemType: ItemType,
    /** "folder" / "word" / "excel" / "ppt" / "pdf" / "video" / "image" / "audio" / "archive" / "code" / "text" / "file" */
    val fileType: String?,
    val sizeBytes: Long?,
    val updatedAt: String?,
    val lastAccessedAt: String?,
) {
    val isFolder: Boolean get() = itemType == ItemType.FOLDER

    enum class ItemType { FOLDER, FILE }
}

data class DrivePathSegment(
    val id: String,
    val name: String,
)
