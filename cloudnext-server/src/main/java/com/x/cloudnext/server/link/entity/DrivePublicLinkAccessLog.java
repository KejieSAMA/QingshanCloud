package com.x.cloudnext.server.link.entity;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.PrePersist;
import javax.persistence.Table;

import com.x.cloudnext.server.link.PublicLinkAccessFailReason;

/**
 * 外链匿名访问日志（N+2.3）。
 *
 * 每次 verify / preview / download 不论成功失败都写一行。token 作为索引主轴：
 * - owner 端查询：先按 owner.linkId 找出 token，再按 token 查日志；
 * - 风控：按 token+ip 滑动窗口聚合（速率限制由 PublicLinkRateLimiter 单独维护，本表用于事后审计）。
 *
 * 不存 linkId 是有意为之：token 在 cloudnext_drive_public_link 表已经
 * unique，省一层 join；revoke 也不改 token，所以历史 log 永远能跟当前 link 关联。
 */
@Entity
@Table(
        name = "cloudnext_drive_public_link_access_log",
        indexes = {
                @Index(name = "idx_link_access_token_attempted", columnList = "token, attempted_at"),
                @Index(name = "idx_link_access_attempted", columnList = "attempted_at"),
                @Index(name = "idx_link_access_token_success", columnList = "token, success")
        })
public class DrivePublicLinkAccessLog {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    /** 外链 token；与 DrivePublicLink.token 一致 */
    @Column(name = "token", nullable = false, length = 64)
    private String token = "";

    /** 触发端点：VERIFY / PREVIEW / DOWNLOAD / META（虽然 META 是无副作用读，本期不记） */
    @Column(name = "action_type", nullable = false, length = 16)
    private String actionType = "";

    /** 业务成功标志（HTTP 2xx → true） */
    @Column(name = "success", nullable = false)
    private boolean success;

    @Enumerated(EnumType.STRING)
    @Column(name = "fail_reason", nullable = false, length = 32)
    private PublicLinkAccessFailReason failReason = PublicLinkAccessFailReason.OK;

    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress = "";

    @Column(name = "user_agent", nullable = false, length = 512)
    private String userAgent = "";

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @PrePersist
    void onCreate() {
        if (attemptedAt == null) {
            attemptedAt = Instant.now();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token == null ? "" : token; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType == null ? "" : actionType; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public PublicLinkAccessFailReason getFailReason() { return failReason; }
    public void setFailReason(PublicLinkAccessFailReason failReason) {
        this.failReason = failReason == null ? PublicLinkAccessFailReason.OTHER : failReason;
    }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress == null ? "" : ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent == null ? "" : userAgent; }

    public Instant getAttemptedAt() { return attemptedAt; }
    public void setAttemptedAt(Instant attemptedAt) { this.attemptedAt = attemptedAt; }
}
