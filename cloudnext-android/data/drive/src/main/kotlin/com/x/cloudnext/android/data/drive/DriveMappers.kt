package com.x.cloudnext.android.data.drive

import com.x.cloudnext.android.database.entity.DriveCacheEntity
import com.x.cloudnext.android.network.model.DriveBatchOperationResponse
import com.x.cloudnext.android.network.model.DriveItemResponse
import com.x.cloudnext.android.network.model.DrivePathNodeResponse

fun DriveItemResponse.toDomain(): DriveItem = DriveItem(
    id = id,
    parentId = parentId,
    name = name,
    itemType = parseItemType(itemType),
    fileType = fileType,
    sizeBytes = sizeBytes,
    updatedAt = updatedAt,
    lastAccessedAt = lastAccessedAt,
)

internal fun DriveItemResponse.toEntity(parentIdKey: String, syncAt: Long): DriveCacheEntity =
    DriveCacheEntity(
        id = id,
        parentId = parentIdKey,
        name = name,
        itemType = itemType,
        fileType = fileType,
        contentType = contentType,
        sizeBytes = sizeBytes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastAccessedAt = lastAccessedAt,
        lastSyncAt = syncAt,
    )

internal fun DriveCacheEntity.toDomain(): DriveItem = DriveItem(
    id = id,
    parentId = parentId.takeIf { it.isNotEmpty() },
    name = name,
    itemType = parseItemType(itemType),
    fileType = fileType,
    sizeBytes = sizeBytes,
    updatedAt = updatedAt,
    lastAccessedAt = lastAccessedAt,
)

fun DrivePathNodeResponse.toDomain(): DrivePathSegment =
    DrivePathSegment(id = id, name = name)

fun DriveBatchOperationResponse.toDomain(): BatchOperationResult = BatchOperationResult(
    totalRequested = totalRequested,
    totalSucceeded = totalSucceeded,
    totalFailed = totalFailed,
    succeededIds = succeededIds,
    failedItems = failedItems,
    message = message,
)

private fun parseItemType(raw: String?): DriveItem.ItemType = when (raw?.uppercase()) {
    "FOLDER" -> DriveItem.ItemType.FOLDER
    else -> DriveItem.ItemType.FILE
}

/** Room 用空串表示根目录，避免可空 PK 索引问题。 */
internal const val ROOT_PARENT_KEY: String = ""

internal fun parentIdKey(parentId: String?): String = parentId ?: ROOT_PARENT_KEY
