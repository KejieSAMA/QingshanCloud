package com.x.cloudnext.android.network.model

import kotlinx.serialization.Serializable

/**
 * 文件/文件夹项目，对应 cloudnext-server DriveItemResponse。
 * 后端会把所有字段以 null 形式落空，故全部 nullable + default。
 */
@Serializable
data class DriveItemResponse(
    val id: String,
    val parentId: String? = null,
    val parentName: String? = null,
    val ownerPersonOaId: String? = null,
    val ownerName: String? = null,
    val name: String,
    /** "FOLDER" | "FILE" */
    val itemType: String,
    /** "folder" | "word" | "excel" | ... 用于客户端图标匹配 */
    val fileType: String? = null,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val deleted: Boolean? = null,
    val deletedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lastAccessedAt: String? = null,
)

/**
 * 路径段，对应 cloudnext-server DrivePathNodeResponse。
 */
@Serializable
data class DrivePathNodeResponse(
    val id: String,
    val name: String,
)

// ============================================================
// M2b: CRUD requests / responses
// ============================================================

/**
 * 创建文件夹。后端约束：name @NotBlank @Size(max=255)，parentId @Size(max=64)。
 * 根目录建文件夹时 parentId 传空串 ""（后端 default）。
 */
@Serializable
data class DriveCreateFolderRequest(
    val name: String,
    val parentId: String = "",
)

/**
 * 重命名。后端约束：name @NotBlank @Size(max=255)。
 */
@Serializable
data class DriveRenameRequest(
    val name: String,
)

/**
 * 批量移动。conflictStrategy 取值与上传一致：abort / replace / skip / rename，后端默认 ABORT。
 */
@Serializable
data class DriveMoveRequest(
    val itemIds: List<String>,
    val targetParentId: String = "",
    val conflictStrategy: String = "abort",
)

/**
 * 批量操作通用响应。失败明细在 failedItems：itemId → reason。
 */
@Serializable
data class DriveBatchOperationResponse(
    val message: String? = null,
    val totalRequested: Int = 0,
    val totalSucceeded: Int = 0,
    val totalFailed: Int = 0,
    val succeededIds: List<String> = emptyList(),
    val failedItems: Map<String, String> = emptyMap(),
)

/**
 * 批量操作请求（如 Trash 恢复/彻底删除、Share 批量撤销）。后端 DriveBatchIdsRequest。
 */
@Serializable
data class DriveBatchIdsRequest(
    val itemIds: List<String> = emptyList(),
)

/**
 * 通用分页响应（Trash 查询、Search 文件搜索复用）。对应 cloudnext-server DrivePageResponse。
 */
@Serializable
data class DrivePageResponse(
    val items: List<DriveItemResponse> = emptyList(),
    val page: Int = 1,
    val size: Int = 20,
    val total: Long = 0L,
    val totalPages: Int = 0,
    val hasNext: Boolean = false,
)

// ============================================================
// M4: Preview
// ============================================================

/**
 * 预览响应，对应 cloudnext-server DrivePreviewResponse（12 字段）。
 *
 * kind 取值：image / pdf / office / unsupported
 * - image / pdf：用 previewUrl（通常是 /api/drive/items/{id}/content）
 * - office：用 previewPageUrl（OnlyOffice WebView）+ previewTicket
 * - unsupported：客户端 fallback；视频实际归 unsupported，客户端按 fileType=video 绕开此接口直接喂 /content 给 ExoPlayer
 */
@Serializable
data class DrivePreviewResponse(
    val itemId: String,
    val kind: String? = null,
    val supported: Boolean = false,
    val mode: String? = null,
    val fileName: String? = null,
    val contentType: String? = null,
    val previewUrl: String? = null,
    val previewTicket: String? = null,
    val previewPageUrl: String? = null,
    val allowDownload: Boolean? = null,
    val unavailableCode: String? = null,
    val unavailableMessage: String? = null,
)
