package com.x.cloudnext.android.data.share

import com.x.cloudnext.android.data.drive.DriveItem

/**
 * 分享目标（人 / 部门）。OrgPicker 选完后回传，CreateShareSheet 用。
 */
data class ShareTarget(
    val type: TargetType,
    val id: String,
    val name: String,
) {
    enum class TargetType(val raw: String) {
        Person("person"),
        Department("department"),
        ;

        companion object {
            fun parse(raw: String?): TargetType = when (raw?.lowercase()) {
                "department", "dept" -> Department
                else -> Person
            }
        }
    }
}

/**
 * 分享权限。后端 DriveSharePermission 真值 read / edit。
 */
enum class SharePermission(val raw: String, val label: String) {
    Read("read", "只读"),
    Edit("edit", "可编辑"),
    ;

    companion object {
        fun parse(raw: String?): SharePermission = when (raw?.lowercase()) {
            "edit", "write" -> Edit
            else -> Read
        }
    }
}

/**
 * 分享状态。
 *
 * 后端 status 字段取值：active / expired / revoked / consumed
 */
enum class ShareStatus(val raw: String, val label: String) {
    Active("active", "有效"),
    Expired("expired", "已过期"),
    Revoked("revoked", "已撤销"),
    Consumed("consumed", "已用完"),
    Unknown("", "未知"),
    ;

    companion object {
        fun parse(raw: String?): ShareStatus = when (raw?.lowercase()) {
            "active" -> Active
            "expired" -> Expired
            "revoked" -> Revoked
            "consumed" -> Consumed
            else -> Unknown
        }
    }
}

/**
 * 分享领域模型。incoming（收到的）和 outgoing（我发出的）共用同一类型。
 *
 * 后端 DriveShareResponse 17 字段映射 → 这里精简成 UI 真正用得到的部分。
 */
data class DriveShare(
    val id: String,
    /** 嵌套 DriveItem（被分享的文件） */
    val item: DriveItem?,
    /** 分享者（incoming 时是别人，outgoing 时是自己） */
    val ownerPersonOaId: String?,
    val ownerName: String?,
    /** 目标（接收方） */
    val target: ShareTarget?,
    val permission: SharePermission,
    val expiresAt: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val status: ShareStatus,
    /** incoming 用：实际生效权限（可能小于 [permission]） */
    val effectivePermission: SharePermission?,
    val allowPreview: Boolean,
    val allowDownload: Boolean,
)

/**
 * 分享分页结果。
 */
data class SharePage(
    val items: List<DriveShare>,
    val page: Int,
    val size: Int,
    val total: Long,
    val totalPages: Int,
    val hasNext: Boolean,
)
