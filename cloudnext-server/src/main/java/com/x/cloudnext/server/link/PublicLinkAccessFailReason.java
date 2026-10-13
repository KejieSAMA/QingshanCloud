package com.x.cloudnext.server.link;

/**
 * 外链访问的细粒度失败原因（N+2.3）。
 *
 * 由 {@link PublicLinkAccessLogger} 把 {@link org.springframework.web.server.ResponseStatusException}
 * 的 HTTP 状态码 + reason 字符串映射到本枚举，写入 {@code DrivePublicLinkAccessLog} 表，
 * 便于 owner 端的"访问统计"抽屉与未来的风控聚合 SQL。
 */
public enum PublicLinkAccessFailReason {

    /** 占位：访问成功时记录的 reason（success=true 时使用） */
    OK,

    /** 密码错误（含 password required 与 password incorrect） */
    WRONG_PASSWORD,

    /** 链接已过期 */
    EXPIRED,

    /** 下载次数已用完 */
    EXHAUSTED,

    /** 链接已被 owner 撤销 */
    REVOKED,

    /** Token 不存在 / 文件已被删除 */
    NOT_FOUND,

    /** 权限不足（如 PREVIEW 链接尝试下载） */
    PERMISSION_DENIED,

    /** 触发速率限制（同 token + ip 1 分钟超过阈值） */
    RATE_LIMITED,

    /** 其它未分类原因（5xx / 未知 reason 字符串） */
    OTHER
}
