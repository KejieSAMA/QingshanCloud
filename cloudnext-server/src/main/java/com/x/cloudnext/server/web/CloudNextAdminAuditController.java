package com.x.cloudnext.server.web;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.x.cloudnext.server.audit.AuditAction;
import com.x.cloudnext.server.audit.AuditLogResponse;
import com.x.cloudnext.server.audit.repository.AuditLogRepository;
import com.x.cloudnext.server.auth.AdminAccessService;
import com.x.cloudnext.server.form.repository.PrincipalBindingRepository;

/**
 * Admin 审计日志查询端点（N+2.2）。
 *
 * 鉴权：每个端点入口调用 {@link AdminAccessService#requireAdmin(String)}；
 *      白名单不命中即 403，且本 controller 不依赖 Spring Security，
 *      与 N+2.0 引入的低侵入鉴权一致。
 *
 * 不实现 CSV 导出 / 趋势图 / 聚合统计 — 留 N+3。
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class CloudNextAdminAuditController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final AdminAccessService adminAccessService;
    private final AuditLogRepository auditLogRepository;
    private final PrincipalBindingRepository principalBindings;

    public CloudNextAdminAuditController(
            AdminAccessService adminAccessService,
            AuditLogRepository auditLogRepository,
            PrincipalBindingRepository principalBindings) {
        this.adminAccessService = adminAccessService;
        this.auditLogRepository = auditLogRepository;
        this.principalBindings = principalBindings;
    }

    @GetMapping
    public Page<AuditLogResponse> list(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @RequestParam(name = "person", required = false) String person,
            @RequestParam(name = "action", required = false) String actionName,
            @RequestParam(name = "since", required = false) Long sinceMs,
            @RequestParam(name = "until", required = false) Long untilMs,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "size", required = false, defaultValue = "20") Integer size) {

        adminAccessService.requireAdmin(token);

        int pageIndex = page == null || page < 1 ? 0 : page - 1;
        int pageSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE
                : Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(pageIndex, pageSize);

        String personFilter = (person == null || person.trim().isEmpty()) ? null : person.trim();
        AuditAction actionFilter = parseAction(actionName);
        Instant sinceFilter = sinceMs == null ? null : Instant.ofEpochMilli(sinceMs);
        Instant untilFilter = untilMs == null ? null : Instant.ofEpochMilli(untilMs);

        Page<com.x.cloudnext.server.audit.entity.AuditLog> page1 =
                auditLogRepository.queryForAdmin(personFilter, actionFilter, sinceFilter, untilFilter, pageable);
        return page1.map(log->{AuditLogResponse response=AuditLogResponse.from(log);principalBindings.findByCanonicalPersonId(log.getPersonOaId()).ifPresent(binding->response.setEmployeeNo(binding.getEmployeeNoSnapshot()));return response;});
    }

    private AuditAction parseAction(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        try {
            return AuditAction.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ignore) {
            return null;
        }
    }
}
