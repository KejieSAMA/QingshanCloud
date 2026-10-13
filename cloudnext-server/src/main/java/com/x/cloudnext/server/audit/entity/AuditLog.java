package com.x.cloudnext.server.audit.entity;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.PrePersist;
import javax.persistence.Table;

import com.x.cloudnext.server.audit.AuditAction;

/**
 * 系统审计日志。记录用户在 CloudNext 中的关键写操作，用于：
 *  - N+2 Admin 后台「审计日志」面板的数据来源；
 *  - N+1 OnlyOffice 回调失败的事后排查；
 *  - 后续合规 / 数据安全场景。
 *
 * 写入路径：service 层在写操作末尾调用 {@link com.x.cloudnext.server.audit.AuditLogService#record}。
 * 不放在 controller 层是为了让 service 内部的批量 / 重试逻辑也能正确记录。
 */
@Entity
@Table(
        name = "cloudnext_audit_log",
        indexes = {
                @Index(name = "idx_audit_person_created", columnList = "person_oa_id, created_at"),
                @Index(name = "idx_audit_action_created", columnList = "action, created_at"),
                @Index(name = "idx_audit_target", columnList = "target_type, target_id")
        })
public class AuditLog {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    /** 操作者；系统/异步任务可置空字符串 */
    @Column(name = "person_oa_id", nullable = false, length = 64)
    private String personOaId = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private AuditAction action;

    /** 目标实体类型：DRIVE_ITEM / DRIVE_SHARE / LINK / WORKSPACE / ONLYOFFICE_CALLBACK 等 */
    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType = "";

    @Column(name = "target_id", nullable = false, length = 256)
    private String targetId = "";

    /** 额外上下文，JSON 字符串；首版本不做结构约束，由 service 拼装 */
    @Lob
    @Column(name = "payload")
    private String payload;

    /** 操作来源 IP；N+1 暂不强制采集（service 层无 request 上下文），留字段为后续扩展 */
    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress = "";

    /** UA；同上 */
    @Column(name = "user_agent", nullable = false, length = 512)
    private String userAgent = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPersonOaId() { return personOaId; }
    public void setPersonOaId(String personOaId) { this.personOaId = personOaId == null ? "" : personOaId; }

    public AuditAction getAction() { return action; }
    public void setAction(AuditAction action) { this.action = action; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType == null ? "" : targetType; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId == null ? "" : targetId; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress == null ? "" : ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent == null ? "" : userAgent; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
