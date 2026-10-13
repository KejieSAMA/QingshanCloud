package com.x.cloudnext.android.data.share

import com.x.cloudnext.android.data.drive.toDomain
import com.x.cloudnext.android.network.model.DriveCreateShareRequest
import com.x.cloudnext.android.network.model.DriveShareResponse
import com.x.cloudnext.android.network.model.DriveShareTargetRequest
import com.x.cloudnext.android.network.model.DriveUpdateShareRequest

internal fun DriveShareResponse.toDomain(): DriveShare = DriveShare(
    id = id,
    item = item?.toDomain(),
    ownerPersonOaId = ownerPersonOaId,
    ownerName = ownerName,
    target = if (!targetId.isNullOrBlank()) {
        ShareTarget(
            type = ShareTarget.TargetType.parse(targetType),
            id = targetId.orEmpty(),
            name = targetName.orEmpty(),
        )
    } else null,
    permission = SharePermission.parse(permission),
    expiresAt = expiresAt?.takeIf { it.isNotBlank() },
    createdAt = createdAt,
    updatedAt = updatedAt,
    status = ShareStatus.parse(status),
    effectivePermission = effectivePermission?.let { SharePermission.parse(it) },
    allowPreview = allowPreview ?: true,
    allowDownload = allowDownload ?: false,
)

internal fun ShareTarget.toRequest(): DriveShareTargetRequest = DriveShareTargetRequest(
    type = type.raw,
    id = id,
    name = name,
)

internal fun buildCreateRequest(
    itemIds: List<String>,
    targets: List<ShareTarget>,
    permission: SharePermission,
    expiresAt: String?,
    canReshare: Boolean,
): DriveCreateShareRequest = DriveCreateShareRequest(
    // 单文件时也填 itemIds 列表，后端兼容
    itemId = itemIds.firstOrNull().orEmpty(),
    itemIds = itemIds,
    targets = targets.map { it.toRequest() },
    permission = permission.raw,
    expiresAt = expiresAt.orEmpty(),
    canReshare = canReshare,
)

internal fun buildUpdateRequest(
    permission: SharePermission?,
    expiresAt: String?,
    canReshare: Boolean?,
): DriveUpdateShareRequest = DriveUpdateShareRequest(
    permission = permission?.raw.orEmpty(),
    expiresAt = expiresAt.orEmpty(),
    canReshare = canReshare,
)
