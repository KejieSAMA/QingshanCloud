package com.x.cloudnext.server.drive;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.x.cloudnext.server.audit.AuditAction;
import com.x.cloudnext.server.audit.AuditLogService;
import com.x.cloudnext.server.auth.AuthIdentityDto;
import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.entity.DriveShare;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;
import com.x.cloudnext.server.drive.repository.DriveShareRepository;
import com.x.cloudnext.server.form.CanonicalAliasResolver;
import com.x.cloudnext.server.form.CanonicalTargetValidationService;

@Service
public class DriveShareService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int[] ALLOWED_PAGE_SIZES = new int[] { 20, 50, 100 };

    private final DriveAccessService driveAccessService;
    private final DriveScopeAccessService driveScopeAccessService;
    private final DriveItemRepository driveItemRepository;
    private final DriveShareRepository driveShareRepository;
    private final DriveItemResponseAssembler driveItemResponseAssembler;
    private final AuditLogService auditLogService;
    private final CanonicalAliasResolver canonicalAliasResolver;
    private final CanonicalTargetValidationService targetValidationService;

    public DriveShareService(
            DriveAccessService driveAccessService,
            DriveScopeAccessService driveScopeAccessService,
            DriveItemRepository driveItemRepository,
            DriveShareRepository driveShareRepository,
            DriveItemResponseAssembler driveItemResponseAssembler,
            AuditLogService auditLogService,
            CanonicalAliasResolver canonicalAliasResolver,
            CanonicalTargetValidationService targetValidationService) {
        this.driveAccessService = driveAccessService;
        this.driveScopeAccessService = driveScopeAccessService;
        this.driveItemRepository = driveItemRepository;
        this.driveShareRepository = driveShareRepository;
        this.driveItemResponseAssembler = driveItemResponseAssembler;
        this.auditLogService = auditLogService;
        this.canonicalAliasResolver = canonicalAliasResolver;
        this.targetValidationService = targetValidationService;
    }

    @Transactional
    public DriveBatchOperationResponse createShares(String token, DriveCreateShareRequest request) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        List<String> itemIds = normalizeItemIds(request);
        if (itemIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shared items are required");
        }
        List<DriveShareTargetRequest> targets = request == null ? Collections.emptyList() : request.getTargets();
        if (targets == null || targets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "share targets are required");
        }

        DriveSharePermission permission = DriveSharePermission.fromValue(request == null ? "" : request.getPermission());
        String expiresAt = normalizeExpiresAt(request == null ? "" : request.getExpiresAt());
        boolean canReshare = request != null && request.isCanReshare();

        DriveBatchOperationResponse response = new DriveBatchOperationResponse();
        response.setTotalRequested(itemIds.size() * targets.size());

        for (String itemId : itemIds) {
            DriveItem item = driveScopeAccessService.requireItem(session, itemId, DriveScopeAction.MANAGE_SHARE);
            if (item.isDeleted()) {
                response.getFailedItems().put(itemId, "deleted item cannot be shared");
                continue;
            }
            if (isWorkspaceRoot(item)) {
                response.getFailedItems().put(itemId, "cannot share workspace root");
                continue;
            }
            for (DriveShareTargetRequest target : targets) {
                try {
                    persistShare(session, item, target, permission, expiresAt, canReshare);
                    response.setTotalSucceeded(response.getTotalSucceeded() + 1);
                    response.getSucceededIds().add(itemId + ":" + normalizeTargetKey(target));
                } catch (ResponseStatusException e) {
                    response.getFailedItems().put(itemId + ":" + normalizeTargetKey(target), e.getReason());
                }
            }
        }

        response.setTotalFailed(response.getFailedItems().size());
        response.setMessage(buildSummaryMessage(response, "create shares"));
        if (response.getTotalSucceeded() > 0) {
            auditLogService.record(
                    session.getPersonOaId(),
                    AuditAction.SHARE_CREATE,
                    "DRIVE_SHARE",
                    itemIds.get(0),
                    "itemCount", itemIds.size(),
                    "targetCount", targets.size(),
                    "succeeded", response.getTotalSucceeded(),
                    "failed", response.getTotalFailed(),
                    "permission", permission == null ? "" : permission.name(),
                    "expiresAt", expiresAt);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public DriveSharePageResponse queryOutgoingShares(String token, DriveShareQueryRequest request) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        DriveShareQueryRequest safeRequest = request == null ? new DriveShareQueryRequest() : request;
        List<DriveShareResponse> items = driveShareRepository.findByOwnerPersonOaIdOrderByUpdatedAtDesc(session.getPersonOaId())
                .stream()
                .map(this::toOutgoingResponse)
                .filter(item -> matchesKeyword(item, safeRequest.getKeyword()))
                .filter(item -> matchesPermission(item, safeRequest.getPermission()))
                .filter(item -> matchesStatus(item, safeRequest.getStatus()))
                .sorted(Comparator.comparing(DriveShareResponse::getUpdatedAt, Comparator.nullsLast(String::compareTo)).reversed())
                .collect(Collectors.toList());
        return pageShares(items, safeRequest);
    }

    @Transactional(readOnly = true)
    public DriveShareSummaryResponse getShareSummary(String token, String itemId) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        DriveItem item = driveScopeAccessService.requireItem(session, itemId, DriveScopeAction.MANAGE_SHARE);
        List<DriveShare> shares = listManageableShares(session, item);
        DriveShareSummaryResponse response = new DriveShareSummaryResponse();
        response.setItemId(item.getId());
        response.setItemName(item.getName());
        response.setItemType(item.getItemType().name().toLowerCase(Locale.ROOT));
        response.setOwnerName(item.getOwnerName());
        response.setTargetCount(shares.size());

        List<DriveShareCollaboratorSummaryResponse> collaborators = shares.stream()
                .map(this::toCollaboratorSummary)
                .collect(Collectors.toList());
        response.setCollaborators(collaborators);
        response.setActiveTargetCount((int) collaborators.stream().filter(summary -> "active".equals(summary.getStatus())).count());
        response.setShared(response.getActiveTargetCount() > 0);
        return response;
    }

    @Transactional(readOnly = true)
    public DriveSharePageResponse queryIncomingShares(String token, DriveShareQueryRequest request) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        DriveShareQueryRequest safeRequest = request == null ? new DriveShareQueryRequest() : request;
        String shareId = trimToEmpty(safeRequest.getShareId());
        String parentId = trimToEmpty(safeRequest.getParentId());
        if (!shareId.isEmpty()) {
            DriveShare share = requireIncomingRootShare(session, shareId);
            return pageShares(listIncomingChildren(session, share, parentId, safeRequest), safeRequest);
        }
        List<DriveShareResponse> items = listIncomingRootShares(session).stream()
                .filter(item -> matchesKeyword(item, safeRequest.getKeyword()))
                .filter(item -> matchesPermission(item, safeRequest.getPermission()))
                .sorted(Comparator.comparing(DriveShareResponse::getUpdatedAt, Comparator.nullsLast(String::compareTo)).reversed())
                .collect(Collectors.toList());
        return pageShares(items, safeRequest);
    }

    @Transactional(readOnly = true)
    public List<DrivePathNodeResponse> getIncomingPath(String token, String shareId, String itemId) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        DriveShare share = requireIncomingRootShare(session, shareId);
        DriveItem rootItem = requireItem(share.getItemId());
        if (rootItem.getItemType() != DriveItemType.FOLDER) {
            return Collections.singletonList(new DrivePathNodeResponse(rootItem.getId(), rootItem.getName()));
        }
        String normalizedItemId = trimToEmpty(itemId);
        DriveItem current = normalizedItemId.isEmpty() ? rootItem : requireItem(normalizedItemId);
        if (!isDescendantOrSelf(rootItem, current)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requested item is outside the shared folder");
        }
        List<DrivePathNodeResponse> path = new ArrayList<>();
        while (current != null) {
            path.add(new DrivePathNodeResponse(current.getId(), current.getName()));
            if (current.getId().equals(rootItem.getId())) {
                break;
            }
            if (!StringUtils.hasText(current.getParentId())) {
                break;
            }
            current = findItemInShareScope(scopeOf(rootItem), current.getParentId()).orElse(null);
        }
        Collections.reverse(path);
        return path;
    }

    @Transactional
    public DriveShareResponse updateShare(String token, String shareId, DriveUpdateShareRequest request) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        DriveShare share = requireManageableShare(session, shareId);
        if (StringUtils.hasText(request == null ? "" : request.getPermission())) {
            share.setPermission(DriveSharePermission.fromValue(request.getPermission()));
        }
        share.setExpiresAt(normalizeExpiresAt(request == null ? "" : request.getExpiresAt()));
        if (request != null && request.getCanReshare() != null) {
            share.setCanReshare(request.getCanReshare());
        }
        share.setRevoked(false);
        DriveShare saved = driveShareRepository.save(share);
        auditLogService.record(
                session.getPersonOaId(),
                AuditAction.SHARE_UPDATE,
                "DRIVE_SHARE",
                saved.getId(),
                "itemId", saved.getItemId(),
                "permission", saved.getPermission() == null ? "" : saved.getPermission().name(),
                "expiresAt", saved.getExpiresAt());
        return toOutgoingResponse(saved);
    }

    @Transactional
    public DriveShareResponse revokeShare(String token, String shareId) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        DriveShare share = requireManageableShare(session, shareId);
        share.setRevoked(true);
        DriveShare saved = driveShareRepository.save(share);
        auditLogService.record(
                session.getPersonOaId(),
                AuditAction.SHARE_REVOKE,
                "DRIVE_SHARE",
                saved.getId(),
                "itemId", saved.getItemId(),
                "targetType", saved.getTargetType() == null ? "" : saved.getTargetType().name(),
                "targetId", saved.getTargetId());
        return toOutgoingResponse(saved);
    }

    @Transactional
    public DriveBatchOperationResponse revokeShares(String token, DriveBatchIdsRequest request) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        List<String> ids = normalizeIds(request == null ? null : request.getItemIds());
        DriveBatchOperationResponse response = new DriveBatchOperationResponse();
        response.setTotalRequested(ids.size());
        if (ids.isEmpty()) {
            response.setMessage("no share selected");
            return response;
        }
        Map<String, DriveShare> shares = driveShareRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(DriveShare::getId, share -> share, (left, right) -> left, LinkedHashMap::new));
        for (String id : ids) {
            DriveShare share = shares.get(id);
            if (share == null || !canManageShare(session, share)) {
                response.getFailedItems().put(id, "share not found");
                continue;
            }
            share.setRevoked(true);
            driveShareRepository.save(share);
            response.setTotalSucceeded(response.getTotalSucceeded() + 1);
            response.getSucceededIds().add(id);
        }
        response.setTotalFailed(response.getFailedItems().size());
        response.setMessage(buildSummaryMessage(response, "revoke shares"));
        if (response.getTotalSucceeded() > 0) {
            auditLogService.record(
                    session.getPersonOaId(),
                    AuditAction.SHARE_REVOKE,
                    "DRIVE_SHARE",
                    response.getSucceededIds().isEmpty() ? "batch" : response.getSucceededIds().get(0),
                    "shareCount", response.getTotalSucceeded(),
                    "failed", response.getTotalFailed(),
                    "batch", true);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public DriveResolvedShareAccess requireAccess(AuthLoginResponse session, String itemId, DriveSharePermission minimumPermission) {
        DriveItem item = requireItem(itemId);
        if (session.getPersonOaId().equals(item.getOwnerPersonOaId())) {
            return DriveResolvedShareAccess.owner(item);
        }
        if (item.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found");
        }
        DriveResolvedShareAccess access = resolveAccess(session, item)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "item is not shared with the current user"));
        if (!access.getEffectivePermission().satisfies(minimumPermission)) {
            String reason = minimumPermission == DriveSharePermission.DOWNLOAD
                    ? "shared item does not allow download"
                    : "item is not shared with the current user";
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
        }
        return access;
    }

    @Transactional(readOnly = true)
    public Optional<DriveResolvedShareAccess> resolveAccess(AuthLoginResponse session, DriveItem item) {
        if (item == null || item.isDeleted()) {
            return Optional.empty();
        }
        if (session.getPersonOaId().equals(item.getOwnerPersonOaId())) {
            return Optional.of(DriveResolvedShareAccess.owner(item));
        }
        return findActiveReadableShare(session, item);
    }

    @Transactional(readOnly = true)
    public Optional<DriveShareResponse> findIncomingRootShareForItem(AuthLoginResponse session, String itemId) {
        return listIncomingRootShares(session).stream()
                .filter(share -> share.getItem() != null && itemId.equals(share.getItem().getId()))
                .findFirst();
    }

    private List<DriveShareResponse> listIncomingRootShares(AuthLoginResponse session) {
        Map<String, DriveShare> bestShares = new LinkedHashMap<>();
        for (DriveShare share : loadMatchedShares(session)) {
            if (!"active".equals(resolveOutgoingStatus(share))) {
                continue;
            }
            DriveShare current = bestShares.get(share.getItemId());
            if (current == null || isHigherPermission(share.getPermission(), current.getPermission())) {
                bestShares.put(share.getItemId(), share);
            }
        }
        return bestShares.values().stream()
                .map(share -> {
                    DriveItem item = requireItem(share.getItemId());
                    DriveResolvedShareAccess access = resolveAccess(session, item).orElse(null);
                    if (access == null) {
                        return null;
                    }
                    DriveShareResponse response = toIncomingShareResponse(share, access);
                    response.setShareId(share.getId());
                    return response;
                })
                .filter(item -> item != null)
                .collect(Collectors.toList());
    }

    private List<DriveShareResponse> listIncomingChildren(
            AuthLoginResponse session,
            DriveShare share,
            String parentId,
            DriveShareQueryRequest request) {
        DriveItem rootItem = requireItem(share.getItemId());
        String browseParentId = StringUtils.hasText(parentId) ? parentId : rootItem.getId();
        DriveItem browseParent = requireItem(browseParentId);
        if (browseParent.isDeleted() || browseParent.getItemType() != DriveItemType.FOLDER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shared folder is invalid");
        }
        if (!isDescendantOrSelf(rootItem, browseParent)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requested folder is outside the shared root");
        }
        return findActiveChildrenInShareScope(scopeOf(rootItem), browseParent.getId())
                .stream()
                .map(item -> {
                    DriveResolvedShareAccess access = resolveAccess(session, item).orElse(null);
                    if (access == null) {
                        return null;
                    }
                    DriveShareResponse response = toIncomingShareResponse(share, access);
                    response.setShareId(share.getId());
                    return response;
                })
                .filter(item -> item != null)
                .filter(item -> matchesKeyword(item, request.getKeyword()))
                .sorted(
                        Comparator.comparing((DriveShareResponse item) -> !"folder".equals(item.getItem().getItemType()))
                                .thenComparing(
                                        DriveShareResponse::getUpdatedAt,
                                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    private void persistShare(
            AuthLoginResponse session,
            DriveItem item,
            DriveShareTargetRequest target,
            DriveSharePermission permission,
            String expiresAt,
            boolean canReshare) {
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "share target is invalid");
        }
        DriveShareTargetType targetType = DriveShareTargetType.fromValue(target.getType());
        String targetId = trimToEmpty(target.getId());
        String targetName = trimToEmpty(target.getName());
        if (targetId.isEmpty() || targetName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "share target is invalid");
        }
        targetValidationService.requireResolved(targetType,targetId);
        String ownerPersonOaId = shareOwnerPersonOaId(session, item);
        String ownerName = shareOwnerName(session, item);
        DriveShare share = driveShareRepository
                .findByItemIdAndOwnerPersonOaIdAndTargetTypeAndTargetId(
                        item.getId(),
                        ownerPersonOaId,
                        targetType,
                        targetId)
                .orElseGet(() -> {
                    DriveShare created = new DriveShare();
                    created.setId(UUID.randomUUID().toString());
                    created.setItemId(item.getId());
                    created.setOwnerPersonOaId(ownerPersonOaId);
                    created.setOwnerName(ownerName);
                    created.setTargetType(targetType);
                    created.setTargetId(targetId);
                    return created;
                });
        share.setTargetName(targetName);
        share.setPermission(permission);
        share.setExpiresAt(expiresAt);
        share.setRevoked(false);
        share.setCanReshare(canReshare);
        driveShareRepository.save(share);
    }

    private DriveShare requireManageableShare(AuthLoginResponse session, String shareId) {
        DriveShare share = driveShareRepository.findById(shareId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "share not found"));
        if (!canManageShare(session, share)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "share not found");
        }
        return share;
    }

    private boolean canManageShare(AuthLoginResponse session, DriveShare share) {
        if (session == null || share == null) {
            return false;
        }
        DriveItem item = driveItemRepository.findById(share.getItemId()).orElse(null);
        if (item == null) {
            return session.getPersonOaId().equals(share.getOwnerPersonOaId());
        }
        if (item.getScopeType() == DriveScopeType.WORKSPACE) {
            WorkspaceRole role = driveScopeAccessService.resolveWorkspaceRole(session, item.getScopeId());
            if (role == WorkspaceRole.OWNER || role == WorkspaceRole.ADMIN) {
                return true;
            }
            return session.getPersonOaId().equals(share.getOwnerPersonOaId())
                    && driveScopeAccessService.canActOn(session, item, DriveScopeAction.MANAGE_SHARE);
        }
        return session.getPersonOaId().equals(share.getOwnerPersonOaId())
                && driveScopeAccessService.canActOn(session, item, DriveScopeAction.MANAGE_SHARE);
    }

    private DriveShare requireIncomingRootShare(AuthLoginResponse session, String shareId) {
        DriveShare share = driveShareRepository.findById(shareId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "share not found"));
        if (!matchesSession(share, session)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "share not found");
        }
        if (!"active".equals(resolveOutgoingStatus(share))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "share is unavailable");
        }
        DriveItem rootItem = requireItem(share.getItemId());
        if (rootItem.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "share is unavailable");
        }
        return share;
    }

    private Optional<DriveResolvedShareAccess> findActiveReadableShare(AuthLoginResponse session, DriveItem item) {
        DriveItem current = item;
        DriveScopeContext scope = scopeOf(item);
        Set<String> visited = new LinkedHashSet<>();
        DriveShare bestShare = null;
        DriveSharePermission bestPermission = null;
        while (current != null && visited.add(current.getId())) {
            if (!sameScope(scope, current)) {
                break;
            }
            List<DriveShare> shares = driveShareRepository.findByItemIdIn(Collections.singletonList(current.getId()));
            for (DriveShare currentShare : shares) {
                if (currentShare.isRevoked()
                        || !matchesSession(currentShare, session)
                        || "expired".equals(resolveOutgoingStatus(currentShare))
                        || "source_deleted".equals(resolveOutgoingStatus(currentShare))) {
                    continue;
                }
                if (bestPermission == null || isHigherPermission(currentShare.getPermission(), bestPermission)) {
                    bestShare = currentShare;
                    bestPermission = currentShare.getPermission();
                }
            }
            if (!StringUtils.hasText(current.getParentId())) {
                break;
            }
            current = findItemInShareScope(scope, current.getParentId()).orElse(null);
        }
        if (bestShare == null || bestPermission == null) {
            return Optional.empty();
        }
        return Optional.of(new DriveResolvedShareAccess(item, bestShare, bestPermission));
    }

    List<DriveShare> loadMatchedShares(AuthLoginResponse session) {
        Map<DriveShareTargetType, Set<String>> targets = collectTargets(session);
        List<DriveShare> shares = new ArrayList<>();
        for (Map.Entry<DriveShareTargetType, Set<String>> entry : targets.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                shares.addAll(driveShareRepository.findByTargetTypeAndTargetIdInOrderByUpdatedAtDesc(entry.getKey(), entry.getValue()));
            }
        }
        Map<String, DriveShare> deduplicated = new LinkedHashMap<>();
        for (DriveShare share : shares) {
            deduplicated.putIfAbsent(share.getId(), share);
        }
        return new ArrayList<>(deduplicated.values());
    }

    private Map<DriveShareTargetType, Set<String>> collectTargets(AuthLoginResponse session) {
        Map<DriveShareTargetType, Set<String>> targets = new LinkedHashMap<>();
        targets.put(DriveShareTargetType.PERSON, new LinkedHashSet<>());
        targets.put(DriveShareTargetType.UNIT, new LinkedHashSet<>());
        targets.put(DriveShareTargetType.IDENTITY, new LinkedHashSet<>());
        targets.get(DriveShareTargetType.PERSON).add(session.getPersonOaId());
        for (AuthIdentityDto identity : session.getIdentityList()) {
            if (identity == null) {
                continue;
            }
            if (StringUtils.hasText(identity.getId())) {
                targets.get(DriveShareTargetType.IDENTITY).add(identity.getId());
            }
            if (StringUtils.hasText(identity.getUnit())) {
                targets.get(DriveShareTargetType.UNIT).add(identity.getUnit());
            }
        }
        targets.put(DriveShareTargetType.PERSON, canonicalAliasResolver.includeLegacyAliases("PERSON", targets.get(DriveShareTargetType.PERSON)));
        targets.put(DriveShareTargetType.UNIT, canonicalAliasResolver.includeLegacyAliases("UNIT", targets.get(DriveShareTargetType.UNIT)));
        targets.put(DriveShareTargetType.IDENTITY, canonicalAliasResolver.includeLegacyAliases("IDENTITY", targets.get(DriveShareTargetType.IDENTITY)));
        return targets;
    }

    private boolean matchesSession(DriveShare share, AuthLoginResponse session) {
        Set<String> targets=collectTargets(session).get(share.getTargetType());
        return targets!=null&&targets.contains(share.getTargetId());
    }

    private boolean isDescendantOrSelf(DriveItem rootItem, DriveItem item) {
        DriveScopeContext scope = scopeOf(rootItem);
        DriveItem current = item;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current.getId())) {
            if (!sameScope(scope, current)) {
                return false;
            }
            if (rootItem.getId().equals(current.getId())) {
                return true;
            }
            if (!StringUtils.hasText(current.getParentId())) {
                return false;
            }
            current = findItemInShareScope(scope, current.getParentId()).orElse(null);
        }
        return false;
    }

    private List<DriveShare> listManageableShares(AuthLoginResponse session, DriveItem item) {
        if (item.getScopeType() == DriveScopeType.WORKSPACE) {
            WorkspaceRole role = driveScopeAccessService.resolveWorkspaceRole(session, item.getScopeId());
            if (role == WorkspaceRole.OWNER || role == WorkspaceRole.ADMIN) {
                return driveShareRepository.findByItemIdOrderByUpdatedAtDesc(item.getId());
            }
        }
        return driveShareRepository.findByItemIdAndOwnerPersonOaIdOrderByUpdatedAtDesc(
                item.getId(), session.getPersonOaId());
    }

    private String shareOwnerPersonOaId(AuthLoginResponse session, DriveItem item) {
        if (item.getScopeType() == DriveScopeType.WORKSPACE) {
            return session.getPersonOaId();
        }
        return item.getOwnerPersonOaId();
    }

    private String shareOwnerName(AuthLoginResponse session, DriveItem item) {
        if (item.getScopeType() == DriveScopeType.WORKSPACE) {
            if (StringUtils.hasText(session.getPersonName())) {
                return session.getPersonName();
            }
            return session.getPersonOaId();
        }
        return item.getOwnerName();
    }

    private DriveScopeContext scopeOf(DriveItem item) {
        DriveScopeType type = item.getScopeType() == null ? DriveScopeType.PERSONAL : item.getScopeType();
        String scopeId = StringUtils.hasText(item.getScopeId()) ? item.getScopeId() : item.getOwnerPersonOaId();
        return DriveScopeContext.of(type, scopeId, item.getOwnerPersonOaId(), item.getOwnerName());
    }

    private boolean sameScope(DriveScopeContext scope, DriveItem item) {
        DriveScopeType itemScopeType = item.getScopeType() == null ? DriveScopeType.PERSONAL : item.getScopeType();
        String itemScopeId = StringUtils.hasText(item.getScopeId()) ? item.getScopeId() : item.getOwnerPersonOaId();
        return itemScopeType == scope.getScopeType()
                && (itemScopeId == null ? "" : itemScopeId).equals(scope.getScopeId() == null ? "" : scope.getScopeId());
    }

    private boolean isWorkspaceRoot(DriveItem item) {
        return item != null
                && item.getScopeType() == DriveScopeType.WORKSPACE
                && !StringUtils.hasText(item.getParentId());
    }

    private Optional<DriveItem> findItemInShareScope(DriveScopeContext scope, String itemId) {
        if (!StringUtils.hasText(itemId)) {
            return Optional.empty();
        }
        return driveItemRepository.findById(itemId)
                .filter(item -> sameScope(scope, item));
    }

    private List<DriveItem> findActiveChildrenInShareScope(DriveScopeContext scope, String parentId) {
        return driveItemRepository.findByScopeTypeAndScopeIdAndDeletedFalseAndParentId(
                scope.getScopeType(), scope.getScopeId(), parentId);
    }

    private DriveShareResponse toOutgoingResponse(DriveShare share) {
        DriveItem item = driveItemRepository.findById(share.getItemId()).orElse(null);
        DriveShareResponse response = toShareResponse(share, item);
        boolean active = "active".equals(response.getStatus());
        response.setEffectivePermission(share.getPermission().name().toLowerCase(Locale.ROOT));
        response.setAllowPreview(active);
        response.setAllowDownload(active && share.getPermission().allowsDownload());
        return response;
    }

    private DriveShareCollaboratorSummaryResponse toCollaboratorSummary(DriveShare share) {
        DriveShareCollaboratorSummaryResponse response = new DriveShareCollaboratorSummaryResponse();
        response.setShareId(share.getId());
        response.setTargetType(share.getTargetType().name().toLowerCase(Locale.ROOT));
        response.setTargetId(share.getTargetId());
        response.setTargetName(share.getTargetName());
        response.setPermission(share.getPermission().name().toLowerCase(Locale.ROOT));
        response.setExpiresAt(share.getExpiresAt());
        response.setStatus(resolveOutgoingStatus(share));
        response.setUpdatedAt(share.getUpdatedAt());
        return response;
    }

    private DriveShareResponse toShareResponse(DriveShare share, DriveItem item) {
        DriveShareResponse response = new DriveShareResponse();
        response.setId(share.getId());
        response.setOwnerPersonOaId(share.getOwnerPersonOaId());
        response.setOwnerName(share.getOwnerName());
        response.setTargetType(share.getTargetType().name().toLowerCase(Locale.ROOT));
        response.setTargetId(share.getTargetId());
        response.setTargetName(share.getTargetName());
        response.setPermission(share.getPermission().name().toLowerCase(Locale.ROOT));
        response.setExpiresAt(share.getExpiresAt());
        response.setRevoked(share.isRevoked());
        response.setCreatedAt(share.getCreatedAt());
        response.setUpdatedAt(share.getUpdatedAt());
        response.setStatus(resolveOutgoingStatus(share, item));
        if (item != null) {
            response.setItem(driveItemResponseAssembler.toResponseWithParentName(item));
        }
        return response;
    }

    private DriveShareResponse toIncomingShareResponse(DriveShare share, DriveResolvedShareAccess access) {
        DriveShareResponse response = toShareResponse(share, access.getItem());
        applyEffectiveAccess(response, access);
        return response;
    }

    private void applyEffectiveAccess(DriveShareResponse response, DriveResolvedShareAccess access) {
        response.setEffectivePermission(access.getEffectivePermission().name().toLowerCase(Locale.ROOT));
        response.setAllowPreview(access.allowsPreview());
        response.setAllowDownload(access.allowsDownload());
    }

    private String resolveOutgoingStatus(DriveShare share) {
        return resolveOutgoingStatus(share, driveItemRepository.findById(share.getItemId()).orElse(null));
    }

    private String resolveOutgoingStatus(DriveShare share, DriveItem item) {
        if (share.isRevoked()) {
            return "revoked";
        }
        if (item == null || item.isDeleted()) {
            return "source_deleted";
        }
        if (isExpired(share.getExpiresAt())) {
            return "expired";
        }
        return "active";
    }

    private boolean isHigherPermission(DriveSharePermission candidate, DriveSharePermission baseline) {
        if (candidate == null) {
            return false;
        }
        if (baseline == null) {
            return true;
        }
        return DriveSharePermission.max(candidate, baseline) == candidate && candidate != baseline;
    }

    private boolean isExpired(String expiresAt) {
        if (!StringUtils.hasText(expiresAt)) {
            return false;
        }
        try {
            return Instant.parse(expiresAt).isBefore(Instant.now());
        } catch (Exception e) {
            return false;
        }
    }

    private DriveItem requireItem(String itemId) {
        return driveItemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found"));
    }

    private DriveSharePageResponse pageShares(List<DriveShareResponse> items, DriveShareQueryRequest request) {
        List<DriveShareResponse> sortedItems = new ArrayList<>(items);
        sortedItems.sort(shareComparator(request));
        int pageSize = normalizePageSize(request == null ? null : request.getSize());
        long total = sortedItems.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) pageSize);
        int page = normalizePage(request == null ? null : request.getPage(), totalPages);
        int fromIndex = total == 0 ? 0 : Math.min((page - 1) * pageSize, sortedItems.size());
        int toIndex = total == 0 ? 0 : Math.min(fromIndex + pageSize, sortedItems.size());

        DriveSharePageResponse response = new DriveSharePageResponse();
        response.setPage(page);
        response.setSize(pageSize);
        response.setTotal(total);
        response.setTotalPages(totalPages);
        response.setHasNext(totalPages > 0 && page < totalPages);
        response.setItems(sortedItems.subList(fromIndex, toIndex));
        return response;
    }

    private Comparator<DriveShareResponse> shareComparator(DriveShareQueryRequest request) {
        String sortBy = request != null && StringUtils.hasText(request.getSortBy())
                ? request.getSortBy().trim() : "updatedAt";
        boolean ascending = request != null && "asc".equalsIgnoreCase(request.getSortOrder());
        Comparator<DriveShareResponse> valueComparator;
        switch (sortBy) {
            case "name":
                valueComparator = (left, right) -> NaturalOrder.compare(itemName(left), itemName(right));
                break;
            case "sizeBytes":
                valueComparator = Comparator.comparing(
                        item -> item.getItem() == null ? 0L : item.getItem().getSizeBytes(),
                        Comparator.nullsLast(Long::compareTo));
                break;
            case "ownerName":
                valueComparator = (left, right) -> NaturalOrder.compare(left.getOwnerName(), right.getOwnerName());
                break;
            case "targetName":
                valueComparator = (left, right) -> NaturalOrder.compare(left.getTargetName(), right.getTargetName());
                break;
            case "permission":
                valueComparator = Comparator.comparing(DriveShareResponse::getPermission, Comparator.nullsLast(String::compareTo));
                break;
            case "updatedAt":
            default:
                valueComparator = Comparator.comparing(
                        item -> item.getItem() != null && StringUtils.hasText(item.getItem().getUpdatedAt())
                                ? item.getItem().getUpdatedAt() : item.getUpdatedAt(),
                        Comparator.nullsLast(String::compareTo));
                break;
        }
        if (!ascending) {
            valueComparator = valueComparator.reversed();
        }
        Comparator<DriveShareResponse> folderFirst = Comparator.comparing(
                item -> item.getItem() != null && "folder".equalsIgnoreCase(item.getItem().getItemType()) ? 0 : 1);
        return folderFirst.thenComparing(valueComparator)
                .thenComparing((left, right) -> NaturalOrder.compare(itemName(left), itemName(right)))
                .thenComparing(item -> item.getId() == null ? "" : item.getId());
    }

    private String itemName(DriveShareResponse response) {
        return response.getItem() == null ? "" : response.getItem().getName();
    }

    private boolean matchesKeyword(DriveShareResponse item, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String current = keyword.trim().toLowerCase(Locale.ROOT);
        String itemName = item.getItem() == null ? "" : trimToEmpty(item.getItem().getName());
        String text = (itemName + " " + trimToEmpty(item.getTargetName()) + " " + trimToEmpty(item.getOwnerName())).toLowerCase(Locale.ROOT);
        return text.contains(current);
    }

    private boolean matchesPermission(DriveShareResponse item, String permission) {
        if (!StringUtils.hasText(permission)) {
            return true;
        }
        return permission.trim().equalsIgnoreCase(item.getPermission());
    }

    private boolean matchesStatus(DriveShareResponse item, String status) {
        if (!StringUtils.hasText(status)) {
            return true;
        }
        return status.trim().equalsIgnoreCase(item.getStatus());
    }

    private List<String> normalizeItemIds(DriveCreateShareRequest request) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (request != null) {
            if (StringUtils.hasText(request.getItemId())) {
                ids.add(request.getItemId().trim());
            }
            if (request.getItemIds() != null) {
                request.getItemIds().stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .forEach(ids::add);
            }
        }
        return new ArrayList<>(ids);
    }

    private List<String> normalizeIds(Collection<String> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(ids::add);
        return new ArrayList<>(ids);
    }

    private String normalizeTargetKey(DriveShareTargetRequest target) {
        return trimToEmpty(target == null ? "" : target.getType()) + ":" + trimToEmpty(target == null ? "" : target.getId());
    }

    private String normalizeExpiresAt(String value) {
        String current = trimToEmpty(value);
        if (current.isEmpty()) {
            return "";
        }
        try {
            if (current.length() == 10) {
                return LocalDate.parse(current)
                        .atTime(LocalTime.of(23, 59, 59))
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toString();
            }
            return Instant.parse(current).toString();
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid share expiration time");
        }
    }

    private String trimToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private int normalizePage(Integer page, int totalPages) {
        int normalized = page == null ? DEFAULT_PAGE : Math.max(DEFAULT_PAGE, page);
        if (totalPages > 0) {
            return Math.min(normalized, totalPages);
        }
        return DEFAULT_PAGE;
    }

    private int normalizePageSize(Integer size) {
        int requested = size == null ? DEFAULT_PAGE_SIZE : size;
        for (int allowed : ALLOWED_PAGE_SIZES) {
            if (requested <= allowed) {
                return allowed;
            }
        }
        return 100;
    }

    private String buildSummaryMessage(DriveBatchOperationResponse response, String action) {
        return response.getTotalFailed() == 0
                ? action + " completed"
                : action + " completed with " + response.getTotalFailed() + " failure(s)";
    }
}
