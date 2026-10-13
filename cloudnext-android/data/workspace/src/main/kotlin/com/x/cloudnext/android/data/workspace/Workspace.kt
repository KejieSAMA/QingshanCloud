package com.x.cloudnext.android.data.workspace

/**
 * 工作空间角色。后端 enum 真值：OWNER / ADMIN / MEMBER / VIEWER。
 *
 * 客户端权限策略（M5b 简化）：
 * - OWNER：完整权限（CRUD + 成员管理 + 删除空间）
 * - ADMIN：可上传 / 改文件 / 改成员，不能删除空间
 * - MEMBER：可上传 / 改自己上传的文件
 * - VIEWER：只读
 *
 * UI 层用 [canManageMembers] / [canWrite] 等便利属性减少散落 if-else。
 */
enum class WorkspaceRole(val raw: String, val label: String) {
    Owner("OWNER", "所有者"),
    Admin("ADMIN", "管理员"),
    Member("MEMBER", "成员"),
    Viewer("VIEWER", "只读"),
    ;

    val canManageMembers: Boolean get() = this == Owner || this == Admin
    val canWrite: Boolean get() = this != Viewer
    val canDeleteWorkspace: Boolean get() = this == Owner

    companion object {
        fun parse(raw: String?): WorkspaceRole = when (raw?.uppercase()) {
            "OWNER" -> Owner
            "ADMIN" -> Admin
            "VIEWER" -> Viewer
            else -> Member
        }
    }
}

/**
 * 工作空间列表项（轻量）。
 */
data class WorkspaceSummary(
    val id: String,
    val name: String,
    val status: String?,
    val myRole: WorkspaceRole,
    val creatorName: String?,
    val updatedAt: String?,
    val memberCount: Long,
)

/**
 * 工作空间详情。带 rootItemId 供 detail 页直接列文件。
 */
data class WorkspaceDetail(
    val id: String,
    val name: String,
    val status: String?,
    val myRole: WorkspaceRole,
    val rootItemId: String?,
    val creatorPersonOaId: String?,
    val creatorName: String?,
    val memberCount: Long,
    val createdAt: String?,
    val updatedAt: String?,
)

/**
 * 成员领域模型。
 */
data class WorkspaceMember(
    val id: String,
    val workspaceId: String,
    val personOaId: String,
    val personName: String,
    val role: WorkspaceRole,
    val createdAt: String?,
    val updatedAt: String?,
)
