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
import java.util.stream.Collectors;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.x.cloudnext.server.audit.AuditAction;
import com.x.cloudnext.server.audit.AuditLogService;
import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.auth.AuthIdentityDto;
import com.x.cloudnext.server.config.CloudNextProperties;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;

@Service
public class DriveService {
    @org.springframework.beans.factory.annotation.Autowired private com.x.cloudnext.server.namespace.DriveNamespaceService namespaces;
    @org.springframework.beans.factory.annotation.Autowired
    private com.x.cloudnext.server.drive.next.DriveVersionService versionService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.x.cloudnext.server.drive.next.DriveExperienceService experienceService;

    private static final Logger LOGGER = LoggerFactory.getLogger(DriveService.class);

    private final DriveItemRepository driveItemRepository;
    private final DriveStorageService driveStorageService;
    private final DriveAccessService driveAccessService;
    private final DriveScopeAccessService driveScopeAccessService;
    private final DriveItemResponseAssembler driveItemResponseAssembler;
    private final DriveReadService driveReadService;
    private final DriveShareService driveShareService;
    private final CloudNextProperties properties;
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy
    private com.x.cloudnext.server.preview.DriveFilePreviewService previews;
    private final AuditLogService auditLogService;

    public DriveService(
            DriveItemRepository driveItemRepository,
            DriveStorageService driveStorageService,
            DriveAccessService driveAccessService,
            DriveScopeAccessService driveScopeAccessService,
            DriveItemResponseAssembler driveItemResponseAssembler,
            DriveReadService driveReadService,
            DriveShareService driveShareService,
            CloudNextProperties properties,
            AuditLogService auditLogService) {
        this.driveItemRepository = driveItemRepository;
        this.driveStorageService = driveStorageService;
        this.driveAccessService = driveAccessService;
        this.driveScopeAccessService = driveScopeAccessService;
        this.driveItemResponseAssembler = driveItemResponseAssembler;
        this.driveReadService = driveReadService;
        this.driveShareService = driveShareService;
        this.properties = properties;

        this.auditLogService = auditLogService;
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

    /** Ordinary item/share authorization stays in getPreview; derivatives use the same subject. */
    public DrivePreviewResponse buildPreviewForItem(DriveItem item, AccessContext context) {
        return previews.describe(item, context, null, null);
    }

    @Transactional
    public DriveDownloadPayload downloadItem(String token, String itemId) {
        long t0 = System.nanoTime();
        AuthLoginResponse session = requireSession(token);
        DriveItem item = requireDownloadItem(token, itemId);
        auditLogService.record(session.getPersonOaId(), AuditAction.DOWNLOAD, "DRIVE_ITEM", item.getId(),
                "name", item.getName(), "sizeBytes", item.getSizeBytes(), "contentType", item.getContentType());
        DriveDownloadPayload payload = loadFilePayload(item, true);
        logTiming("download", item.getId(), t0, item.getSizeBytes());
        return payload;
    }

    @Transactional(readOnly = true)
    public DriveItem requireDownloadItem(String token, String itemId) {
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
        return item;
    }

    @Transactional
    public DriveDownloadPayload contentItem(String token, String itemId) {
        long t0 = System.nanoTime();
        AuthLoginResponse session = requireSession(token);
        // Scope-aware：preview/inline content 走 READ 矩阵 — workspace 任何成员都允许；
        // personal owner / active share 都允许。修复协作空间成员预览别人文件被 403。
        DriveItem item = driveScopeAccessService.requireItem(session, itemId, DriveScopeAction.READ);
        if ("office".equals(com.x.cloudnext.server.preview.PreviewSource.kind(item.getName(), item.getContentType()))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请通过文件预览查看。");
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
            if (!namespaces.maintenanceAllowed()) break;
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

    private AuthLoginResponse requireSession(String token) {
        return driveAccessService.requireSession(token);
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
