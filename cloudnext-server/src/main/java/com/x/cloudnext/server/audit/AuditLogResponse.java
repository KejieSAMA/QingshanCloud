package com.x.cloudnext.server.audit;

import java.time.Instant;

import com.x.cloudnext.server.audit.entity.AuditLog;

/**
 * Admin 审计端点的响应 DTO。
 * 不直接暴露 entity，避免 ipAddress / userAgent 在前端误用 / 未来 entity 演化耦合。
 * 当前 v1 暴露的字段已足够 Admin 面板表格展示与详情抽屉所需。
 */
public class AuditLogResponse {

    private String id;
    private String personOaId;
    private String personKey;
    private String employeeNo;
    private String action;
    private String targetType;
    private String targetId;
    private String payload;
    private String ipAddress;
    private String userAgent;
    private Instant createdAt;

    public static AuditLogResponse from(AuditLog log) {
        if (log == null) {
            return null;
        }
        AuditLogResponse r = new AuditLogResponse();
        r.id = log.getId();
        r.personOaId = log.getPersonOaId();
        r.personKey = log.getPersonOaId();
        r.action = log.getAction() == null ? "" : log.getAction().name();
        r.targetType = log.getTargetType();
        r.targetId = log.getTargetId();
        r.payload = log.getPayload();
        r.ipAddress = log.getIpAddress();
        r.userAgent = log.getUserAgent();
        r.createdAt = log.getCreatedAt();
        return r;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPersonOaId() { return personOaId; }
    public void setPersonOaId(String personOaId) { this.personOaId = personOaId; }
    public String getPersonKey(){return personKey;} public void setPersonKey(String v){personKey=v;}
    public String getEmployeeNo(){return employeeNo;} public void setEmployeeNo(String v){employeeNo=v;}

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
