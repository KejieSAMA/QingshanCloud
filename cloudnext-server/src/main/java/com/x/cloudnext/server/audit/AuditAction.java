package com.x.cloudnext.server.audit;

/**
 * 审计动作枚举 — 与 AuditLog 一起记录用户对系统的关键写操作。
 *
 * N+1 阶段先定义 6 个核心 action；后续 N+2 阶段会扩展到约 20 种
 * （DOWNLOAD/RESTORE/PURGE/MOVE/RENAME/SHARE_REVOKE/LINK_REVOKE/LINK_ACCESS/
 *  EDIT_OPEN/VERSION_RESTORE/MEMBER_ADD/MEMBER_REMOVE/QUOTA_EXCEEDED/LOGIN/LOGOUT 等）。
 */
public enum AuditAction {

    /** 文件 / 文件夹上传或创建 */
    UPLOAD,

    /** 文件下载（含个人盘与外链匿名访问） */
    DOWNLOAD,

    /** 文件 / 文件夹移入回收站 */
    DELETE,

    /** 回收站还原 */
    RESTORE,

    /** 回收站彻底删除（不可恢复） */
    PURGE,

    /** 文件 / 文件夹移动到其它目录 */
    MOVE,

    /** 文件 / 文件夹跨目录或跨空间复制 */
    COPY,

    /** 文件 / 文件夹重命名（不含批量） */
    RENAME,

    /** 内部共享创建 */
    SHARE_CREATE,

    /** 内部共享更新（权限 / 期限 / canReshare 等） */
    SHARE_UPDATE,

    /** 内部共享撤销 */
    SHARE_REVOKE,

    /** 公开外链创建 */
    LINK_CREATE,

    /** 公开外链更新（密码 / 期限 / 下限限额） */
    LINK_UPDATE,

    /** 公开外链撤销 */
    LINK_REVOKE,

    /** 公开外链匿名访问（细节由 DrivePublicLinkAccessLog 详细记录） */
    LINK_ACCESS,

    /** OnlyOffice 编辑窗口打开（生成 edit ticket 时） */
    EDIT_OPEN,

    /** OnlyOffice 编辑完成回写成功 */
    EDIT_COMPLETED,

    /** OnlyOffice 编辑回调失败（验签/下载/写盘异常） */
    EDIT_FAILED,

    /** 协作空间成员加入 */
    MEMBER_ADD,

    /** 协作空间成员移除 */
    MEMBER_REMOVE
}
