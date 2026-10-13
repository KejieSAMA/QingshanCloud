package com.x.cloudnext.android.network.model

import kotlinx.serialization.Serializable

/**
 * M5b 工作空间相关 DTOs。1:1 对应 cloudnext-server `com.x.cloudnext.server.workspace`：
 *   WorkspaceCreateRequest / WorkspaceFolderCreateRequest /
 *   WorkspaceMemberAddRequest / WorkspaceMemberUpdateRequest /
 *   WorkspaceSummaryResponse / WorkspaceDetailResponse / WorkspaceMemberResponse
 *
 * 角色：OWNER / ADMIN / MEMBER / VIEWER（见 com.x.cloudnext.server.drive.WorkspaceRole）
 */

@Serializable
data class WorkspaceCreateRequest(
    /** 1..100 字符。后端 service 做 NotBlank + 长度校验 */
    val name: String,
)

@Serializable
data class WorkspaceFolderCreateRequest(
    /** 空串 = 直接建在工作空间根下 */
    val parentId: String = "",
    val name: String,
)

@Serializable
data class WorkspaceMemberAddRequest(
    val personOaId: String,
    val personName: String = "",
    /** OWNER / ADMIN / MEMBER / VIEWER */
    val role: String,
)

@Serializable
data class WorkspaceMemberUpdateRequest(
    val role: String,
)

@Serializable
data class WorkspaceSummaryResponse(
    val id: String,
    val name: String? = null,
    val status: String? = null,
    /** 当前用户在该工作空间的角色：OWNER / ADMIN / MEMBER / VIEWER */
    val myRole: String? = null,
    val creatorName: String? = null,
    val updatedAt: String? = null,
    val memberCount: Long = 0L,
)

@Serializable
data class WorkspaceDetailResponse(
    val id: String,
    val name: String? = null,
    val status: String? = null,
    val myRole: String? = null,
    /** 工作空间根目录的 DriveItem.id；列文件时 parentId 为 null 后端会用 rootItemId */
    val rootItemId: String? = null,
    val creatorPersonOaId: String? = null,
    val creatorName: String? = null,
    val memberCount: Long = 0L,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class WorkspaceMemberResponse(
    val id: String,
    val workspaceId: String? = null,
    val personOaId: String? = null,
    val personName: String? = null,
    val role: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
