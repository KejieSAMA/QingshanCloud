package com.x.cloudnext.server.link;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.x.cloudnext.server.audit.AuditAction;
import com.x.cloudnext.server.audit.AuditLogService;
import com.x.cloudnext.server.link.entity.DrivePublicLinkAccessLog;
import com.x.cloudnext.server.link.repository.DrivePublicLinkAccessLogRepository;

/**
 * 外链匿名访问的事后审计写入器（N+2.3）。
 *
 * 调用方（{@code CloudNextPublicLinkController}）在 verify / preview / download
 * 三处入口分别用 try-catch 包装：
 * - 成功 → {@link #recordSuccess(String, String, String, String)}
 * - {@code ResponseStatusException} → {@link #recordFailure(String, String, String, String, ResponseStatusException)}
 *
 * 任何 audit 写失败都不会反向影响业务调用方（catch 内 swallow）。
 *
 * 同步写两条记录：
 * 1. {@code cloudnext_drive_public_link_access_log} 详细访问日志（owner 端"访问统计"抽屉数据源）；
 * 2. {@code cloudnext_audit_log} 用 {@link AuditAction#LINK_ACCESS} 聚合，便于 Admin 全局审计面板检索。
 */
@Service
public class PublicLinkAccessLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicLinkAccessLogger.class);

    private static final int UA_MAX_LENGTH = 500;

    private final DrivePublicLinkAccessLogRepository accessLogRepository;
    private final AuditLogService auditLogService;

    public PublicLinkAccessLogger(
            DrivePublicLinkAccessLogRepository accessLogRepository,
            AuditLogService auditLogService) {
        this.accessLogRepository = accessLogRepository;
        this.auditLogService = auditLogService;
    }

    public void recordSuccess(String token, String ip, String userAgent, String action) {
        persist(token, action, true, PublicLinkAccessFailReason.OK, ip, userAgent);
        auditLogService.record(
                "anonymous-link",
                AuditAction.LINK_ACCESS,
                "DRIVE_PUBLIC_LINK",
                token,
                "action", action,
                "success", true,
                "ip", safe(ip));
    }

    public void recordFailure(String token, String ip, String userAgent, String action, ResponseStatusException e) {
        PublicLinkAccessFailReason reason = classify(e);
        persist(token, action, false, reason, ip, userAgent);
        auditLogService.record(
                "anonymous-link",
                AuditAction.LINK_ACCESS,
                "DRIVE_PUBLIC_LINK",
                token,
                "action", action,
                "success", false,
                "reason", reason.name(),
                "httpStatus", e.getRawStatusCode(),
                "ip", safe(ip));
    }

    private void persist(String token, String action, boolean success,
                         PublicLinkAccessFailReason reason, String ip, String userAgent) {
        try {
            DrivePublicLinkAccessLog log = new DrivePublicLinkAccessLog();
            log.setId(UUID.randomUUID().toString());
            log.setToken(token == null ? "" : token);
            log.setActionType(action == null ? "" : action);
            log.setSuccess(success);
            log.setFailReason(reason);
            log.setIpAddress(safe(ip));
            log.setUserAgent(truncateUserAgent(userAgent));
            log.setAttemptedAt(Instant.now());
            accessLogRepository.save(log);
        } catch (RuntimeException ex) {
            LOGGER.warn("cloudnext public link access log persistence failed: token={}, action={}", token, action, ex);
        }
    }

    private PublicLinkAccessFailReason classify(ResponseStatusException e) {
        if (e == null) {
            return PublicLinkAccessFailReason.OTHER;
        }
        int status = e.getRawStatusCode();
        String reason = e.getReason() == null ? "" : e.getReason().toLowerCase();
        if (status == 429) {
            return PublicLinkAccessFailReason.RATE_LIMITED;
        }
        if (status == 404) {
            return PublicLinkAccessFailReason.NOT_FOUND;
        }
        if (status == 410) {
            if (reason.contains("expired")) {
                return PublicLinkAccessFailReason.EXPIRED;
            }
            if (reason.contains("exhausted")) {
                return PublicLinkAccessFailReason.EXHAUSTED;
            }
            if (reason.contains("revoked")) {
                return PublicLinkAccessFailReason.REVOKED;
            }
            return PublicLinkAccessFailReason.OTHER;
        }
        if (status == 403) {
            if (reason.contains("password")) {
                return PublicLinkAccessFailReason.WRONG_PASSWORD;
            }
            return PublicLinkAccessFailReason.PERMISSION_DENIED;
        }
        return PublicLinkAccessFailReason.OTHER;
    }

    private String truncateUserAgent(String ua) {
        if (ua == null) {
            return "";
        }
        return ua.length() > UA_MAX_LENGTH ? ua.substring(0, UA_MAX_LENGTH) : ua;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
