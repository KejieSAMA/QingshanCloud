package com.x.cloudnext.server.drive;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.x.cloudnext.server.audit.AuditAction;
import com.x.cloudnext.server.audit.AuditLogService;
import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;
import com.x.cloudnext.server.workspace.entity.Workspace;
import com.x.cloudnext.server.workspace.repository.WorkspaceRepository;

@Service
public class DriveCopyService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.x.cloudnext.server.drive.next.DriveVersionService versionService;
    private static final int MAX_TOP_LEVEL_ITEMS = 100;

    private final DriveItemRepository itemRepository;
    private final DriveStorageService storageService;
    private final DriveAccessService driveAccessService;
    private final DriveScopeAccessService scopeAccessService;
    private final DriveShareService shareService;
    private final WorkspaceRepository workspaceRepository;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;

    public DriveCopyService(
            DriveItemRepository itemRepository,
            DriveStorageService storageService,
            DriveAccessService driveAccessService,
            DriveScopeAccessService scopeAccessService,
            DriveShareService shareService,
            WorkspaceRepository workspaceRepository,
            AuditLogService auditLogService,
            PlatformTransactionManager transactionManager) {
        this.itemRepository = itemRepository;
        this.storageService = storageService;
        this.driveAccessService = driveAccessService;
        this.scopeAccessService = scopeAccessService;
        this.shareService = shareService;
        this.workspaceRepository = workspaceRepository;
        this.auditLogService = auditLogService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public DriveCopyItemsResponse copy(String token, DriveCopyItemsRequest request) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        List<String> requestedIds = normalizeIds(request == null ? null : request.getItemIds());
        if (requestedIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itemIds is required");
        }
        if (requestedIds.size() > MAX_TOP_LEVEL_ITEMS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at most 100 top-level items may be copied");
        }
        Target target = resolveTarget(session, request);
        List<String> rootIds = removeSelectedDescendants(requestedIds);
        DriveConflictStrategy strategy = request.getConflictStrategy() == null
                ? DriveConflictStrategy.KEEP_BOTH : request.getConflictStrategy();

        DriveCopyItemsResponse response = new DriveCopyItemsResponse();
        response.setTotalRequested(rootIds.size());
        for (String sourceId : rootIds) {
            CopyAttempt attempt = new CopyAttempt();
            try {
                String createdId = transactionTemplate.execute(status -> copyTopLevel(
                        session, sourceId, target, strategy, attempt));
                response.getSucceededIds().add(sourceId);
                response.getCreatedIdsBySource().put(sourceId, createdId);
            } catch (RuntimeException failure) {
                cleanupStorage(attempt.createdStorageKeys);
                response.getFailedItems().put(sourceId, messageOf(failure));
            }
        }
        response.setTotalSucceeded(response.getSucceededIds().size());
        response.setTotalFailed(response.getFailedItems().size());
        response.setMessage("copy completed: " + response.getTotalSucceeded() + " succeeded, "
                + response.getTotalFailed() + " failed");
        return response;
    }

    private String copyTopLevel(
            AuthLoginResponse session,
            String sourceId,
            Target target,
            DriveConflictStrategy strategy,
            CopyAttempt attempt) {
        DriveItem source = itemRepository.findById(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found"));
        requireCopySource(session, source);
        assertTargetOutsideSource(source, target);
        DriveItem created = copyRecursive(session, source, target.scopeType, target.scopeId,
                target.parentId, strategy, attempt, true);
        auditLogService.record(
                session.getPersonOaId(), AuditAction.COPY, "DRIVE_ITEM", source.getId(),
                "targetScopeType", target.scopeType.name(),
                "targetScopeId", target.scopeId,
                "targetParentId", target.parentId,
                "newRootItemId", created.getId());
        return created.getId();
    }

    private DriveItem copyRecursive(
            AuthLoginResponse session,
            DriveItem source,
            DriveScopeType targetScopeType,
            String targetScopeId,
            String targetParentId,
            DriveConflictStrategy strategy,
            CopyAttempt attempt,
            boolean topLevel) {
        List<DriveItem> conflicts = itemRepository
                .findByScopeTypeAndScopeIdAndDeletedFalseAndParentIdAndName(
                        targetScopeType, targetScopeId, targetParentId, source.getName());
        String targetName = source.getName();
        if (!conflicts.isEmpty()) {
            if (strategy == DriveConflictStrategy.ABORT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "target already contains an item with the same name");
            }
            if (strategy == DriveConflictStrategy.KEEP_BOTH) {
                targetName = availableCopyName(targetScopeType, targetScopeId, targetParentId, source.getName());
            } else if (sameItem(conflicts, source)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "source cannot replace itself");
            }
        }

        DriveItem created = new DriveItem();
        created.setId(UUID.randomUUID().toString());
        created.setParentId(targetParentId);
        created.setOwnerPersonOaId(session.getPersonOaId());
        created.setOwnerName(resolveActorName(session));
        created.setName(targetName);
        created.setItemType(source.getItemType());
        created.setFileType(source.getFileType());
        created.setContentType(source.getContentType());
        created.setSizeBytes(source.getSizeBytes());
        created.setStorageKey("");
        created.setScopeType(targetScopeType);
        created.setScopeId(targetScopeId);
        created.setStorageBackend(source.getStorageBackend());
        created.setDeleted(false);
        created.setDeletedAt("");
        created.setLastAccessedAt("");

        if (source.getItemType() == DriveItemType.FILE) {
            DriveStoredFile stored = storageService.copy(
                    source.getStorageKey(), created.getId(), targetName, source.getContentType());
            attempt.createdStorageKeys.add(stored.getStorageKey());
            created.setStorageKey(stored.getStorageKey());
            created.setSizeBytes(stored.getSizeBytes());
            created.setContentType(stored.getContentType());
        }
        itemRepository.save(created);
        versionService.recordCopy(created, session.getPersonOaId(), resolveActorName(session));

        if (source.getItemType() == DriveItemType.FOLDER) {
            List<DriveItem> children = itemRepository.findByScopeTypeAndScopeIdAndDeletedFalseAndParentId(
                    source.getScopeType(), source.getScopeId(), source.getId());
            children.sort((left, right) -> NaturalOrder.compare(left.getName(), right.getName()));
            for (DriveItem child : children) {
                copyRecursive(session, child, targetScopeType, targetScopeId, created.getId(), strategy, attempt, false);
            }
        }

        if (topLevel && strategy == DriveConflictStrategy.REPLACE) {
            for (DriveItem conflict : conflicts) {
                markDeletedRecursively(conflict, Instant.now().toString());
            }
        }
        return created;
    }

    private Target resolveTarget(AuthLoginResponse session, DriveCopyItemsRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "copy request is required");
        }
        String rawScope = StringUtils.hasText(request.getTargetScopeType())
                ? request.getTargetScopeType().trim().toUpperCase(Locale.ROOT) : "PERSONAL";
        DriveScopeType scopeType;
        try {
            scopeType = DriveScopeType.valueOf(rawScope);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported target scope");
        }
        String scopeId;
        String parentId = trim(request.getTargetParentId());
        if (scopeType == DriveScopeType.PERSONAL) {
            scopeId = session.getPersonOaId();
            if (StringUtils.hasText(request.getTargetScopeId())
                    && !scopeId.equals(request.getTargetScopeId().trim())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "personal target must belong to current user");
            }
        } else {
            scopeId = trim(request.getTargetScopeId());
            Workspace workspace = workspaceRepository.findById(scopeId)
                    .filter(value -> Workspace.STATUS_ACTIVE.equals(value.getStatus()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "workspace not found"));
            WorkspaceRole role = scopeAccessService.resolveWorkspaceRole(session, scopeId);
            if (role == WorkspaceRole.VIEWER || role == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "workspace write denied");
            }
            if (!StringUtils.hasText(parentId)) {
                parentId = workspace.getRootItemId();
            }
        }
        if (StringUtils.hasText(parentId)) {
            DriveItem parent = itemRepository.findById(parentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "target folder is invalid"));
            if (parent.isDeleted() || parent.getItemType() != DriveItemType.FOLDER
                    || parent.getScopeType() != scopeType || !scopeId.equals(parent.getScopeId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "target folder is invalid");
            }
            assertActiveAncestry(parent, HttpStatus.BAD_REQUEST, "target folder is invalid");
        }
        return new Target(scopeType, scopeId, parentId);
    }

    private void requireCopySource(AuthLoginResponse session, DriveItem item) {
        if (item.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found");
        }
        assertActiveAncestry(item, HttpStatus.NOT_FOUND, "item not found");
        if (item.getScopeType() == DriveScopeType.WORKSPACE) {
            if (!scopeAccessService.canActOn(session, item, DriveScopeAction.WRITE)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "workspace copy denied");
            }
            return;
        }
        if (session.getPersonOaId().equals(item.getOwnerPersonOaId())) {
            return;
        }
        Optional<DriveResolvedShareAccess> access = shareService.resolveAccess(session, item);
        if (!access.isPresent() || !access.get().allowsDownload()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "shared item does not allow copying");
        }
    }

    private void assertActiveAncestry(DriveItem item, HttpStatus status, String message) {
        DriveItem current = item;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && StringUtils.hasText(current.getParentId())) {
            if (!visited.add(current.getId())) {
                throw new ResponseStatusException(status, message);
            }
            DriveItem parent = itemRepository.findById(current.getParentId()).orElse(null);
            if (parent == null || parent.isDeleted() || parent.getItemType() != DriveItemType.FOLDER
                    || parent.getScopeType() != item.getScopeType()
                    || !item.getScopeId().equals(parent.getScopeId())) {
                throw new ResponseStatusException(status, message);
            }
            current = parent;
        }
    }

    private void assertTargetOutsideSource(DriveItem source, Target target) {
        if (source.getItemType() != DriveItemType.FOLDER
                || source.getScopeType() != target.scopeType
                || !source.getScopeId().equals(target.scopeId)) {
            return;
        }
        String currentId = target.parentId;
        Set<String> visited = new LinkedHashSet<>();
        while (StringUtils.hasText(currentId) && visited.add(currentId)) {
            if (source.getId().equals(currentId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "folder cannot be copied into itself or its descendant");
            }
            DriveItem current = itemRepository.findById(currentId).orElse(null);
            currentId = current == null ? "" : current.getParentId();
        }
    }

    private List<String> removeSelectedDescendants(List<String> ids) {
        Set<String> selected = new LinkedHashSet<>(ids);
        List<String> roots = new ArrayList<>();
        for (String id : ids) {
            DriveItem current = itemRepository.findById(id).orElse(null);
            boolean nested = false;
            Set<String> visited = new LinkedHashSet<>();
            while (current != null && StringUtils.hasText(current.getParentId()) && visited.add(current.getParentId())) {
                if (selected.contains(current.getParentId())) {
                    nested = true;
                    break;
                }
                current = itemRepository.findById(current.getParentId()).orElse(null);
            }
            if (!nested) {
                roots.add(id);
            }
        }
        return roots;
    }

    private String availableCopyName(DriveScopeType scopeType, String scopeId, String parentId, String original) {
        String stem = original;
        String extension = "";
        int dot = original.lastIndexOf('.');
        if (dot > 0) {
            stem = original.substring(0, dot);
            extension = original.substring(dot);
        }
        int suffix = 1;
        while (suffix < 10000) {
            String candidate = stem + " (" + suffix + ")" + extension;
            if (itemRepository.findByScopeTypeAndScopeIdAndDeletedFalseAndParentIdAndName(
                    scopeType, scopeId, parentId, candidate).isEmpty()) {
                return candidate;
            }
            suffix++;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "unable to allocate a duplicate name");
    }

    private void markDeletedRecursively(DriveItem item, String deletedAt) {
        item.setDeleted(true);
        item.setDeletedAt(deletedAt);
        itemRepository.save(item);
        if (item.getItemType() == DriveItemType.FOLDER) {
            List<DriveItem> children = itemRepository.findByScopeTypeAndScopeIdAndDeletedFalseAndParentId(
                    item.getScopeType(), item.getScopeId(), item.getId());
            for (DriveItem child : children) {
                markDeletedRecursively(child, deletedAt);
            }
        }
    }

    private boolean sameItem(List<DriveItem> conflicts, DriveItem source) {
        return conflicts.stream().anyMatch(item -> item.getId().equals(source.getId()));
    }

    private List<String> normalizeIds(List<String> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                ids.add(value.trim());
            }
        }
        return new ArrayList<>(ids);
    }

    private void cleanupStorage(List<String> storageKeys) {
        for (String key : storageKeys) {
            try {
                storageService.delete(key);
            } catch (RuntimeException cleanupFailure) {
                // The failed key stays visible to storage inventory diagnostics for operator cleanup.
            }
        }
    }

    private String resolveActorName(AuthLoginResponse session) {
        return StringUtils.hasText(session.getPersonName()) ? session.getPersonName() : session.getPersonOaId();
    }

    private String messageOf(RuntimeException failure) {
        if (failure instanceof ResponseStatusException) {
            String reason = ((ResponseStatusException) failure).getReason();
            if (StringUtils.hasText(reason)) {
                return reason;
            }
        }
        return StringUtils.hasText(failure.getMessage()) ? failure.getMessage() : "copy failed";
    }

    private String trim(String value) { return value == null ? "" : value.trim(); }

    private static final class Target {
        private final DriveScopeType scopeType;
        private final String scopeId;
        private final String parentId;
        private Target(DriveScopeType scopeType, String scopeId, String parentId) {
            this.scopeType = scopeType;
            this.scopeId = scopeId;
            this.parentId = parentId;
        }
    }

    private static final class CopyAttempt {
        private final List<String> createdStorageKeys = new ArrayList<>();
    }
}
