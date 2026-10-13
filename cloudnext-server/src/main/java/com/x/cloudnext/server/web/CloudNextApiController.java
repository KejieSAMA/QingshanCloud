package com.x.cloudnext.server.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.x.cloudnext.server.auth.AdminAccessService;
import com.x.cloudnext.server.config.CloudNextProperties;
import com.x.cloudnext.server.drive.DriveOnlyofficePreviewReadiness;
import com.x.cloudnext.server.drive.DriveOnlyofficePreviewStatusService;
import com.x.cloudnext.server.orgsync.OrgMirrorStats;
import com.x.cloudnext.server.orgsync.OrgMirrorSyncService;
import com.x.cloudnext.server.orgsync.OrgSyncSnapshot;
import com.x.cloudnext.server.orgsync.OrgSyncService;

@RestController
@RequestMapping("/api")
public class CloudNextApiController {

    private final CloudNextProperties properties;
    private final OrgSyncService orgSyncService;
    private final OrgMirrorSyncService orgMirrorSyncService;
    private final DriveOnlyofficePreviewStatusService onlyofficePreviewStatusService;
    private final AdminAccessService adminAccessService;
    private final String maxFileSize;
    private final long maxFileSizeBytes;

    public CloudNextApiController(
            CloudNextProperties properties,
            OrgSyncService orgSyncService,
            OrgMirrorSyncService orgMirrorSyncService,
            DriveOnlyofficePreviewStatusService onlyofficePreviewStatusService,
            AdminAccessService adminAccessService,
            @org.springframework.beans.factory.annotation.Value("${spring.servlet.multipart.max-file-size:100MB}") String maxFileSize,
            @org.springframework.beans.factory.annotation.Value("${spring.servlet.multipart.max-file-size:100MB}") org.springframework.util.unit.DataSize maxFileSizeDataSize) {
        this.properties = properties;
        this.orgSyncService = orgSyncService;
        this.orgMirrorSyncService = orgMirrorSyncService;
        this.onlyofficePreviewStatusService = onlyofficePreviewStatusService;
        this.adminAccessService = adminAccessService;
        this.maxFileSize = maxFileSize;
        this.maxFileSizeBytes = maxFileSizeDataSize.toBytes();
    }

    @GetMapping("/status")
    public CloudNextStatusResponse status() {
        OrgSyncSnapshot snapshot = orgSyncService.snapshot();
        DriveOnlyofficePreviewReadiness readiness = onlyofficePreviewStatusService.getReadiness();
        CloudNextStatusResponse response = new CloudNextStatusResponse();
        response.setName("cloudnext");
        response.setPhase(snapshot.getPhase());
        response.setReady(Boolean.FALSE);
        response.setOrgSyncMode(snapshot.getMode());
        response.setOrgSyncOutcome(snapshot.getLastOutcome());
        response.setNextAction(snapshot.getNextAction());
        response.setOnlyofficePreviewReady(readiness.isConfigured());
        response.setWarnings(readiness.getWarnings());
        response.setPendingCapabilities(derivePendingCapabilities());
        return response;
    }

    /**
     * Derives the {@code pendingCapabilities} list from
     * {@code cloudnext.iteration.*} feature flags. A capability is considered
     * pending when its iteration flag is still disabled.
     *
     * <p>Two capability names are kept unconditionally because they describe
     * legacy work items that are not yet wired to a runtime flag:
     * {@code org-sync-detail} and {@code identity-broker}.
     */
    private List<String> derivePendingCapabilities() {
        CloudNextProperties.Iteration iteration = properties.getIteration();
        List<String> pending = new ArrayList<>();
        // Legacy capabilities still in development (not flag-driven yet).
        pending.add("org-sync-detail");
        if (!iteration.getWorkspaces().isEnabled()) {
            pending.add("workspaces");
        }
        if (!iteration.getPublicLinks().isEnabled()) {
            pending.add("public-links");
        }
        if (!iteration.getSearch().isEnabled()) {
            pending.add("search");
        }
        if (!iteration.getOnlyofficeEdit().isEnabled()) {
            pending.add("onlyoffice-edit");
        }
        if (!iteration.getGovernance().isEnabled()) {
            pending.add("governance");
        }
        if (!iteration.getPreviewHealth().isEnabled()) {
            pending.add("preview-health");
        }
        if (!iteration.getStorageGateway().isObjectStorageEnabled()) {
            pending.add("storage-gateway-object-storage");
        }
        return pending;
    }

    @GetMapping("/admin/config")
    public CloudNextConfigResponse config(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token) {
        OrgSyncSnapshot snapshot = orgSyncService.snapshot();
        OrgMirrorStats stats = orgMirrorSyncService.stats();
        DriveOnlyofficePreviewReadiness readiness = onlyofficePreviewStatusService.getReadiness();
        boolean hasReadonlyJdbcSource = properties.getReadonlyJdbc().isConfigured();
        boolean orgMirrorEmpty = stats.getPersons() == 0L && stats.getUnits() == 0L && stats.getIdentities() == 0L;
        boolean orgMirrorLastSyncSucceeded = "success".equalsIgnoreCase(snapshot.getLastOutcome());
        boolean isAdmin = adminAccessService.checkIsAdminByToken(token);
        CloudNextConfigResponse response = new CloudNextConfigResponse();
        response.setAuthMode(properties.getAuth().getMode());
        response.setAuthProvider(properties.getAuth().getEffectiveProvider());
        response.setAuthConfigured(properties.getAuth().isConfigured());
        response.setSyncBatchSize(properties.getOrgSync().getBatchSize());
        response.setSyncIntervalMinutes(properties.getOrgSync().getIntervalMinutes());
        response.setSyncCron(properties.getOrgSync().getCron());
        response.setHasReadonlyJdbcSource(hasReadonlyJdbcSource);
        // 敏感字段仅对白名单 admin 返回；非 admin 已登录用户拿到 null（AdminPage 仅看 admin 标志判断 Tab 显隐）
        response.setObjectStorageEndpoint(isAdmin ? properties.getObjectStorage().getEndpoint() : null);
        response.setObjectStorageBucket(isAdmin ? properties.getObjectStorage().getBucket() : null);
        response.setCallbackBaseUrl(isAdmin ? properties.getEndpoints().getCallbackBaseUrl() : null);
        response.setWebBaseUrl(isAdmin ? properties.getEndpoints().getWebBaseUrl() : null);
        response.setMaxUploadFileSize(maxFileSize);
        response.setMaxUploadFileSizeBytes(maxFileSizeBytes);
        response.setTrashRetentionDays(properties.getDrive().getTrashRetentionDays());
        response.setPreviewTicketTtlSeconds(properties.getDrive().getPreviewTicketTtlSeconds());
        // onlyoffice 健康度（jwtEnabled/missingConfigKeys）属于运维健康信号、不含密钥本身 — 对所有登录用户开放
        response.setOnlyofficePreviewConfigured(readiness.isConfigured());
        response.setOnlyofficeJwtEnabled(readiness.isJwtEnabled());
        response.setOnlyofficeMissingConfigKeys(readiness.getMissingConfigKeys());
        response.setOrgMirrorReady(hasReadonlyJdbcSource && orgMirrorLastSyncSucceeded && !orgMirrorEmpty);
        response.setOrgMirrorEmpty(orgMirrorEmpty);
        response.setOrgMirrorLastSyncSucceeded(orgMirrorLastSyncSucceeded);
        return response;
    }

    @GetMapping("/admin/sync/status")
    public OrgSyncSnapshot syncStatus(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token) {
        OrgSyncSnapshot snapshot = orgSyncService.snapshot();
        if (snapshot != null && !adminAccessService.checkIsAdminByToken(token)) {
            // failureLog/lastMessage 可能含异常堆栈或 SQL 片段；非白名单 admin 抹掉
            snapshot.setFailureLog("");
            snapshot.setLastMessage("");
        }
        return snapshot;
    }

    @GetMapping("/admin/sync/stats")
    public OrgMirrorStatsResponse syncStats() {
        OrgMirrorStats stats = orgMirrorSyncService.stats();
        OrgMirrorStatsResponse response = new OrgMirrorStatsResponse();
        response.setUnits(stats.getUnits());
        response.setPersons(stats.getPersons());
        response.setIdentities(stats.getIdentities());
        response.setRoles(stats.getRoles());
        response.setGroups(stats.getGroups());
        response.setMemberships(stats.getMemberships());
        response.setCapturedAt(stats.getCapturedAt());
        return response;
    }

    @PostMapping("/admin/sync/trigger")
    public SyncTriggerResponse triggerSync(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token) {
        adminAccessService.requireAdmin(token);
        return SyncTriggerResponse.fromSnapshot(orgSyncService.triggerManualSync());
    }

    /**
     * 前端探测当前 session 是否管理员；不暴露白名单内容。
     * AdminPage 据此决定是否渲染需要管理员权限的 Tab（如审计日志面板）。
     * 无 token / token 失效 / 不在白名单 → isAdmin=false（不抛 401/403，避免前端
     * 在未登录时受连锁影响）。
     */
    @GetMapping("/admin/whoami")
    public CloudNextAdminWhoamiResponse whoami(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token) {
        CloudNextAdminWhoamiResponse response = new CloudNextAdminWhoamiResponse();
        response.setAdmin(adminAccessService.checkIsAdminByToken(token));
        return response;
    }
}
