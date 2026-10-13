package com.x.cloudnext.server.link;

import java.time.Instant;

import com.x.cloudnext.server.link.entity.DrivePublicLinkAccessLog;

/**
 * 外链访问日志的 owner 端响应 DTO。
 * 不暴露 entity 本身，避免未来字段演化耦合到前端。
 */
public class DrivePublicLinkAccessLogResponse {

    private String id;
    private String actionType;
    private boolean success;
    private String failReason;
    private String ipAddress;
    private String userAgent;
    private Instant attemptedAt;

    public static DrivePublicLinkAccessLogResponse from(DrivePublicLinkAccessLog log) {
        if (log == null) {
            return null;
        }
        DrivePublicLinkAccessLogResponse r = new DrivePublicLinkAccessLogResponse();
        r.id = log.getId();
        r.actionType = log.getActionType();
        r.success = log.isSuccess();
        r.failReason = log.getFailReason() == null ? "" : log.getFailReason().name();
        r.ipAddress = log.getIpAddress();
        r.userAgent = log.getUserAgent();
        r.attemptedAt = log.getAttemptedAt();
        return r;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public Instant getAttemptedAt() { return attemptedAt; }
    public void setAttemptedAt(Instant attemptedAt) { this.attemptedAt = attemptedAt; }
}
