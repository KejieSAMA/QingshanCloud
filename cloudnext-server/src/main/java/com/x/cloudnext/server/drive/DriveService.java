package com.x.cloudnext.server.drive;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.cloudnext.server.audit.AuditAction;
import com.x.cloudnext.server.audit.AuditLogService;
import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.auth.AuthIdentityDto;
import com.x.cloudnext.server.config.CloudNextProperties;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;

@Service
public class DriveService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.x.cloudnext.server.drive.next.DriveVersionService versionService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.x.cloudnext.server.drive.next.DriveExperienceService experienceService;

    private static final Logger LOGGER = LoggerFactory.getLogger(DriveService.class);

    /**
     * N+2.7 F5: OnlyOffice forcesave 第一次失败后的退避时间。给 docserver 写盘
     * 一个 800ms 喘息窗口，比立刻重试更友好且实测能消除大部分 -3/-4 误报。
     */
    private static final long ONLYOFFICE_FORCESAVE_RETRY_BACKOFF_MS = 800L;

    private final DriveItemRepository driveItemRepository;
    private final DriveStorageService driveStorageService;
    private final DriveAccessService driveAccessService;
    private final DriveScopeAccessService driveScopeAccessService;
    private final DriveItemResponseAssembler driveItemResponseAssembler;
    private final DriveReadService driveReadService;
    private final DriveShareService driveShareService;
    private final CloudNextProperties properties;
    private final DriveOnlyofficePreviewStatusService onlyofficePreviewStatusService;
    private final DrivePreviewTicketService drivePreviewTicketService;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;
    private RestTemplate restTemplate;
    private final Set<String> appliedOnlyofficeCallbackKeys = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, DriveOnlyofficeSaveSessionState> onlyofficeSaveStates = new ConcurrentHashMap<>();

    public DriveService(
            DriveItemRepository driveItemRepository,
            DriveStorageService driveStorageService,
            DriveAccessService driveAccessService,
            DriveScopeAccessService driveScopeAccessService,
            DriveItemResponseAssembler driveItemResponseAssembler,
            DriveReadService driveReadService,
            DriveShareService driveShareService,
            CloudNextProperties properties,
            DriveOnlyofficePreviewStatusService onlyofficePreviewStatusService,
            DrivePreviewTicketService drivePreviewTicketService,
            ObjectMapper objectMapper,
            AuditLogService auditLogService,
            RestTemplateBuilder restTemplateBuilder) {
        this.driveItemRepository = driveItemRepository;
        this.driveStorageService = driveStorageService;
        this.driveAccessService = driveAccessService;
        this.driveScopeAccessService = driveScopeAccessService;
        this.driveItemResponseAssembler = driveItemResponseAssembler;
        this.driveReadService = driveReadService;
        this.driveShareService = driveShareService;
        this.properties = properties;
        this.onlyofficePreviewStatusService = onlyofficePreviewStatusService;
        this.drivePreviewTicketService = drivePreviewTicketService;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
        this.restTemplate = restTemplateBuilder.build();
    }

    @Transactional(readOnly = true)
    public List<DriveItemResponse> listMyItems(String token, String parentId) {
        return driveReadService.listMyItems(token, parentId);
    }

    @Transactional(readOnly = true)
    public DrivePageResponse queryMyItems(String token, DriveQueryRequest request) {
        return driveReadService.queryMyItems(token, request);
    }

    @Transactional(readOnly = true)
    public List<DriveItemResponse> listTrashItems(String token) {
        return driveReadService.listTrashItems(token);
    }

    @Transactional(readOnly = true)
    public DrivePageResponse queryTrashItems(String token, DriveQueryRequest request) {
        return driveReadService.queryTrashItems(token, request);
    }

    @Transactional(readOnly = true)
    public List<DriveItemResponse> listRecentItems(String token) {
        return driveReadService.listRecentItems(token);
    }

    @Transactional(readOnly = true)
    public List<DriveItemResponse> listRecentOpenedItems(String token, Integer limit) {
        return driveReadService.listRecentOpenedItems(token, limit);
    }

    @Transactional(readOnly = true)
    public List<DrivePathNodeResponse> getPath(String token, String itemId) {
        return driveReadService.getPath(token, itemId);
    }

    @Transactional
    public DriveItemResponse createFolder(String token, DriveCreateFolderRequest request) {
        AuthLoginResponse session = requireSession(token);
        DriveScopeContext scope = DriveScopeContext.personal(session, resolveOwnerName(session));
        return createFolderInScope(
                scope,
                normalizeParentId(request == null ? "" : request.getParentId()),
                request == null ? "" : request.getName());
    }

    /**
     * Scope-aware folder creation shared by personal flows and
     * {@code WorkspaceService}. The {@link DriveScopeContext} carries the
     * scope key that {@link DriveItem#getScopeType()} / {@link DriveItem#getScopeId()}
     * are persisted with as well as the owner identity used for audit /
     * preview flows. Personal callers wrap this through
     * {@link #createFolder(String, DriveCreateFolderRequest)}; workspace
     * callers go directly via {@link com.x.cloudnext.server.workspace.WorkspaceService}.
     *
     * <p>Validation order matches the legacy personal-only path so existing
     * client error messages remain unchanged: parent accessibility → name
     * normalization → sibling-name conflict.
     */
    @Transactional
    public DriveItemResponse createFolderInScope(DriveScopeContext scope, String parentId, String rawName) {
        String normalizedParentId = normalizeParentId(parentId);
        assertParentAccessibleInScope(scope, normalizedParentId);
        String folderName = normalizeRequiredName(rawName, "folder name is required");
        ensureSiblingNameAvailableInScope(scope, normalizedParentId, folderName, null);

        DriveItem item = new DriveItem();
        item.setId(generateId());
        item.setParentId(normalizedParentId);
        item.setOwnerPersonOaId(scope.getOwnerPersonOaId());
        item.setOwnerName(resolveScopeOwnerName(scope));
        item.setName(folderName);
        item.setItemType(DriveItemType.FOLDER);
        item.setFileType("folder");
        item.setContentType("");
        item.setSizeBytes(0L);
        item.setStorageKey("");
        item.setScopeType(scope.getScopeType());
        item.setScopeId(scope.getScopeId());
        return driveItemResponseAssembler.toResponseWithParentName(driveItemRepository.save(item));
    }

    @Transactional
    public DriveItemResponse uploadFile(String token, String parentId, MultipartFile file, DriveConflictStrategy conflictStrategy) {
        AuthLoginResponse session = requireSession(token);
        DriveScopeContext scope = DriveScopeContext.personal(session, resolveOwnerName(session));
        return uploadFileInScope(scope, parentId, file, conflictStrategy);
    }

    /**
     * Scope-aware file upload shared by personal and workspace flows. Mirrors
     * the legacy {@link #uploadFile(String, String, MultipartFile, DriveConflictStrategy)}
     * conflict resolution exactly, so existing personal upload tests / clients
     * keep working unchanged after the wrapper is in place.
     */
    @Transactional
    public DriveItemResponse uploadFileInScope(
            DriveScopeContext scope,
            String parentId,
            MultipartFile file,
            DriveConflictStrategy conflictStrategy) {
        long t0 = System.nanoTime();
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        String normalizedParentId = normalizeParentId(parentId);
        assertParentAccessibleInScope(scope, normalizedParentId);

        String resolvedName = resolveFileName(file);
        DriveConflictStrategy strategy = normalizeConflictStrategy(conflictStrategy);
        DriveItem conflict = findActiveSiblingInScope(scope, normalizedParentId, resolvedName, null);
        if (conflict != null) {
            if (strategy == DriveConflictStrategy.ABORT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "item with the same name already exists");
            }
            if (strategy == DriveConflictStrategy.REPLACE) {
                if (conflict.getItemType() != DriveItemType.FILE) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "cannot replace a folder with a file");
                }
                String previousStorageKey = conflict.getStorageKey();
                DriveStoredFile stored = versionService.store(conflict, file, scope.getOwnerPersonOaId(), resolveScopeOwnerName(scope));
                conflict.setName(resolvedName);
                conflict.setFileType(resolveFileType(resolvedName, file.getContentType()));
                conflict.setStorageKey(stored.getStorageKey());
                conflict.setSizeBytes(stored.getSizeBytes());
                conflict.setContentType(stored.getContentType());
                conflict.setDeleted(false);
                conflict.setDeletedAt("");
                DriveItem saved = driveItemRepository.save(conflict);
                // Previous content remains referenced by the immutable version history.
                auditLogService.record(
                        scope.getOwnerPersonOaId(),
                        AuditAction.UPLOAD,
                        "DRIVE_ITEM",
                        saved.getId(),
                        "name", saved.getName(),
                        "sizeBytes", stored.getSizeBytes(),
                        "contentType", stored.getContentType(),
                        "scope", scope.getScopeType() == null ? "" : scope.getScopeType().name(),
                        "conflict", "replace");
                logTiming("upload", saved.getId(), t0, stored.getSizeBytes());
                return driveItemResponseAssembler.toResponseWithParentName(saved);
            }
            resolvedName = generateAvailableNameInScope(scope, normalizedParentId, resolvedName, null, true);
        }

        DriveItem item = new DriveItem();
        item.setId(generateId());
        item.setParentId(normalizedParentId);
        item.setOwnerPersonOaId(scope.getOwnerPersonOaId());
        item.setOwnerName(resolveScopeOwnerName(scope));
        item.setName(resolvedName);
        item.setItemType(DriveItemType.FILE);
        item.setFileType(resolveFileType(resolvedName, file.getContentType()));
        item.setScopeType(scope.getScopeType());
        item.setScopeId(scope.getScopeId());
        DriveStoredFile stored = versionService.store(item, file, scope.getOwnerPersonOaId(), resolveScopeOwnerName(scope));
        item.setStorageKey(stored.getStorageKey());
        item.setSizeBytes(stored.getSizeBytes());
        item.setContentType(stored.getContentType());
        DriveItem saved = driveItemRepository.save(item);
        auditLogService.record(
                scope.getOwnerPersonOaId(),
                AuditAction.UPLOAD,
                "DRIVE_ITEM",
                saved.getId(),
                "name", saved.getName(),
                "sizeBytes", stored.getSizeBytes(),
                "contentType", stored.getContentType(),
                "scope", scope.getScopeType() == null ? "" : scope.getScopeType().name(),
                "parentId", normalizedParentId);
        logTiming("upload", saved.getId(), t0, stored.getSizeBytes());
        return driveItemResponseAssembler.toResponseWithParentName(saved);
    }

    @Transactional
    public DrivePreviewResponse getPreview(String token, String itemId) {
        long t0 = System.nanoTime();
        AuthLoginResponse session = requireSession(token);

        // Unified scope-aware authorization. requireItem 通过 DriveScopeAccessService
        // 同时覆盖 PERSONAL (owner / share) 与 WORKSPACE (成员矩阵) 两路鉴权 — 修复
        // 之前只走 DriveShareService.requireAccess 导致协作空间成员访问非自己上传文件
        // 时被 403 的 bug。
        DriveItem item = driveScopeAccessService.requireItem(session, itemId, DriveScopeAction.READ);

        AccessContext context;
        if (item.getScopeType() == DriveScopeType.WORKSPACE) {
            // Workspace 成员：下载资格按 WRITE 矩阵判定（OWNER/ADMIN/MEMBER 可下载，
            // VIEWER 只读）。permission 标签同时驱动 OnlyOffice 编辑器侧 UI。
            boolean canDownload = driveScopeAccessService.canActOn(session, item, DriveScopeAction.WRITE);
            context = AccessContext.userWithPermissions(
                    session, true, canDownload, canDownload ? "edit" : "read");
        } else {
            // PERSONAL：保持原有 share 派生语义不变。owner 或 active share 都能命中；
            // 走到这里说明 requireItem 已通过 READ，理论上 share 必存在（非 owner 时）。
            // 用 orElseGet 兜底防御性构造 owner 视图，避免依赖隐性不变量崩溃。
            DriveResolvedShareAccess access = driveShareService.resolveAccess(session, item)
                    .orElseGet(() -> DriveResolvedShareAccess.owner(item));
            context = AccessContext.user(session, access);
        }

        DrivePreviewResponse response = buildPreviewForItem(item, context);
        if (Boolean.TRUE.equals(response.getSupported())) experienceService.mark(session, item.getId(), false, true);
        logTiming("preview", item.getId(), t0, response.getKind() == null ? "" : response.getKind());
        return response;
    }

    /**
     * Build the per-item {@link DrivePreviewResponse} payload for an already
     * authorized {@link DriveItem}. Phase A of {@link #getPreview} (session +
     * share / scope authorization) lives upstream of this method; phase B —
     * picking the preview {@code kind}, populating the OnlyOffice ticket, and
     * touching the last-accessed timestamp — is what this method owns.
     *
     * <p>This method intentionally does <b>not</b> re-run any authorization. It
     * trusts that the caller has already proven {@code context} is allowed to
     * preview {@code item}. Callers today:
     * <ul>
     *   <li>{@link #getPreview(String, String)} — passes
     *       {@link AccessContext#user(AuthLoginResponse, DriveResolvedShareAccess)},
     *       so the share-derived {@code allowDownload} and
     *       {@code effectivePermission} stay byte-for-byte identical to the
     *       legacy single-method implementation.</li>
     *   <li>{@code DrivePublicLinkService} (task 2.5, separate
     *       {@code com.x.cloudnext.server.link} package) — will pass
     *       {@link AccessContext#publicLink(String, boolean, boolean)} after
     *       running the {@code revoked → expired → permission/limit} gate. The
     *       method is therefore {@code public} so the link package can reach
     *       it.</li>
     * </ul>
     *
     * <p>OnlyOffice configuration loading is unchanged. The ticket payload
     * still goes through {@link DrivePreviewTicketService#create} with the
     * same TTL and the same {@code viewerPersonOaId} / {@code viewerName}
     * semantics; for PUBLIC_LINK callers, {@code viewerPersonOaId} carries the
     * link token (so OnlyOffice gets a non-empty identifier and the audit
     * layer can trace the request back to the token) while {@code viewerName}
     * is empty, which makes the existing OnlyOffice editor config fall back to
     * {@code item.ownerName}.
     */
    public DrivePreviewResponse buildPreviewForItem(DriveItem item, AccessContext context) {
        if (item.isDeleted() || item.getItemType() != DriveItemType.FILE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "file not found");
        }

        DrivePreviewResponse response = new DrivePreviewResponse();
        response.setItemId(item.getId());
        response.setMode("view");
        response.setFileName(item.getName());
        response.setContentType(item.getContentType());
        response.setPreviewUrl("/api/drive/items/" + item.getId() + "/content");
        response.setAllowDownload(context.isDownloadAllowed());

        String kind = resolvePreviewKind(item);
        response.setKind(kind);
        response.setSupported(!"unsupported".equals(kind));

        if (!Boolean.TRUE.equals(response.getSupported())) {
            return response;
        }
        if ("office".equals(kind)) {
            DriveOnlyofficePreviewReadiness readiness = onlyofficePreviewStatusService.getReadiness();
            if (!readiness.isConfigured()) {
                response.setSupported(false);
                response.setUnavailableCode(readiness.getUnavailableCode());
                response.setUnavailableMessage(readiness.getUnavailableMessage());
                return response;
            }
            DrivePreviewTicketPayload payload = new DrivePreviewTicketPayload();
            payload.setItemId(item.getId());
            payload.setOwnerPersonOaId(item.getOwnerPersonOaId());
            payload.setViewerPersonOaId(resolveViewerId(context));
            payload.setViewerName(resolveViewerName(context));
            payload.setViewerPersonOaIdRaw(resolveViewerRawPersonOaId(context));
            payload.setEffectivePermission(context.getEffectivePermission());
            payload.setAllowDownload(context.isDownloadAllowed());
            payload.setAllowEdit(false);
            payload.setScopeType(item.getScopeType().name());
            payload.setScopeId(item.getScopeId());
            payload.setExpiresAt(Instant.now()
                    .plus(properties.getDrive().getPreviewTicketTtlSeconds().longValue(), ChronoUnit.SECONDS)
                    .toEpochMilli());
            String ticket = drivePreviewTicketService.create(payload);
            response.setPreviewTicket(ticket);
            response.setPreviewPageUrl(buildPreviewPageUrl(ticket));
            return response;
        }
        touchLastAccessed(item);
        return response;
    }

    @Transactional
    public DriveDownloadPayload downloadItem(String token, String itemId) {
        long t0 = System.nanoTime();
        AuthLoginResponse session = requireSession(token);
        // Scope-aware：先 READ 通过 scope 鉴权拿 item，再按 scope 分支判定下载资格
        // （workspace VIEWER 不允许下载；personal 走 share DOWNLOAD 权限）。
        DriveItem item = driveScopeAccessService.requireItem(session, itemId, DriveScopeAction.READ);
        if (item.getScopeType() == DriveScopeType.WORKSPACE) {
            if (!driveScopeAccessService.canActOn(session, item, DriveScopeAction.WRITE)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "workspace viewer cannot download");
            }
        } else {
            // PERSONAL：复用既有 share DOWNLOAD 检查（read-only 共享被拒等语义保留不变）
            driveShareService.requireAccess(session, itemId, DriveSharePermission.DOWNLOAD);
        }
        auditLogService.record(
                session.getPersonOaId(),
                AuditAction.DOWNLOAD,
                "DRIVE_ITEM",
                item.getId(),
                "name", item.getName(),
                "sizeBytes", item.getSizeBytes(),
                "contentType", item.getContentType());
        DriveDownloadPayload payload = loadFilePayload(item, true);
        logTiming("download", item.getId(), t0, item.getSizeBytes());
        return payload;
    }

    @Transactional
    public DriveDownloadPayload contentItem(String token, String itemId) {
        long t0 = System.nanoTime();
        AuthLoginResponse session = requireSession(token);
        // Scope-aware：preview/inline content 走 READ 矩阵 — workspace 任何成员都允许；
        // personal owner / active share 都允许。修复协作空间成员预览别人文件被 403。
        DriveItem item = driveScopeAccessService.requireItem(session, itemId, DriveScopeAction.READ);
        DriveDownloadPayload payload = loadFilePayload(item, true);
        // N+2.7 F1: preview 高频路径，audit 改异步避免阻塞业务线程
        auditLogService.recordAsync(
                session.getPersonOaId(),
                AuditAction.DOWNLOAD,
                "DRIVE_ITEM",
                item.getId(),
                "mode", "preview",
                "name", item.getName(),
                "contentType", payload.getContentType() == null ? "" : payload.getContentType());
        logTiming("preview-content", item.getId(), t0, item.getSizeBytes());
        return payload;
    }

    @Transactional
    public DriveItemResponse deleteItem(String token, String itemId) {
        AuthLoginResponse session = requireSession(token);
        DriveItem item = driveScopeAccessService.requireItem(session, itemId, DriveScopeAction.DELETE);
        assertNotWorkspaceRoot(item, "cannot delete workspace root");
        markDeletedRecursively(session.getPersonOaId(), item, Instant.now().toString());
        auditLogService.record(
                session.getPersonOaId(),
                AuditAction.DELETE,
                "DRIVE_ITEM",
                item.getId(),
                "name", item.getName(),
                "itemType", item.getItemType() == null ? "" : item.getItemType().name(),
                "scope", item.getScopeType() == null ? "" : item.getScopeType().name());
        return driveItemResponseAssembler.toResponseWithParentName(item);
    }

    /**
     * Scope-aware soft-delete shared by personal and workspace flows. The
     * caller has already gone through {@link DriveScopeAccessService#requireItem}
     * (or an equivalent membership check inside {@code WorkspaceService}); this
     * entry point only owns the recursive marking and trash-isolation guarantee.
     *
     * <p>Folder traversal walks {@code scopeType + scopeId} children, so a
     * personal delete never crosses into a workspace subtree and vice versa
     * (which is what makes {@code WorkspaceTrash} actually isolated from the
     * personal trash for the same person).
     */
    @Transactional
    public DriveItemResponse deleteItemInScope(DriveScopeContext scope, DriveItem item) {
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found");
        }
        assertNotWorkspaceRoot(item, "cannot delete workspace root");
        markDeletedRecursivelyInScope(scope, item, Instant.now().toString());
        return driveItemResponseAssembler.toResponseWithParentName(item);
    }

    @Transactional
    public DriveItemResponse renameItem(String token, String itemId, DriveRenameItemRequest request) {
        AuthLoginResponse session = requireSession(token);
        DriveItem item = driveScopeAccessService.requireItem(session, itemId, DriveScopeAction.WRITE);
        assertNotWorkspaceRoot(item, "cannot rename workspace root");
        if (item.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deleted item cannot be renamed");
        }
        String previousName = item.getName();
        String nextName = normalizeRequiredName(request == null ? "" : request.getName(), "name is required");
        ensureSiblingNameAvailableInScope(scopeOf(item, session.getPersonOaId()), item.getParentId(), nextName, item.getId());
        item.setName(nextName);
        DriveItem saved = driveItemRepository.save(item);
        auditLogService.record(
                session.getPersonOaId(),
                AuditAction.RENAME,
                "DRIVE_ITEM",
                saved.getId(),
                "from", previousName,
                "to", nextName,
                "scope", saved.getScopeType() == null ? "" : saved.getScopeType().name());
        return driveItemResponseAssembler.toResponseWithParentName(saved);
    }

    @Transactional
    public DriveItemResponse restoreItem(String token, String itemId) {
        DriveBatchIdsRequest request = new DriveBatchIdsRequest();
        request.setItemIds(Collections.singletonList(itemId));
        DriveBatchOperationResponse result = restoreTrashItems(token, request);
        if (result.getTotalSucceeded() != 1) {
            String message = result.getFailedItems().values().stream().findFirst().orElse("restore failed");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        AuthLoginResponse session = requireSession(token);
        return driveItemResponseAssembler.toResponseWithParentName(
                driveScopeAccessService.requireItem(session, itemId, DriveScopeAction.READ));
    }

    @Transactional
    public DriveBatchOperationResponse moveItems(String token, DriveMoveItemsRequest request) {
        AuthLoginResponse session = requireSession(token);
        String ownerPersonOaId = session.getPersonOaId();
        String targetParentId = normalizeParentId(request == null ? "" : request.getTargetParentId());
        List<DriveItem> items = filterRootSelections(
                loadAccessibleItems(
                        session,
                        request == null ? Collections.emptyList() : request.getItemIds(),
                        DriveScopeAction.WRITE));

        DriveBatchOperationResponse response = new DriveBatchOperationResponse();
        response.setTotalRequested(items.size());
        DriveConflictStrategy strategy = normalizeConflictStrategy(request == null ? null : request.getConflictStrategy());
        for (DriveItem item : items) {
            try {
                moveSingleItem(ownerPersonOaId, item, targetParentId, strategy);
                response.getSucceededIds().add(item.getId());
            } catch (ResponseStatusException e) {
                response.getFailedItems().put(item.getId(), e.getReason());
            }
        }
        finalizeBatchResponse(response, "move");
        if (!items.isEmpty()) {
            auditBatchOperation(ownerPersonOaId, AuditAction.MOVE, response, items.get(0).getId(),
                    "targetParentId", targetParentId);
        }
        return response;
    }

    @Transactional
    public DriveBatchOperationResponse batchRename(String token, DriveBatchRenameRequest request) {
        AuthLoginResponse session = requireSession(token);
        List<DriveItem> items = loadAccessibleItems(
                session,
                request == null ? Collections.emptyList() : request.getItemIds(),
                DriveScopeAction.WRITE);
        DriveBatchOperationResponse response = new DriveBatchOperationResponse();
        response.setTotalRequested(items.size());
        if (items.isEmpty()) {
            response.setMessage("no items selected");
            return response;
        }

        String baseName = normalizeRequiredName(request == null ? "" : request.getBaseName(), "base name is required");
        int startNumber = request == null || request.getStartNumber() == null ? 1 : Math.max(0, request.getStartNumber());
        int numberWidth = request == null || request.getNumberWidth() == null ? 2 : Math.max(1, request.getNumberWidth());

        Map<String, String> nextNames = new LinkedHashMap<>();
        Set<String> seenNames = new LinkedHashSet<>();
        for (int i = 0; i < items.size(); i++) {
            DriveItem item = items.get(i);
            if (isWorkspaceRoot(item)) {
                response.getFailedItems().put(item.getId(), "cannot rename workspace root");
                continue;
            }
            if (item.isDeleted()) {
                response.getFailedItems().put(item.getId(), "deleted item cannot be renamed");
                continue;
            }
            String candidateName = buildBatchRenameName(item, baseName, startNumber + i, numberWidth);
            DriveScopeContext scope = scopeOf(item, session.getPersonOaId());
            String duplicateKey = scope.getScopeType().name()
                    + "|" + scope.getScopeId()
                    + "|" + item.getParentId()
                    + "|" + candidateName.toLowerCase(Locale.ROOT);
            if (!seenNames.add(duplicateKey)) {
                response.getFailedItems().put(item.getId(), "generated duplicate target name");
                continue;
            }
            DriveItem conflict = findActiveSiblingInScope(scope, item.getParentId(), candidateName, item.getId());
            if (conflict != null) {
                response.getFailedItems().put(item.getId(), "item with the same name already exists");
                continue;
            }
            nextNames.put(item.getId(), candidateName);
        }

        if (!response.getFailedItems().isEmpty()) {
            finalizeBatchResponse(response, "batch rename");
            return response;
        }

        for (DriveItem item : items) {
            item.setName(nextNames.get(item.getId()));
            driveItemRepository.save(item);
            response.getSucceededIds().add(item.getId());
        }
        finalizeBatchResponse(response, "batch rename");
        if (!items.isEmpty()) {
            auditBatchOperation(session.getPersonOaId(), AuditAction.RENAME, response, items.get(0).getId(),
                    "mode", "batch",
                    "baseName", baseName);
        }
        return response;
    }

    @Transactional
    public DriveBatchOperationResponse restoreTrashItems(String token, DriveBatchIdsRequest request) {
        AuthLoginResponse session = requireSession(token);
        String ownerPersonOaId = session.getPersonOaId();
        List<DriveItem> items = filterRootSelections(
                loadAccessibleItems(
                        session,
                        request == null ? Collections.emptyList() : request.getItemIds(),
                        DriveScopeAction.WRITE));
        items.sort(Comparator.comparingInt(item -> depthOf(item)));

        DriveBatchOperationResponse response = new DriveBatchOperationResponse();
        response.setTotalRequested(items.size());
        Set<String> selectedIds = items.stream().map(DriveItem::getId).collect(Collectors.toSet());
        for (DriveItem item : items) {
            try {
                restoreSingleItem(ownerPersonOaId, item, selectedIds);
                response.getSucceededIds().add(item.getId());
            } catch (ResponseStatusException e) {
                response.getFailedItems().put(item.getId(), e.getReason());
            }
        }
        finalizeBatchResponse(response, "restore");
        if (!items.isEmpty()) {
            auditBatchOperation(ownerPersonOaId, AuditAction.RESTORE, response, items.get(0).getId());
        }
        return response;
    }

    @Transactional
    public DriveBatchOperationResponse purgeTrashItems(String token, DriveBatchIdsRequest request) {
        AuthLoginResponse session = requireSession(token);
        String ownerPersonOaId = session.getPersonOaId();
        List<DriveItem> items = filterRootSelections(
                loadAccessibleItems(
                        session,
                        request == null ? Collections.emptyList() : request.getItemIds(),
                        DriveScopeAction.DELETE));

        DriveBatchOperationResponse response = new DriveBatchOperationResponse();
        response.setTotalRequested(items.size());
        for (DriveItem item : items) {
            try {
                assertNotWorkspaceRoot(item, "cannot purge workspace root");
                if (!item.isDeleted()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "item is not in trash");
                }
                permanentDeleteRecursively(ownerPersonOaId, item);
                response.getSucceededIds().add(item.getId());
            } catch (ResponseStatusException e) {
                response.getFailedItems().put(item.getId(), e.getReason());
            }
        }
        finalizeBatchResponse(response, "purge");
        if (!items.isEmpty()) {
            auditBatchOperation(ownerPersonOaId, AuditAction.PURGE, response, items.get(0).getId());
        }
        return response;
    }

    @Transactional
    public DriveBatchOperationResponse clearTrash(String token) {
        AuthLoginResponse session = requireSession(token);
        List<DriveItem> deletedItems = driveItemRepository.findByScopeTypeAndScopeIdAndDeletedTrue(
                DriveScopeType.PERSONAL,
                session.getPersonOaId());
        List<DriveItem> roots = filterDeletedRoots(deletedItems);

        DriveBatchOperationResponse response = new DriveBatchOperationResponse();
        response.setTotalRequested(roots.size());
        for (DriveItem item : roots) {
            try {
                permanentDeleteRecursively(session.getPersonOaId(), item);
                response.getSucceededIds().add(item.getId());
            } catch (ResponseStatusException e) {
                response.getFailedItems().put(item.getId(), e.getReason());
            }
        }
        finalizeBatchResponse(response, "clear trash");
        if (!roots.isEmpty()) {
            auditBatchOperation(session.getPersonOaId(), AuditAction.PURGE, response, roots.get(0).getId(),
                    "mode", "clear-trash");
        }
        return response;
    }

    @Transactional(readOnly = true)
    public DriveOnlyofficeViewConfigResponse getOnlyofficeViewConfig(String previewTicket, String requestBaseUrl) {
        requireOnlyofficeDocumentServer();
        DrivePreviewTicketPayload payload = drivePreviewTicketService.parse(previewTicket);
        DriveItem item = requireOwnedItem(payload.getOwnerPersonOaId(), payload.getItemId());
        if (item.isDeleted() || item.getItemType() != DriveItemType.FILE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "preview file not found");
        }
        if (!"office".equals(resolvePreviewKind(item))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "current file does not support onlyoffice preview");
        }

        DriveOnlyofficeViewConfigResponse response = new DriveOnlyofficeViewConfigResponse();
        response.setFileName(item.getName());
        response.setMode("view");
        response.setDocserviceApi(properties.getDrive().getOnlyoffice().getDocserviceApi());
        response.setDocumentServerUrl(properties.getDrive().getOnlyoffice().getDocumentServerUrl());
        response.setDocEditorConfig(buildOnlyofficeDocEditorConfig(item, payload, requestBaseUrl, previewTicket, false));
        return response;
    }

    @Transactional(readOnly = true)
    public DriveOnlyofficeViewConfigResponse getOnlyofficeEditConfig(String editTicket, String requestBaseUrl) {
        requireOnlyofficeEditEnabled();
        onlyofficePreviewStatusService.requireEditConfigured();
        DrivePreviewTicketPayload payload = drivePreviewTicketService.parse(editTicket);
        if (!payload.isAllowEdit()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "edit ticket does not allow editing");
        }
        DriveItem item = requireCurrentEditableItem(payload);
        String currentKey = buildOnlyofficeDocumentKey(item);
        if (!StringUtils.pathEquals(currentKey, payload.getDocumentKey())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "edit ticket is stale");
        }

        DriveOnlyofficeViewConfigResponse response = new DriveOnlyofficeViewConfigResponse();
        response.setFileName(item.getName());
        response.setMode("edit");
        response.setDocserviceApi(properties.getDrive().getOnlyoffice().getDocserviceApi());
        response.setDocumentServerUrl(properties.getDrive().getOnlyoffice().getDocumentServerUrl());
        response.setDocEditorConfig(buildOnlyofficeDocEditorConfig(item, payload, requestBaseUrl, editTicket, true));
        return response;
    }

    @Transactional(readOnly = true)
    public DriveOnlyofficeEditTicketResponse getOnlyofficeEditTicket(String token, String itemId) {
        requireOnlyofficeEditEnabled();
        onlyofficePreviewStatusService.requireEditConfigured();
        AuthLoginResponse session = requireSession(token);
        DriveItem item = driveScopeAccessService.requireItem(session, itemId, DriveScopeAction.READ);
        if (item.isDeleted() || item.getItemType() != DriveItemType.FILE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "edit file not found");
        }
        if (!"office".equals(resolvePreviewKind(item))) {
            return buildUnsupportedEditTicket(item, "onlyoffice_edit_unsupported", "current file does not support onlyoffice editing");
        }
        requireOnlyofficeEditable(session, item);
        DrivePreviewTicketPayload payload = buildEditTicketPayload(session, item);
        String editTicket = drivePreviewTicketService.create(payload);
        DriveOnlyofficeSaveSessionState state = onlyofficeSaveStates.computeIfAbsent(editTicket, DriveOnlyofficeSaveSessionState::new);
        state.setState("idle");
        state.setMessage("");
        state.setUpdatedAt(null);
        state.setSizeBytes(null);
        DriveOnlyofficeEditTicketResponse response = new DriveOnlyofficeEditTicketResponse();
        response.setItemId(item.getId());
        response.setKind(resolvePreviewKind(item));
        response.setSupported(true);
        response.setMode("edit");
        response.setFileName(item.getName());
        response.setEditTicket(editTicket);
        response.setEditPageUrl("/edit/office?ticket=" + URLEncoder.encode(editTicket, StandardCharsets.UTF_8));
        experienceService.mark(session, item.getId(), false, true);
        auditLogService.record(
                session.getPersonOaId(),
                AuditAction.EDIT_OPEN,
                "DRIVE_ITEM",
                item.getId(),
                "name", item.getName(),
                "kind", response.getKind(),
                "scope", item.getScopeType() == null ? "" : item.getScopeType().name());
        return response;
    }

    @Transactional(readOnly = true)
    public DriveOnlyofficeSaveStatusResponse getOnlyofficeSaveStatus(String editTicket) {
        requireOnlyofficeEditEnabled();
        onlyofficePreviewStatusService.requireEditConfigured();
        DriveOnlyofficeSaveSessionState state = onlyofficeSaveStates.get(editTicket);
        if (state == null) {
            DriveOnlyofficeSaveStatusResponse response = new DriveOnlyofficeSaveStatusResponse();
            response.setEditTicket(editTicket);
            response.setState("idle");
            response.setPending(false);
            response.setSaved(false);
            response.setFailed(false);
            return response;
        }
        DriveOnlyofficeSaveStatusResponse response = new DriveOnlyofficeSaveStatusResponse();
        response.setEditTicket(editTicket);
        response.setState(state.getState());
        response.setPending(state.isPending());
        response.setSaved(state.isSaved());
        response.setFailed(state.isFailed());
        response.setMessage(state.getMessage());
        response.setUpdatedAt(state.getUpdatedAt());
        response.setSizeBytes(state.getSizeBytes());
        return response;
    }

    @Transactional
    public DriveOnlyofficeForceSaveResponse forceOnlyofficeSave(String editTicket, String authorizationHeader) {
        requireOnlyofficeEditEnabled();
        onlyofficePreviewStatusService.requireEditConfigured();
        DrivePreviewTicketPayload payload = drivePreviewTicketService.parse(editTicket);
        if (!payload.isAllowEdit()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "edit ticket does not allow editing");
        }
        DriveItem item = requireCurrentEditableItem(payload);
        DriveOnlyofficeSaveSessionState state = onlyofficeSaveStates.computeIfAbsent(editTicket, DriveOnlyofficeSaveSessionState::new);
        state.setState("pending");
        state.setMessage("saving");
        state.setUpdatedAt(null);
        state.setSizeBytes(null);

        Map<String, Object> payloadBody = new LinkedHashMap<>();
        payloadBody.put("c", "forcesave");
        payloadBody.put("key", payload.getDocumentKey());
        payloadBody.put("shardkey", payload.getDocumentKey());
        if (Boolean.TRUE.equals(properties.getDrive().getOnlyoffice().getJwtEnabled())) {
            payloadBody.put("token", signOnlyofficeJwtPayload(payloadBody));
        }

        String commandUrl = normalizeConfiguredBaseUrl(properties.getDrive().getOnlyoffice().getDocumentServerUrl());
        if (!StringUtils.hasText(commandUrl)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "cloudnext onlyoffice document server is not configured");
        }
        commandUrl = commandUrl + "/command";
        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payloadBody);
            ResponseEntity<Map> response;
            boolean retried = false;
            try {
                response = restTemplate.exchange(commandUrl, HttpMethod.POST, request, Map.class);
            } catch (RestClientException firstError) {
                // 网络瞬时抖动 — 退避 800ms 重试一次再决定是否报错
                LOGGER.warn("cloudnext forcesave first attempt failed, retrying once: {}", firstError.getMessage());
                try {
                    Thread.sleep(ONLYOFFICE_FORCESAVE_RETRY_BACKOFF_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw firstError;
                }
                retried = true;
                HttpEntity<Map<String, Object>> retryRequest = new HttpEntity<>(payloadBody);
                response = restTemplate.exchange(commandUrl, HttpMethod.POST, retryRequest, Map.class);
            }
            Object errorRaw = response.getBody() == null ? null : response.getBody().get("error");
            Integer errorCode = coerceInteger(errorRaw);
            boolean http2xx = response.getStatusCode().is2xxSuccessful();
            DriveOnlyofficeForceSaveResponse result = new DriveOnlyofficeForceSaveResponse();
            if (http2xx && Integer.valueOf(0).equals(errorCode)) {
                result.setAccepted(true);
                result.setState("pending");
                result.setMessage("forcesave accepted");
            } else if (http2xx && Integer.valueOf(4).equals(errorCode)) {
                // OnlyOffice error=4: 自上次保存以来文档无修改，视为已是最新版本
                result.setAccepted(true);
                result.setState("saved");
                result.setMessage("已是最新版本");
                if (!"saved".equals(state.getState())) {
                    state.setState("saved");
                    state.setMessage("已是最新版本");
                    if (state.getUpdatedAt() == null) {
                        state.setUpdatedAt(System.currentTimeMillis());
                    }
                } else {
                    state.setMessage("已是最新版本");
                }
            } else {
                String reason = "forcesave rejected (error=" + errorCode + ")";
                result.setAccepted(false);
                result.setState("failed");
                result.setMessage(reason);
                state.setState("failed");
                state.setMessage(reason);
                auditLogService.record(
                        auditActor(payload),
                        AuditAction.EDIT_FAILED,
                        "ONLYOFFICE_FORCESAVE",
                        payload.getItemId(),
                        "reason", reason,
                        "errorCode", errorCode,
                        "retried", retried);
            }
            return result;
        } catch (RestClientException e) {
            state.setState("failed");
            state.setMessage("forcesave request failed");
            auditLogService.record(
                    auditActor(payload),
                    AuditAction.EDIT_FAILED,
                    "ONLYOFFICE_FORCESAVE",
                    payload.getItemId(),
                    "reason", e.getMessage() == null ? "rest client exception" : e.getMessage(),
                    "exception", e.getClass().getSimpleName(),
                    "retried", true);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "cloudnext could not request onlyoffice forcesave", e);
        }
    }

    @Transactional
    public DriveDownloadPayload loadOnlyofficeContent(String previewTicket) {
        requireOnlyofficeDocumentServer();
        DrivePreviewTicketPayload payload = drivePreviewTicketService.parse(previewTicket);
        if (StringUtils.hasText(payload.getVersionId())) {
            AuthLoginResponse viewer = new AuthLoginResponse();
            viewer.setPersonOaId(payload.getViewerPersonOaId());
            com.x.cloudnext.server.drive.next.DriveItemVersion version = versionService.requireVersion(viewer, payload.getItemId(), payload.getVersionId());
            return new DriveDownloadPayload(version.fileName, version.contentType, versionService.content(version));
        }
        DriveItem item = requireOwnedItem(payload.getOwnerPersonOaId(), payload.getItemId());
        if (!"office".equals(resolvePreviewKind(item))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "current file does not support onlyoffice preview");
        }
        return loadFilePayload(item, true);
    }

    @Transactional
    public DriveOnlyofficeCallbackResponse handleOnlyofficeCallback(
            String editTicket,
            DriveOnlyofficeCallbackRequest request,
            String authorizationHeader) {
        long t0 = System.nanoTime();
        requireOnlyofficeEditEnabled();
        onlyofficePreviewStatusService.requireEditConfigured();
        if (request == null || request.getStatus() == null) {
            return DriveOnlyofficeCallbackResponse.failed();
        }
        if (!isFinalOnlyofficeSaveStatus(request.getStatus())) {
            return DriveOnlyofficeCallbackResponse.ok();
        }
        try {
            DriveOnlyofficeCallbackRequest verifiedRequest = verifyOnlyofficeCallback(request, authorizationHeader);
            if (!StringUtils.hasText(verifiedRequest.getKey()) || !StringUtils.hasText(verifiedRequest.getUrl())) {
                return DriveOnlyofficeCallbackResponse.failed();
            }
            DrivePreviewTicketPayload payload = drivePreviewTicketService.parse(editTicket);
            if (!payload.isAllowEdit() || !StringUtils.pathEquals(payload.getDocumentKey(), verifiedRequest.getKey())) {
                return DriveOnlyofficeCallbackResponse.failed();
            }
            String callbackKey = buildOnlyofficeCallbackId(editTicket, verifiedRequest);
            if (appliedOnlyofficeCallbackKeys.contains(callbackKey)) {
                return DriveOnlyofficeCallbackResponse.ok();
            }
            DriveItem item = requireCurrentEditableItem(payload);
            byte[] content = downloadOnlyofficeSavedContent(verifiedRequest.getUrl());
            DriveStoredFile stored = versionService.store(item, content, auditActor(payload), payload.getViewerName(), callbackKey);
            item.setStorageKey(stored.getStorageKey());
            item.setSizeBytes(stored.getSizeBytes());
            item.setContentType(stored.getContentType());
            item.setFileType(resolveFileType(item.getName(), stored.getContentType()));
            driveItemRepository.save(item);
            DriveOnlyofficeSaveSessionState state = onlyofficeSaveStates.computeIfAbsent(editTicket, DriveOnlyofficeSaveSessionState::new);
            state.setState("saving");
            state.setMessage("committing");
            state.setUpdatedAt(System.currentTimeMillis());
            state.setSizeBytes(stored.getSizeBytes());
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() {
                            appliedOnlyofficeCallbackKeys.add(callbackKey);
                            state.setState("saved"); state.setMessage("saved");
                        }
                        @Override public void afterCompletion(int status) {
                            if (status != STATUS_COMMITTED) { state.setState("failed"); state.setMessage("save transaction rolled back"); }
                        }
                    });
            auditLogService.record(
                    auditActor(payload),
                    AuditAction.EDIT_COMPLETED,
                    "DRIVE_ITEM",
                    item.getId(),
                    "name", item.getName(),
                    "sizeBytes", stored.getSizeBytes(),
                    "contentType", stored.getContentType(),
                    "status", request.getStatus());
            logTiming("onlyoffice-callback", item.getId(), t0, "ok");
            return DriveOnlyofficeCallbackResponse.ok();
        } catch (ResponseStatusException e) {
            DriveOnlyofficeSaveSessionState state = onlyofficeSaveStates.computeIfAbsent(editTicket, DriveOnlyofficeSaveSessionState::new);
            state.setState("failed");
            state.setMessage(e.getReason());
            LOGGER.warn("cloudnext onlyoffice callback rejected: {}", e.getReason());
            auditLogService.record(
                    auditActorFromTicket(editTicket),
                    AuditAction.EDIT_FAILED,
                    "ONLYOFFICE_CALLBACK",
                    auditCallbackTargetId(editTicket),
                    "reason", e.getReason(),
                    "httpStatus", e.getRawStatusCode(),
                    "status", request.getStatus());
            logTiming("onlyoffice-callback", auditCallbackTargetId(editTicket), t0, "failed");
            return DriveOnlyofficeCallbackResponse.failed();
        } catch (RuntimeException e) {
            DriveOnlyofficeSaveSessionState state = onlyofficeSaveStates.computeIfAbsent(editTicket, DriveOnlyofficeSaveSessionState::new);
            state.setState("failed");
            state.setMessage("callback failed");
            LOGGER.warn("cloudnext onlyoffice callback failed", e);
            auditLogService.record(
                    auditActorFromTicket(editTicket),
                    AuditAction.EDIT_FAILED,
                    "ONLYOFFICE_CALLBACK",
                    auditCallbackTargetId(editTicket),
                    "reason", e.getMessage() == null ? "callback failed" : e.getMessage(),
                    "exception", e.getClass().getSimpleName(),
                    "status", request.getStatus());
            logTiming("onlyoffice-callback", auditCallbackTargetId(editTicket), t0, "failed");
            return DriveOnlyofficeCallbackResponse.failed();
        }
    }

    private String auditCallbackTargetId(String editTicket) {
        try {
            String itemId = drivePreviewTicketService.parse(editTicket).getItemId();
            return StringUtils.hasText(itemId) ? itemId : "unknown";
        } catch (RuntimeException ignore) {
            return "unknown";
        }
    }

    private String auditActor(DrivePreviewTicketPayload payload) {
        if (payload == null) {
            return "system";
        }
        if (StringUtils.hasText(payload.getViewerPersonOaId())) {
            return payload.getViewerPersonOaId();
        }
        if (StringUtils.hasText(payload.getOwnerPersonOaId())) {
            return payload.getOwnerPersonOaId();
        }
        return "system";
    }

    private String auditActorFromTicket(String editTicket) {
        try {
            return auditActor(drivePreviewTicketService.parse(editTicket));
        } catch (RuntimeException ignore) {
            return "system";
        }
    }

    @Transactional
    public void cleanupExpiredTrash() {
        int retentionDays = properties.getDrive().getTrashRetentionDays() == null
                ? 30
                : Math.max(1, properties.getDrive().getTrashRetentionDays());
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        List<DriveItem> deletedItems = driveItemRepository.findByDeletedTrue().stream()
                .filter(item -> !isWorkspaceRoot(item))
                .filter(item -> isExpiredDeletedItem(item, cutoff))
                .collect(Collectors.toList());
        List<DriveItem> roots = filterDeletedRoots(deletedItems);
        for (DriveItem item : roots) {
            try {
                permanentDeleteRecursively(item.getOwnerPersonOaId(), item);
            } catch (Exception e) {
                LOGGER.warn("cloudnext trash cleanup failed for item {}", item.getId(), e);
            }
        }
    }

    private void moveSingleItem(String ownerPersonOaId, DriveItem item, String targetParentId, DriveConflictStrategy strategy) {
        assertNotWorkspaceRoot(item, "cannot move workspace root");
        if (item.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deleted item cannot be moved");
        }
        DriveScopeContext scope = scopeOf(item, ownerPersonOaId);
        String resolvedTargetParentId = resolveMoveTargetParent(scope, targetParentId);
        if (StringUtils.pathEquals(item.getId(), resolvedTargetParentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot move an item into itself");
        }
        if (item.getItemType() == DriveItemType.FOLDER
                && isDescendantFolderInScope(scope, resolvedTargetParentId, item.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot move a folder into its descendant");
        }
        if (StringUtils.pathEquals(item.getParentId(), resolvedTargetParentId)) {
            return;
        }

        String nextName = item.getName();
        DriveItem conflict = findActiveSiblingInScope(scope, resolvedTargetParentId, nextName, item.getId());
        if (conflict != null) {
            if (strategy == DriveConflictStrategy.ABORT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "item with the same name already exists");
            }
            if (strategy == DriveConflictStrategy.REPLACE) {
                permanentDeleteRecursively(ownerPersonOaId, conflict);
            } else {
                nextName = generateAvailableNameInScope(
                        scope,
                        resolvedTargetParentId,
                        nextName,
                        item.getId(),
                        item.getItemType() == DriveItemType.FILE);
            }
        }
        item.setParentId(resolvedTargetParentId);
        item.setName(nextName);
        driveItemRepository.save(item);
    }

    private void restoreSingleItem(String ownerPersonOaId, DriveItem item, Set<String> selectedIds) {
        DriveScopeContext scope = scopeOf(item, ownerPersonOaId);
        if (!item.isDeleted()) {
            return;
        }
        if (StringUtils.hasText(item.getParentId())) {
            DriveItem parent = findItemInScope(scope, item.getParentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "parent folder not found"));
            if (parent.isDeleted() && !selectedIds.contains(parent.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "parent folder is still in trash");
            }
            if (parent.isDeleted()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "restore parent folder first");
            }
        }
        markRestoredRecursivelyInScope(scope, item);
    }

    private List<DriveItem> filterRootSelections(List<DriveItem> items) {
        Set<String> folderIds = items.stream()
                .filter(item -> item.getItemType() == DriveItemType.FOLDER)
                .map(DriveItem::getId)
                .collect(Collectors.toSet());
        return items.stream()
                .filter(item -> !hasSelectedAncestorInScope(
                        scopeOf(item, item.getOwnerPersonOaId()), item.getParentId(), folderIds))
                .collect(Collectors.toList());
    }

    private boolean hasSelectedAncestorInScope(DriveScopeContext scope, String parentId, Set<String> folderIds) {
        String currentParentId = parentId;
        while (StringUtils.hasText(currentParentId)) {
            if (folderIds.contains(currentParentId)) {
                return true;
            }
            currentParentId = findItemInScope(scope, currentParentId)
                    .map(DriveItem::getParentId)
                    .orElse("");
        }
        return false;
    }

    private List<DriveItem> filterDeletedRoots(List<DriveItem> deletedItems) {
        Map<String, DriveItem> deletedMap = deletedItems.stream()
                .collect(Collectors.toMap(DriveItem::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        return deletedItems.stream()
                .filter(item -> !StringUtils.hasText(item.getParentId()) || !deletedMap.containsKey(item.getParentId()))
                .collect(Collectors.toList());
    }

    private boolean isDescendantFolderInScope(DriveScopeContext scope, String targetParentId, String folderId) {
        String currentParentId = targetParentId;
        while (StringUtils.hasText(currentParentId)) {
            if (StringUtils.pathEquals(folderId, currentParentId)) {
                return true;
            }
            currentParentId = findItemInScope(scope, currentParentId)
                    .map(DriveItem::getParentId)
                    .orElse("");
        }
        return false;
    }

    private void finalizeBatchResponse(DriveBatchOperationResponse response, String actionLabel) {
        response.setTotalSucceeded(response.getSucceededIds().size());
        response.setTotalFailed(response.getFailedItems().size());
        if (response.getTotalRequested() == 0) {
            response.setMessage("no items selected");
            return;
        }
        if (response.getTotalFailed() == 0) {
            response.setMessage(actionLabel + " succeeded: " + response.getTotalSucceeded());
            return;
        }
        if (response.getTotalSucceeded() == 0) {
            response.setMessage(actionLabel + " failed: " + response.getTotalFailed());
            return;
        }
        response.setMessage(actionLabel + " completed: " + response.getTotalSucceeded() + " succeeded, "
                + response.getTotalFailed() + " failed");
    }

    /**
     * N+2.7 F4: 热点端点耗时日志统一格式。
     *
     * 格式：cloudnext.timing op={op} target={id} ms={ms} extra={...}
     * 便于后续 grep 或集成结构化日志做 P95 / 慢请求分析。
     */
    private static void logTiming(String op, String targetId, long startNanos, Object extra) {
        long ms = (System.nanoTime() - startNanos) / 1_000_000L;
        LOGGER.info("cloudnext.timing op={} target={} ms={} extra={}",
                op, targetId == null ? "" : targetId, ms, extra);
    }

    /**
     * N+2.7 F2: 批量 Drive 操作的审计写入 helper。
     *
     * 抽自 moveItems/batchRename/restoreTrashItems/purgeTrashItems/clearTrash 共 5 处复制粘贴。
     * payload 自动追加 itemCount/succeeded/failed；调用方仅传 extraKv（如 mode/baseName/targetParentId）。
     * 若无成功项或目标 id 为空，静默跳过（保持现有"仅在成功时写聚合 audit"语义）。
     */
    private void auditBatchOperation(
            String actor,
            AuditAction action,
            DriveBatchOperationResponse response,
            String anyTargetId,
            Object... extraKv) {
        if (response == null || response.getTotalSucceeded() <= 0 || !StringUtils.hasText(anyTargetId)) {
            return;
        }
        Object[] base = new Object[]{
                "itemCount", response.getTotalSucceeded(),
                "succeeded", response.getTotalSucceeded(),
                "failed", response.getTotalFailed()};
        Object[] merged;
        if (extraKv == null || extraKv.length == 0) {
            merged = base;
        } else {
            merged = new Object[extraKv.length + base.length];
            System.arraycopy(extraKv, 0, merged, 0, extraKv.length);
            System.arraycopy(base, 0, merged, extraKv.length, base.length);
        }
        auditLogService.record(actor, action, "DRIVE_ITEM", anyTargetId, merged);
    }

    private List<DriveItem> loadAccessibleItems(
            AuthLoginResponse session, Collection<String> rawIds, DriveScopeAction action) {
        List<String> orderedIds = deduplicateIds(rawIds);
        List<DriveItem> items = new ArrayList<>();
        for (String id : orderedIds) {
            items.add(driveScopeAccessService.requireItem(session, id, action));
        }
        return items;
    }

    private List<String> deduplicateIds(Collection<String> rawIds) {
        if (rawIds == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String rawId : rawIds) {
            if (StringUtils.hasText(rawId)) {
                ids.add(rawId.trim());
            }
        }
        return new ArrayList<>(ids);
    }

    private int depthOf(DriveItem item) {
        DriveScopeContext scope = scopeOf(item, item.getOwnerPersonOaId());
        int depth = 0;
        String parentId = item.getParentId();
        while (StringUtils.hasText(parentId)) {
            depth++;
            parentId = findItemInScope(scope, parentId)
                    .map(DriveItem::getParentId)
                    .orElse("");
        }
        return depth;
    }

    private String resolveMoveTargetParent(DriveScopeContext scope, String targetParentId) {
        if (scope.getScopeType() == DriveScopeType.WORKSPACE && !StringUtils.hasText(targetParentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workspace move target parent is required");
        }
        assertParentAccessibleInScope(scope, targetParentId);
        return targetParentId;
    }

    private Optional<DriveItem> findItemInScope(DriveScopeContext scope, String itemId) {
        if (!StringUtils.hasText(itemId)) {
            return Optional.empty();
        }
        return driveItemRepository.findById(itemId)
                .filter(item -> effectiveScopeType(item) == scope.getScopeType())
                .filter(item -> equalsScopeId(effectiveScopeId(item), scope.getScopeId()));
    }

    private List<DriveItem> findChildrenInScope(DriveScopeContext scope, String parentId, boolean activeOnly) {
        return activeOnly
                ? driveItemRepository.findByScopeTypeAndScopeIdAndDeletedFalseAndParentId(
                        scope.getScopeType(), scope.getScopeId(), parentId)
                : driveItemRepository.findByScopeTypeAndScopeIdAndParentId(
                        scope.getScopeType(), scope.getScopeId(), parentId);
    }

    /**
     * Scope-aware parent accessibility check for personal and workspace
     * flows. It rejects cross-scope parents even when the same person owns or
     * uploaded both items.
     *
     * <p>An empty {@code parentId} is treated as the personal root. Workspace
     * move callers reject empty targets before reaching this helper, while
     * workspace create/upload callers resolve empty parent ids to
     * {@code workspace.rootItemId}.
     */
    private void assertParentAccessibleInScope(DriveScopeContext scope, String parentId) {
        if (!StringUtils.hasText(parentId)) {
            return;
        }
        DriveItem parent = driveItemRepository.findById(parentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "parent folder is invalid"));
        if (parent.isDeleted()
                || parent.getItemType() != DriveItemType.FOLDER
                || parent.getScopeType() != scope.getScopeType()
                || !equalsScopeId(parent.getScopeId(), scope.getScopeId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "parent folder is invalid");
        }
    }

    private boolean equalsScopeId(String left, String right) {
        return (left == null ? "" : left).equals(right == null ? "" : right);
    }

    private DriveScopeType effectiveScopeType(DriveItem item) {
        return item.getScopeType() == null ? DriveScopeType.PERSONAL : item.getScopeType();
    }

    private String effectiveScopeId(DriveItem item) {
        return StringUtils.hasText(item.getScopeId()) ? item.getScopeId() : item.getOwnerPersonOaId();
    }

    private void ensureSiblingNameAvailableInScope(
            DriveScopeContext scope, String parentId, String name, String ignoreId) {
        if (findActiveSiblingInScope(scope, parentId, name, ignoreId) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "item with the same name already exists");
        }
    }

    /**
     * Scope-aware sibling lookup for conflict detection. The lookup is keyed
     * by {@code scopeType + scopeId + parentId + name}, so workspace siblings
     * uploaded by different members and personal siblings owned by the same
     * person follow the same collision rule.
     */
    private DriveItem findActiveSiblingInScope(
            DriveScopeContext scope, String parentId, String name, String ignoreId) {
        return driveItemRepository
                .findByScopeTypeAndScopeIdAndDeletedFalseAndParentIdAndName(
                        scope.getScopeType(), scope.getScopeId(), parentId, name)
                .stream()
                .filter(item -> !StringUtils.pathEquals(item.getId(), ignoreId))
                .findFirst()
                .orElse(null);
    }

    private String generateAvailableNameInScope(
            DriveScopeContext scope,
            String parentId,
            String originalName,
            String ignoreId,
            boolean preserveExtension) {
        String extension = preserveExtension ? resolveExtension(originalName) : "";
        String stem = preserveExtension ? stripExtension(originalName) : originalName;
        String candidate = originalName;
        int index = 1;
        while (findActiveSiblingInScope(scope, parentId, candidate, ignoreId) != null) {
            candidate = stem + " (" + index + ")" + (extension.isEmpty() ? "" : "." + extension);
            index++;
        }
        return candidate;
    }

    private String resolveScopeOwnerName(DriveScopeContext scope) {
        if (scope == null) {
            return "";
        }
        if (StringUtils.hasText(scope.getOwnerName())) {
            return scope.getOwnerName();
        }
        return scope.getOwnerPersonOaId();
    }

    private String buildBatchRenameName(DriveItem item, String baseName, int number, int numberWidth) {
        String paddedNumber = String.format(Locale.ROOT, "%0" + numberWidth + "d", number);
        String stem = baseName + "-" + paddedNumber;
        if (item.getItemType() == DriveItemType.FILE) {
            String extension = resolveExtension(item.getName());
            return extension.isEmpty() ? stem : stem + "." + extension;
        }
        return stem;
    }

    private String stripExtension(String fileName) {
        String extension = resolveExtension(fileName);
        if (!StringUtils.hasText(extension)) {
            return fileName;
        }
        return fileName.substring(0, fileName.length() - extension.length() - 1);
    }

    private String resolveExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index <= 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String resolvePreviewKind(DriveItem item) {
        String fileType = normalizeSortString(item.getFileType());
        String contentType = normalizeSortString(item.getContentType());
        String extension = resolveExtension(item.getName());
        if (contentType.startsWith("image/") || isImageExtension(extension)) {
            return "image";
        }
        if ("pdf".equals(fileType) || "pdf".equals(extension) || contentType.contains("pdf")) {
            return "pdf";
        }
        if ("word".equals(fileType) || "excel".equals(fileType) || "ppt".equals(fileType) || isOfficeExtension(extension)) {
            return "office";
        }
        return "unsupported";
    }

    private boolean isImageExtension(String extension) {
        return "png".equals(extension) || "jpg".equals(extension) || "jpeg".equals(extension)
                || "gif".equals(extension) || "bmp".equals(extension) || "webp".equals(extension);
    }

    private boolean isOfficeExtension(String extension) {
        return "doc".equals(extension) || "docx".equals(extension) || "xls".equals(extension)
                || "xlsx".equals(extension) || "ppt".equals(extension) || "pptx".equals(extension);
    }

    private String buildPreviewPageUrl(String previewTicket) {
        String encodedTicket = URLEncoder.encode(previewTicket, StandardCharsets.UTF_8);
        String configuredWebBaseUrl = normalizeConfiguredBaseUrl(properties.getEndpoints().getWebBaseUrl());
        if (StringUtils.hasText(configuredWebBaseUrl)) {
            return configuredWebBaseUrl + "/preview/office?ticket=" + encodedTicket;
        }
        return "/preview/office?ticket=" + encodedTicket;
    }

    private String buildEditPageUrl(String editTicket) {
        String encodedTicket = URLEncoder.encode(editTicket, StandardCharsets.UTF_8);
        String configuredWebBaseUrl = normalizeConfiguredBaseUrl(properties.getEndpoints().getWebBaseUrl());
        if (StringUtils.hasText(configuredWebBaseUrl)) {
            return configuredWebBaseUrl + "/edit/office?ticket=" + encodedTicket;
        }
        return "/edit/office?ticket=" + encodedTicket;
    }

    private Map<String, Object> buildOnlyofficeDocEditorConfig(
            DriveItem item,
            DrivePreviewTicketPayload payload,
            String requestBaseUrl,
            String ticket,
            boolean editMode) {
        String extension = resolveExtension(item.getName());

        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("edit", editMode);
        permissions.put("download", payload.isAllowDownload());
        permissions.put("print", payload.isAllowDownload());

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fileType", extension);
        document.put("key", StringUtils.hasText(payload.getVersionId()) ? "version-" + payload.getVersionId() : buildOnlyofficeDocumentKey(item));
        document.put("title", item.getName());
        document.put("url", buildOnlyofficeContentUrl(requestBaseUrl, ticket));
        document.put("permissions", permissions);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", StringUtils.hasText(payload.getViewerPersonOaId()) ? payload.getViewerPersonOaId() : item.getOwnerPersonOaId());
        user.put("name", StringUtils.hasText(payload.getViewerName()) ? payload.getViewerName() : item.getOwnerName());

        Map<String, Object> customization = new LinkedHashMap<>();
        customization.put("compactHeader", true);
        customization.put("help", false);
        customization.put("feedback", false);
        customization.put("forcesave", editMode);

        Map<String, Object> editor = new LinkedHashMap<>();
        editor.put("mode", editMode ? "edit" : "view");
        editor.put("lang", "zh");
        editor.put("location", "zh-CN");
        editor.put("region", "zh-CN");
        editor.put("user", user);
        editor.put("customization", customization);
        if (editMode) {
            editor.put("callbackUrl", buildOnlyofficeCallbackUrl(ticket));
        }

        Map<String, Object> docEditorConfig = new LinkedHashMap<>();
        docEditorConfig.put("documentType", resolveOnlyofficeDocumentType(extension));
        docEditorConfig.put("document", document);
        docEditorConfig.put("editorConfig", editor);
        if (Boolean.TRUE.equals(properties.getDrive().getOnlyoffice().getJwtEnabled())) {
            docEditorConfig.put("token", signOnlyofficeDocEditorConfig(docEditorConfig));
        }
        return docEditorConfig;
    }

    private String buildOnlyofficeContentUrl(String requestBaseUrl, String previewTicket) {
        String baseUrl = normalizeConfiguredBaseUrl(properties.getEndpoints().getCallbackBaseUrl());
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = normalizeConfiguredBaseUrl(requestBaseUrl);
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "cloudnext callback base url is not configured");
        }
        return baseUrl + "/api/onlyoffice/content?previewTicket="
                + URLEncoder.encode(previewTicket, StandardCharsets.UTF_8);
    }

    private String buildOnlyofficeCallbackUrl(String editTicket) {
        String baseUrl = normalizeConfiguredBaseUrl(properties.getEndpoints().getCallbackBaseUrl());
        if (!StringUtils.hasText(baseUrl)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "cloudnext callback base url is not configured");
        }
        return baseUrl + "/api/onlyoffice/callback?editTicket="
                + URLEncoder.encode(editTicket, StandardCharsets.UTF_8);
    }

    private DrivePreviewTicketPayload buildEditTicketPayload(AuthLoginResponse session, DriveItem item) {
        DrivePreviewTicketPayload payload = new DrivePreviewTicketPayload();
        payload.setItemId(item.getId());
        payload.setOwnerPersonOaId(item.getOwnerPersonOaId());
        payload.setViewerPersonOaId(resolveViewerId(session));
        payload.setViewerPersonOaIdRaw(session.getPersonOaId());
        payload.setViewerName(resolveOwnerName(session));
        payload.setViewerIdentityIds(resolveViewerIdentityIds(session));
        payload.setViewerUnitIds(resolveViewerUnitIds(session));
        payload.setEffectivePermission(resolveOnlyofficePermission(session, item));
        payload.setAllowDownload(true);
        payload.setAllowEdit(true);
        payload.setScopeType(item.getScopeType().name());
        payload.setScopeId(item.getScopeId());
        payload.setDocumentKey(buildOnlyofficeDocumentKey(item));
        payload.setExpiresAt(Instant.now().plus(resolveOnlyofficeEditTicketTtlSeconds(), ChronoUnit.SECONDS).toEpochMilli());
        return payload;
    }

    private DriveOnlyofficeEditTicketResponse buildUnsupportedEditTicket(DriveItem item, String code, String message) {
        DriveOnlyofficeEditTicketResponse response = new DriveOnlyofficeEditTicketResponse();
        response.setItemId(item.getId());
        response.setKind(resolvePreviewKind(item));
        response.setSupported(false);
        response.setMode("edit");
        response.setFileName(item.getName());
        response.setUnavailableCode(code);
        response.setUnavailableMessage(message);
        return response;
    }

    private String buildOnlyofficeDocumentKey(DriveItem item) {
        try {
            String seed = item.getId() + "|" + normalizeSortString(item.getUpdatedAt()) + "|" + item.getSizeBytes();
            return base64Url(MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("cloudnext drive failed to build onlyoffice document key", e);
        }
    }

    private String resolveOnlyofficeDocumentType(String extension) {
        if ("xls".equals(extension) || "xlsx".equals(extension) || "csv".equals(extension)) {
            return "cell";
        }
        if ("ppt".equals(extension) || "pptx".equals(extension)) {
            return "slide";
        }
        return "word";
    }

    private String signOnlyofficeDocEditorConfig(Map<String, Object> docEditorConfig) {
        String secret = properties.getDrive().getOnlyoffice().getJwtSecret();
        if (!StringUtils.hasText(secret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "cloudnext onlyoffice jwt secret is not configured");
        }
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");
            String headerPart = base64Url(objectMapper.writeValueAsBytes(header));
            String payloadPart = base64Url(objectMapper.writeValueAsBytes(docEditorConfig));
            String signingInput = headerPart + "." + payloadPart;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = base64Url(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
            return signingInput + "." + signature;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cloudnext drive failed to serialize onlyoffice config", e);
        } catch (Exception e) {
            throw new IllegalStateException("cloudnext drive failed to sign onlyoffice config", e);
        }
    }

    private String signOnlyofficeCommandPayload(Map<String, Object> payload) {
        String secret = properties.getDrive().getOnlyoffice().getJwtSecret();
        if (!StringUtils.hasText(secret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "cloudnext onlyoffice jwt secret is not configured");
        }
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");
            String headerPart = base64Url(objectMapper.writeValueAsBytes(header));
            String payloadPart = base64Url(objectMapper.writeValueAsBytes(payload));
            String signingInput = headerPart + "." + payloadPart;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = base64Url(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
            return signingInput + "." + signature;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cloudnext drive failed to serialize onlyoffice command payload", e);
        } catch (Exception e) {
            throw new IllegalStateException("cloudnext drive failed to sign onlyoffice command payload", e);
        }
    }

    private String signOnlyofficeJwtPayload(Map<String, Object> payload) {
        return signOnlyofficeCommandPayload(payload);
    }

    private DriveOnlyofficeCallbackRequest verifyOnlyofficeCallback(
            DriveOnlyofficeCallbackRequest request,
            String authorizationHeader) {
        if (!Boolean.TRUE.equals(properties.getDrive().getOnlyoffice().getJwtEnabled())) {
            return request;
        }
        String token = firstNonBlank(request.getToken(), stripBearerToken(authorizationHeader));
        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "onlyoffice callback jwt token is missing");
        }
        Map<String, Object> claims = verifyOnlyofficeJwtToken(token);
        DriveOnlyofficeCallbackRequest verified = objectMapper.convertValue(claims, DriveOnlyofficeCallbackRequest.class);
        verified.setToken(null);
        return verified;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> verifyOnlyofficeJwtToken(String token) {
        String secret = properties.getDrive().getOnlyoffice().getJwtSecret();
        if (!StringUtils.hasText(secret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "cloudnext onlyoffice jwt secret is not configured");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "onlyoffice callback jwt token is invalid");
        }
        try {
            String signingInput = parts[0] + "." + parts[1];
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = base64Url(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "onlyoffice callback jwt signature is invalid");
            }
            return objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]),
                    Map.class);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "onlyoffice callback jwt payload is invalid", e);
        } catch (Exception e) {
            throw new IllegalStateException("cloudnext drive failed to verify onlyoffice callback jwt", e);
        }
    }

    private String stripBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return "";
        }
        String value = authorizationHeader.trim();
        if (value.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            return value.substring(7).trim();
        }
        int headerPrefix = value.indexOf(' ');
        if (headerPrefix > 0 && headerPrefix < value.length() - 1) {
            return value.substring(headerPrefix + 1).trim();
        }
        return value;
    }

    private boolean isFinalOnlyofficeSaveStatus(Integer status) {
        return Integer.valueOf(2).equals(status) || Integer.valueOf(6).equals(status);
    }

    private String buildOnlyofficeCallbackId(String editTicket, DriveOnlyofficeCallbackRequest request) {
        return editTicket + "|" + request.getKey() + "|" + request.getStatus() + "|" + request.getUrl();
    }

    private byte[] downloadOnlyofficeSavedContent(String url) {
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    byte[].class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "onlyoffice saved document is empty");
            }
            return response.getBody();
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "cloudnext could not download onlyoffice saved document", e);
        }
    }

    private String resolveOnlyofficePermission(AuthLoginResponse session, DriveItem item) {
        if (item.getScopeType() == DriveScopeType.WORKSPACE) {
            return "edit";
        }
        if (StringUtils.hasText(item.getOwnerPersonOaId()) && item.getOwnerPersonOaId().equals(session.getPersonOaId())) {
            return "edit";
        }
        return "edit";
    }

    private Integer coerceInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private long resolveOnlyofficeEditTicketTtlSeconds() {
        Integer ttl = properties.getDrive().getOnlyofficeEditTicketTtlSeconds();
        if (ttl == null || ttl <= 0) {
            return 86400L;
        }
        return ttl;
    }

    private DriveItem requireCurrentEditableItem(DrivePreviewTicketPayload payload) {
        DriveItem item = driveItemRepository.findById(payload.getItemId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "edit file not found"));
        if (item.isDeleted() || item.getItemType() != DriveItemType.FILE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "edit file not found");
        }
        if (!"office".equals(resolvePreviewKind(item))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "current file does not support onlyoffice editing");
        }
        if (!StringUtils.pathEquals(item.getOwnerPersonOaId(), payload.getOwnerPersonOaId())
                || !StringUtils.pathEquals(item.getScopeType().name(), payload.getScopeType())
                || !StringUtils.pathEquals(item.getScopeId(), payload.getScopeId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "edit file scope has changed");
        }
        requireOnlyofficeEditable(toSyntheticSession(payload), item);
        return item;
    }

    private void requireOnlyofficeEditable(AuthLoginResponse session, DriveItem item) {
        if (!canOnlyofficeEdit(session, item)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "current user cannot edit this file");
        }
    }

    private boolean canOnlyofficeEdit(AuthLoginResponse session, DriveItem item) {
        if (item == null || item.isDeleted() || item.getItemType() != DriveItemType.FILE) {
            return false;
        }
        if (item.getScopeType() == DriveScopeType.WORKSPACE) {
            return driveScopeAccessService.canActOn(session, item, DriveScopeAction.WRITE);
        }
        if (session != null && session.getPersonOaId() != null
                && session.getPersonOaId().equals(item.getOwnerPersonOaId())) {
            return true;
        }
        return driveShareService.resolveAccess(session, item)
                .map(access -> access.getEffectivePermission().satisfies(DriveSharePermission.EDIT))
                .orElse(false);
    }

    private AuthLoginResponse toSyntheticSession(DrivePreviewTicketPayload payload) {
        AuthLoginResponse session = new AuthLoginResponse();
        session.setAuthenticated(true);
        session.setPersonOaId(payload.getViewerPersonOaIdRaw());
        session.setPersonName(payload.getViewerName());
        session.setIdentityList(resolvePayloadIdentities(payload));
        return session;
    }

    private List<AuthIdentityDto> resolvePayloadIdentities(DrivePreviewTicketPayload payload) {
        List<AuthIdentityDto> identities = new ArrayList<>();
        int identityCount = payload.getViewerIdentityIds() == null ? 0 : payload.getViewerIdentityIds().size();
        int unitCount = payload.getViewerUnitIds() == null ? 0 : payload.getViewerUnitIds().size();
        int count = Math.max(identityCount, unitCount);
        for (int i = 0; i < count; i++) {
            AuthIdentityDto identity = new AuthIdentityDto();
            if (i < identityCount) {
                identity.setId(payload.getViewerIdentityIds().get(i));
            }
            if (i < unitCount) {
                identity.setUnit(payload.getViewerUnitIds().get(i));
            }
            identities.add(identity);
        }
        return identities;
    }

    private List<String> resolveViewerIdentityIds(AuthLoginResponse session) {
        if (session == null || session.getIdentityList() == null) {
            return Collections.emptyList();
        }
        return session.getIdentityList().stream()
                .filter(identity -> identity != null && StringUtils.hasText(identity.getId()))
                .map(AuthIdentityDto::getId)
                .collect(Collectors.toList());
    }

    private List<String> resolveViewerUnitIds(AuthLoginResponse session) {
        if (session == null || session.getIdentityList() == null) {
            return Collections.emptyList();
        }
        return session.getIdentityList().stream()
                .filter(identity -> identity != null && StringUtils.hasText(identity.getUnit()))
                .map(AuthIdentityDto::getUnit)
                .collect(Collectors.toList());
    }

    private void requireOnlyofficeEditEnabled() {
        if (!properties.getIteration().getOnlyofficeEdit().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "cloudnext onlyoffice edit is disabled");
        }
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return StringUtils.hasText(second) ? second : "";
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String normalizeConfiguredBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "";
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private void requireOnlyofficeDocumentServer() {
        onlyofficePreviewStatusService.requireConfigured();
    }

    private AuthLoginResponse requireSession(String token) {
        return driveAccessService.requireSession(token);
    }

    private DriveItem requireOwnedItem(String ownerPersonOaId, String itemId) {
        return driveAccessService.requireOwnedItem(ownerPersonOaId, itemId);
    }

    private String normalizeParentId(String parentId) {
        return driveAccessService.normalizeParentId(parentId);
    }

    private String normalizeRequiredName(String rawName, String emptyMessage) {
        String name = StringUtils.hasText(rawName) ? rawName.trim() : "";
        if (!StringUtils.hasText(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, emptyMessage);
        }
        if (name.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name length must be <= 255");
        }
        return name;
    }

    private String resolveOwnerName(AuthLoginResponse session) {
        return StringUtils.hasText(session.getPersonName()) ? session.getPersonName() : session.getPersonOaId();
    }

    private String resolveViewerId(AuthLoginResponse session) {
        if (StringUtils.hasText(session.getPersonDistinguishedName())) {
            return session.getPersonDistinguishedName();
        }
        return session.getPersonOaId();
    }

    /**
     * Per-{@link AccessContext} viewer id used by the OnlyOffice ticket payload.
     * For USER contexts this is identical to
     * {@link #resolveViewerId(AuthLoginResponse)} so the legacy preview flow is
     * byte-for-byte preserved. For PUBLIC_LINK contexts, the link token is used
     * as the viewer id so OnlyOffice gets a non-empty identifier and the audit
     * layer can attribute the request to the token rather than to a user.
     */
    private String resolveViewerId(AccessContext context) {
        if (context.getKind() == AccessContext.Kind.USER) {
            return resolveViewerId(context.getSession());
        }
        return context.getPublicLinkToken();
    }

    /**
     * Per-{@link AccessContext} viewer display name used by the OnlyOffice
     * ticket payload. USER contexts reuse the existing
     * {@link #resolveOwnerName(AuthLoginResponse)}. PUBLIC_LINK contexts return
     * an empty string so the OnlyOffice editor config naturally falls back to
     * {@code item.ownerName} (no external visitor name is leaked).
     */
    private String resolveViewerName(AccessContext context) {
        if (context.getKind() == AccessContext.Kind.USER) {
            return resolveOwnerName(context.getSession());
        }
        return "";
    }

    private String resolveViewerRawPersonOaId(AccessContext context) {
        if (context.getKind() == AccessContext.Kind.USER) {
            return context.getUserId();
        }
        return "";
    }

    private String generateId() {
        return UUID.randomUUID().toString();
    }

    private String normalizeSortString(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private DriveConflictStrategy normalizeConflictStrategy(DriveConflictStrategy strategy) {
        return strategy == null ? DriveConflictStrategy.ABORT : strategy;
    }

    private String resolveFileName(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        return StringUtils.hasText(originalFilename) ? originalFilename.trim() : "unnamed-file";
    }

    private String resolveFileType(String fileName, String contentType) {
        String lowerFileName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String lowerContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (lowerFileName.endsWith(".doc") || lowerFileName.endsWith(".docx")) {
            return "word";
        }
        if (lowerFileName.endsWith(".xls") || lowerFileName.endsWith(".xlsx")
                || lowerContentType.contains("spreadsheet") || lowerContentType.contains("excel")) {
            return "excel";
        }
        if (lowerFileName.endsWith(".ppt") || lowerFileName.endsWith(".pptx")
                || lowerContentType.contains("presentation") || lowerContentType.contains("powerpoint")) {
            return "ppt";
        }
        if (lowerFileName.endsWith(".pdf") || lowerContentType.contains("pdf")) {
            return "pdf";
        }
        if (lowerContentType.startsWith("video/")
                || lowerFileName.endsWith(".mp4") || lowerFileName.endsWith(".mov")
                || lowerFileName.endsWith(".avi") || lowerFileName.endsWith(".mkv")
                || lowerFileName.endsWith(".webm")) {
            return "video";
        }
        // ---- 以下分类（M7 补齐，避免图片 / 文本 / 配置 / 代码 全部归入 "file"）----
        if (lowerContentType.startsWith("image/")
                || lowerFileName.endsWith(".png") || lowerFileName.endsWith(".jpg")
                || lowerFileName.endsWith(".jpeg") || lowerFileName.endsWith(".gif")
                || lowerFileName.endsWith(".bmp") || lowerFileName.endsWith(".webp")
                || lowerFileName.endsWith(".svg") || lowerFileName.endsWith(".heic")) {
            return "image";
        }
        if (lowerContentType.startsWith("audio/")
                || lowerFileName.endsWith(".mp3") || lowerFileName.endsWith(".wav")
                || lowerFileName.endsWith(".flac") || lowerFileName.endsWith(".m4a")
                || lowerFileName.endsWith(".ogg")) {
            return "audio";
        }
        if (lowerFileName.endsWith(".zip") || lowerFileName.endsWith(".rar")
                || lowerFileName.endsWith(".7z") || lowerFileName.endsWith(".tar")
                || lowerFileName.endsWith(".gz")) {
            return "archive";
        }
        // 源代码（与 cloudnext-android FileKind.CODE 对齐）
        if (lowerFileName.endsWith(".java") || lowerFileName.endsWith(".kt")
                || lowerFileName.endsWith(".kts") || lowerFileName.endsWith(".js")
                || lowerFileName.endsWith(".ts") || lowerFileName.endsWith(".py")
                || lowerFileName.endsWith(".go") || lowerFileName.endsWith(".rs")
                || lowerFileName.endsWith(".c") || lowerFileName.endsWith(".cpp")
                || lowerFileName.endsWith(".cc") || lowerFileName.endsWith(".h")
                || lowerFileName.endsWith(".hpp") || lowerFileName.endsWith(".cs")
                || lowerFileName.endsWith(".swift") || lowerFileName.endsWith(".php")
                || lowerFileName.endsWith(".rb") || lowerFileName.endsWith(".scala")
                || lowerFileName.endsWith(".groovy") || lowerFileName.endsWith(".vue")
                || lowerFileName.endsWith(".html") || lowerFileName.endsWith(".htm")
                || lowerFileName.endsWith(".css") || lowerFileName.endsWith(".scss")
                || lowerFileName.endsWith(".less") || lowerFileName.endsWith(".json")
                || lowerFileName.endsWith(".xml") || lowerFileName.endsWith(".yml")
                || lowerFileName.endsWith(".yaml") || lowerFileName.endsWith(".sh")
                || lowerFileName.endsWith(".bash") || lowerFileName.endsWith(".zsh")
                || lowerFileName.endsWith(".ps1") || lowerFileName.endsWith(".bat")
                || lowerFileName.endsWith(".cmd") || lowerFileName.endsWith(".sql")
                || lowerFileName.endsWith(".gradle")) {
            return "code";
        }
        // 纯文本 / 配置文件
        if (lowerContentType.startsWith("text/")
                || lowerFileName.endsWith(".txt") || lowerFileName.endsWith(".md")
                || lowerFileName.endsWith(".markdown") || lowerFileName.endsWith(".log")
                || lowerFileName.endsWith(".csv") || lowerFileName.endsWith(".tsv")
                || lowerFileName.endsWith(".toml") || lowerFileName.endsWith(".ini")
                || lowerFileName.endsWith(".conf") || lowerFileName.endsWith(".properties")
                || lowerFileName.endsWith(".env") || lowerFileName.endsWith(".gitignore")
                || lowerFileName.endsWith(".gitattributes") || lowerFileName.endsWith(".editorconfig")) {
            return "text";
        }
        return "file";
    }

    private void markDeletedRecursively(String ownerPersonOaId, DriveItem item, String deletedAt) {
        markDeletedRecursivelyInScope(scopeOf(item, ownerPersonOaId), item, deletedAt);
    }

    /**
     * Scope-aware recursive soft-delete. Children are looked up via
     * {@link DriveItemRepository#findByScopeTypeAndScopeIdAndDeletedFalseAndParentId(DriveScopeType, String, String)}
     * so a delete inside a Workspace subtree never crosses into a peer
     * personal subtree (and vice versa) even though both trees may share an
     * {@code ownerPersonOaId}.
     */
    private void markDeletedRecursivelyInScope(DriveScopeContext scope, DriveItem item, String deletedAt) {
        assertNotWorkspaceRoot(item, "cannot delete workspace root");
        if (item.isDeleted()) {
            return;
        }
        item.setDeleted(true);
        item.setDeletedAt(deletedAt);
        driveItemRepository.save(item);
        if (item.getItemType() == DriveItemType.FOLDER) {
            List<DriveItem> children = findChildrenInScope(scope, item.getId(), true);
            for (DriveItem child : children) {
                markDeletedRecursivelyInScope(scope, child, deletedAt);
            }
        }
    }

    private DriveScopeContext scopeOf(DriveItem item, String fallbackOwner) {
        DriveScopeType type = item.getScopeType() == null ? DriveScopeType.PERSONAL : item.getScopeType();
        String scopeId = StringUtils.hasText(item.getScopeId()) ? item.getScopeId() : fallbackOwner;
        String ownerName = StringUtils.hasText(item.getOwnerName()) ? item.getOwnerName() : item.getOwnerPersonOaId();
        return DriveScopeContext.of(type, scopeId, item.getOwnerPersonOaId(), ownerName);
    }

    private void markRestoredRecursivelyInScope(DriveScopeContext scope, DriveItem item) {
        item.setDeleted(false);
        item.setDeletedAt("");
        driveItemRepository.save(item);
        if (item.getItemType() == DriveItemType.FOLDER) {
            for (DriveItem child : findChildrenInScope(scope, item.getId(), false)) {
                markRestoredRecursivelyInScope(scope, child);
            }
        }
    }

    private void permanentDeleteRecursively(String ownerPersonOaId, DriveItem item) {
        permanentDeleteRecursivelyInScope(scopeOf(item, ownerPersonOaId), item);
    }

    private void permanentDeleteRecursivelyInScope(DriveScopeContext scope, DriveItem item) {
        assertNotWorkspaceRoot(item, "cannot purge workspace root");
        if (item.getItemType() == DriveItemType.FOLDER) {
            for (DriveItem child : findChildrenInScope(scope, item.getId(), false)) {
                permanentDeleteRecursivelyInScope(scope, child);
            }
        }
        versionService.purge(item);
        driveItemRepository.delete(item);
    }

    private boolean isWorkspaceRoot(DriveItem item) {
        return item != null
                && item.getScopeType() == DriveScopeType.WORKSPACE
                && !StringUtils.hasText(item.getParentId());
    }

    private void assertNotWorkspaceRoot(DriveItem item, String message) {
        if (isWorkspaceRoot(item)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private boolean isExpiredDeletedItem(DriveItem item, Instant cutoff) {
        if (!item.isDeleted() || !StringUtils.hasText(item.getDeletedAt())) {
            return false;
        }
        try {
            return Instant.parse(item.getDeletedAt()).isBefore(cutoff);
        } catch (Exception e) {
            return false;
        }
    }

    private DriveDownloadPayload loadOwnedFilePayload(String ownerPersonOaId, String itemId, boolean touchLastAccessed) {
        DriveItem item = requireOwnedItem(ownerPersonOaId, itemId);
        return loadFilePayload(item, touchLastAccessed);
    }

    private DriveDownloadPayload loadFilePayload(DriveItem item, boolean touchLastAccessed) {
        if (item.getItemType() != DriveItemType.FILE || item.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "file not found");
        }
        Resource resource = driveStorageService.load(item.getStorageKey());
        if (resource == null || !resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "file content not found");
        }
        if (touchLastAccessed) {
            touchLastAccessed(item);
        }
        return new DriveDownloadPayload(item.getName(), item.getContentType(), resource);
    }

    private void touchLastAccessed(DriveItem item) {
        if (item == null || !StringUtils.hasText(item.getId())) {
            return;
        }
        driveItemRepository.updateLastAccessedAt(item.getId(), Instant.now().toString());
    }

    private void deleteStorageIfChanged(String previousStorageKey, String currentStorageKey) {
        if (!StringUtils.hasText(previousStorageKey)) {
            return;
        }
        if (StringUtils.pathEquals(previousStorageKey, currentStorageKey)) {
            return;
        }
        driveStorageService.delete(previousStorageKey);
    }

    public static class DriveDownloadPayload {
        private final String fileName;
        private final String contentType;
        private final Resource resource;

        public DriveDownloadPayload(String fileName, String contentType, Resource resource) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.resource = resource;
        }

        public String getFileName() {
            return fileName;
        }

        public String getContentType() {
            return contentType;
        }

        public Resource getResource() {
            return resource;
        }
    }
}
