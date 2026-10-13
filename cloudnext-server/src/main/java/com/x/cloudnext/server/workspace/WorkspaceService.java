package com.x.cloudnext.server.workspace;

import java.util.ArrayList;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.drive.DriveConflictStrategy;
import com.x.cloudnext.server.drive.DriveItemResponse;
import com.x.cloudnext.server.drive.DriveItemResponseAssembler;
import com.x.cloudnext.server.drive.DriveItemQueryResult;
import com.x.cloudnext.server.drive.DriveItemType;
import com.x.cloudnext.server.drive.DrivePageResponse;
import com.x.cloudnext.server.drive.DriveQueryRequest;
import com.x.cloudnext.server.drive.NaturalOrder;
import com.x.cloudnext.server.drive.DriveScopeContext;
import com.x.cloudnext.server.drive.DriveScopeType;
import com.x.cloudnext.server.drive.DriveService;
import com.x.cloudnext.server.drive.WorkspaceRole;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;
import com.x.cloudnext.server.drive.repository.DriveItemReadRepository;
import com.x.cloudnext.server.workspace.entity.Workspace;
import com.x.cloudnext.server.workspace.entity.WorkspaceMember;
import com.x.cloudnext.server.workspace.entity.UserPreference;
import com.x.cloudnext.server.workspace.repository.WorkspaceMemberRepository;
import com.x.cloudnext.server.workspace.repository.WorkspaceRepository;
import com.x.cloudnext.server.workspace.repository.UserPreferenceRepository;
import com.x.cloudnext.server.form.CanonicalTargetValidationService;
import com.x.cloudnext.server.form.repository.PrincipalBindingRepository;

/**
 * Service layer for the iteration-one Workspace feature.
 *
 * <p>This iteration implements {@code createWorkspace} (task 1.3) plus the
 * read-side endpoints from task 1.4: {@code listMyWorkspaces},
 * {@code getWorkspace} and {@code listItems}. Subsequent tasks
 * (1.5 file &amp; folder writes, 1.6 member management) extend this service
 * in place — the {@link #toDetailResponse} helper exists today so those
 * tasks can reuse the same projection.
 *
 * <p>Design reference: {@code .kiro/specs/cloudnext-iteration-plan/design.md}
 * → §"WorkspaceService". Validation rules (non-blank, length 1..100) live
 * here rather than on the DTO so service-direct callers (integration tests,
 * future bulk import) get the same guarantees as HTTP traffic.
 *
 * <p>Why no {@code DriveScopeAccessService} dependency: the read paths in
 * this task already need the caller's {@link WorkspaceRole} to populate
 * {@code myRole} in the response, so they perform a direct
 * {@link WorkspaceMemberRepository#findByWorkspaceIdAndPersonOaId} lookup
 * and reuse that result both as the membership gate and as the response
 * field. Routing through {@code DriveScopeAccessService.requireItem} would
 * either re-issue the same query or expect a {@code DriveItem} id we do
 * not have (the workspace id is not an item id). The access gateway is
 * still the contract for write paths in task 1.5.
 */
@Service
public class WorkspaceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceService.class);

    private static final int MAX_WORKSPACE_NAME_LENGTH = 100;
    private static final String WORKSPACE_SORT_PREFERENCE_KEY = "workspace.sortMode";
    private static final String WORKSPACE_SORT_TIME = "TIME";
    private static final String WORKSPACE_SORT_NAME = "NAME";

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final DriveItemRepository driveItemRepository;
    private final DriveItemReadRepository driveItemReadRepository;
    private final DriveItemResponseAssembler driveItemResponseAssembler;
    private final DriveService driveService;
    private final WorkspaceAuthorizationCleanupService workspaceAuthorizationCleanupService;
    private final com.x.cloudnext.server.audit.AuditLogService auditLogService;
    private final UserPreferenceRepository userPreferenceRepository;
    private final CanonicalTargetValidationService targetValidationService;
    private final PrincipalBindingRepository principalBindingRepository;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            DriveItemRepository driveItemRepository,
            DriveItemReadRepository driveItemReadRepository,
            DriveItemResponseAssembler driveItemResponseAssembler,
            DriveService driveService,
            WorkspaceAuthorizationCleanupService workspaceAuthorizationCleanupService,
            com.x.cloudnext.server.audit.AuditLogService auditLogService,
            UserPreferenceRepository userPreferenceRepository,
            CanonicalTargetValidationService targetValidationService,
            PrincipalBindingRepository principalBindingRepository) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.driveItemRepository = driveItemRepository;
        this.driveItemReadRepository = driveItemReadRepository;
        this.driveItemResponseAssembler = driveItemResponseAssembler;
        this.driveService = driveService;
        this.workspaceAuthorizationCleanupService = workspaceAuthorizationCleanupService;
        this.auditLogService = auditLogService;
        this.userPreferenceRepository = userPreferenceRepository;
        this.targetValidationService = targetValidationService;
        this.principalBindingRepository = principalBindingRepository;
    }

    /**
     * Create a new Workspace and return its full projection.
     *
     * <p>Runs as a single read-write transaction with four steps in order:
     * <ol>
     *   <li>Persist the {@link Workspace} (status {@code ACTIVE},
     *       {@code rootItemId} blank).</li>
     *   <li>Persist a {@link WorkspaceMember} for the caller with role
     *       {@link WorkspaceRole#OWNER}.</li>
     *   <li>Persist the workspace root {@link DriveItem}
     *       ({@code scopeType=WORKSPACE}, {@code parentId=""},
     *       {@code itemType=FOLDER}, {@code name=""}, {@code storageKey=""}).</li>
     *   <li>Backfill {@code workspace.rootItemId} with the root item id and
     *       persist the workspace again.</li>
     * </ol>
     *
     * <p>If any step throws, the entire transaction rolls back so the
     * Workspace, owner membership, and root item either all exist or none
     * do — no half-built workspaces.
     */
    @Transactional
    public WorkspaceDetailResponse createWorkspace(AuthLoginResponse session, WorkspaceCreateRequest req) {
        String name = normalizeWorkspaceName(req == null ? "" : req.getName());

        String creatorName = StringUtils.hasText(session.getPersonName())
                ? session.getPersonName()
                : session.getPersonOaId();

        // (a) Workspace
        Workspace workspace = new Workspace();
        workspace.setId(UUID.randomUUID().toString());
        workspace.setName(name);
        workspace.setCreatorPersonOaId(session.getPersonOaId());
        workspace.setCreatorName(creatorName);
        workspace.setStatus(Workspace.STATUS_ACTIVE);
        // rootItemId left blank; filled in step (d)
        workspaceRepository.save(workspace);

        // (b) WorkspaceMember(role=OWNER)
        WorkspaceMember owner = new WorkspaceMember();
        owner.setId(UUID.randomUUID().toString());
        owner.setWorkspaceId(workspace.getId());
        owner.setPersonOaId(session.getPersonOaId());
        owner.setPersonName(creatorName);
        owner.setRole(WorkspaceRole.OWNER);
        workspaceMemberRepository.save(owner);

        // (c) Root DriveItem (scopeType=WORKSPACE, parentId="", itemType=FOLDER, name="", storageKey="")
        DriveItem rootItem = new DriveItem();
        rootItem.setId(UUID.randomUUID().toString());
        rootItem.setParentId("");
        rootItem.setOwnerPersonOaId(session.getPersonOaId());
        rootItem.setOwnerName(creatorName);
        rootItem.setName("");
        rootItem.setItemType(DriveItemType.FOLDER);
        rootItem.setFileType("folder");
        rootItem.setContentType("");
        rootItem.setSizeBytes(0L);
        rootItem.setStorageKey("");
        rootItem.setScopeType(DriveScopeType.WORKSPACE);
        rootItem.setScopeId(workspace.getId());
        rootItem.setStorageBackend("local");
        driveItemRepository.save(rootItem);

        // (d) Backfill rootItemId on the Workspace
        workspace.setRootItemId(rootItem.getId());
        workspaceRepository.save(workspace);

        LOGGER.info("workspace created id={} name={} creator={}",
                workspace.getId(), workspace.getName(), session.getPersonOaId());

        return toDetailResponse(workspace, WorkspaceRole.OWNER, 1L);
    }

    /**
     * List the Workspaces the caller is a member of, ordered by the caller's
     * saved sort preference. The default is creation time descending.
     *
     * <p>If the caller has no membership rows the method returns an empty
     * list rather than 403 — non-membership is the steady state for new
     * users (Requirement 3.2).
     *
     * <p>Member counts are computed by re-reading {@code WorkspaceMember}
     * rows per workspace; for the first iteration (small N) this is fine.
     * See the inline TODO for the batched alternative.
     */
    @Transactional(readOnly = true)
    public List<WorkspaceSummaryResponse> listMyWorkspaces(AuthLoginResponse session) {
        List<WorkspaceMember> myMemberships = workspaceMemberRepository.findByPersonOaId(session.getPersonOaId());
        if (myMemberships.isEmpty()) {
            return Collections.emptyList();
        }

        return toSummaryResponses(myMemberships, resolveWorkspaceSortPreference(session.getPersonOaId()));
    }

    private List<WorkspaceSummaryResponse> toSummaryResponses(List<WorkspaceMember> myMemberships, String sortMode) {
        if (myMemberships == null || myMemberships.isEmpty()) {
            return Collections.emptyList();
        }

        // Preserve insertion order so duplicates collapse predictably.
        Set<String> workspaceIds = new LinkedHashSet<>();
        for (WorkspaceMember m : myMemberships) {
            workspaceIds.add(m.getWorkspaceId());
        }

        Map<String, Workspace> workspacesById = new LinkedHashMap<>();
        for (Workspace ws : workspaceRepository.findAllById(workspaceIds)) {
            workspacesById.put(ws.getId(), ws);
        }

        // TODO(perf): batch via SELECT workspace_id, COUNT(*) ... GROUP BY workspace_id once dataset grows.
        Map<String, Long> memberCounts = new LinkedHashMap<>();
        for (String id : workspaceIds) {
            memberCounts.put(id, (long) workspaceMemberRepository.findByWorkspaceId(id).size());
        }

        List<WorkspaceSummaryResponse> summaries = new ArrayList<>(myMemberships.size());
        for (WorkspaceMember m : myMemberships) {
            Workspace ws = workspacesById.get(m.getWorkspaceId());
            if (ws == null) {
                // Membership row pointing at a missing Workspace shouldn't happen,
                // but skip defensively rather than NPE.
                continue;
            }
            if (!isActiveWorkspace(ws)) {
                continue;
            }
            WorkspaceSummaryResponse summary = new WorkspaceSummaryResponse();
            summary.setId(ws.getId());
            summary.setName(ws.getName());
            summary.setStatus(ws.getStatus());
            summary.setMyRole(m.getRole() == null ? null : m.getRole().name());
            summary.setCreatorName(ws.getCreatorName());
            summary.setCreatedAt(ws.getCreatedAt());
            summary.setUpdatedAt(ws.getUpdatedAt());
            Long count = memberCounts.get(ws.getId());
            summary.setMemberCount(count == null ? 0L : count);
            summaries.add(summary);
        }

        summaries.sort(workspaceSummaryComparator(sortMode));

        return summaries;
    }

    private Comparator<WorkspaceSummaryResponse> workspaceSummaryComparator(String sortMode) {
        if (WORKSPACE_SORT_NAME.equals(sortMode)) {
            return workspaceSummaryNameComparator();
        }
        return Comparator
                .comparing(
                        WorkspaceSummaryResponse::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(workspaceSummaryNameComparator());
    }

    private Comparator<WorkspaceSummaryResponse> workspaceSummaryNameComparator() {
        return (left, right) -> {
            int compared = NaturalOrder.compare(left.getName(), right.getName());
            return compared != 0 ? compared : Comparator.nullsLast(String::compareTo).compare(left.getId(), right.getId());
        };
    }

    @Transactional(readOnly = true)
    public WorkspaceSortPreferenceResponse getWorkspaceSortPreference(AuthLoginResponse session) {
        return new WorkspaceSortPreferenceResponse(resolveWorkspaceSortPreference(session.getPersonOaId()));
    }

    @Transactional
    public WorkspaceSortPreferenceResponse updateWorkspaceSortPreference(
            AuthLoginResponse session,
            WorkspaceSortPreferenceRequest request) {
        String sortMode = normalizeWorkspaceSortMode(request == null ? null : request.getSortMode());
        UserPreference preference = userPreferenceRepository
                .findByPersonOaIdAndPreferenceKey(session.getPersonOaId(), WORKSPACE_SORT_PREFERENCE_KEY)
                .orElseGet(() -> {
                    UserPreference created = new UserPreference();
                    created.setId(UUID.randomUUID().toString());
                    created.setPersonOaId(session.getPersonOaId());
                    created.setPreferenceKey(WORKSPACE_SORT_PREFERENCE_KEY);
                    return created;
                });
        preference.setPreferenceValue(sortMode);
        userPreferenceRepository.save(preference);
        return new WorkspaceSortPreferenceResponse(sortMode);
    }

    private String resolveWorkspaceSortPreference(String personOaId) {
        return userPreferenceRepository
                .findByPersonOaIdAndPreferenceKey(personOaId, WORKSPACE_SORT_PREFERENCE_KEY)
                .map(UserPreference::getPreferenceValue)
                .map(this::normalizeWorkspaceSortModeOrDefault)
                .orElse(WORKSPACE_SORT_TIME);
    }

    private String normalizeWorkspaceSortModeOrDefault(String raw) {
        if (!StringUtils.hasText(raw)) {
            return WORKSPACE_SORT_TIME;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (WORKSPACE_SORT_NAME.equals(value)) {
            return WORKSPACE_SORT_NAME;
        }
        return WORKSPACE_SORT_TIME;
    }

    private String normalizeWorkspaceSortMode(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sortMode is required");
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (WORKSPACE_SORT_TIME.equals(value) || WORKSPACE_SORT_NAME.equals(value)) {
            return value;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported workspace sort mode");
    }

    /**
     * Return the full detail projection of a Workspace the caller is a
     * member of. Non-members get 403, missing workspaces get 404.
     */
    @Transactional(readOnly = true)
    public WorkspaceDetailResponse getWorkspace(AuthLoginResponse session, String workspaceId) {
        if (!StringUtils.hasText(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "workspace not found");
        }
        String wsId = workspaceId.trim();

        Optional<WorkspaceMember> myMembership = workspaceMemberRepository
                .findByWorkspaceIdAndPersonOaId(wsId, session.getPersonOaId());
        if (!myMembership.isPresent()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "workspace access denied");
        }

        Workspace workspace = workspaceRepository.findById(wsId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "workspace not found"));
        assertActiveWorkspace(workspace);

        long memberCount = workspaceMemberRepository.findByWorkspaceId(wsId).size();
        return toDetailResponse(workspace, myMembership.get().getRole(), memberCount);
    }

    /**
     * Rename a workspace. Owners and admins may rename active workspaces.
     */
    @Transactional
    public WorkspaceDetailResponse updateWorkspace(
            AuthLoginResponse session,
            String workspaceId,
            WorkspaceUpdateRequest req) {
        WorkspaceMember member = requireManagementMember(session, workspaceId);
        String wsId = workspaceId.trim();
        Workspace workspace = requireActiveWorkspace(wsId);
        String previousName = workspace.getName();
        String nextName = normalizeWorkspaceName(req == null ? "" : req.getName());
        if (!nextName.equals(previousName)) {
            workspace.setName(nextName);
            workspaceRepository.save(workspace);
            auditLogService.record(
                    session.getPersonOaId(),
                    com.x.cloudnext.server.audit.AuditAction.RENAME,
                    "WORKSPACE",
                    wsId,
                    "from", previousName,
                    "to", nextName);
        }
        long memberCount = workspaceMemberRepository.findByWorkspaceId(wsId).size();
        return toDetailResponse(workspace, member.getRole(), memberCount);
    }

    /**
     * Soft-delete a workspace by archiving it. Only owners may perform this
     * operation; files, members and storage remain intact for future recovery
     * or audit.
     */
    @Transactional
    public void archiveWorkspace(AuthLoginResponse session, String workspaceId) {
        WorkspaceMember member = requireOwnerMember(session, workspaceId);
        String wsId = workspaceId.trim();
        Workspace workspace = requireActiveWorkspace(wsId);
        workspace.setStatus(Workspace.STATUS_ARCHIVED);
        workspaceRepository.save(workspace);
        auditLogService.record(
                session.getPersonOaId(),
                com.x.cloudnext.server.audit.AuditAction.DELETE,
                "WORKSPACE",
                wsId,
                "name", workspace.getName(),
                "role", member.getRole() == null ? "" : member.getRole().name());
    }

    /**
     * List direct children of a folder inside a Workspace.
     *
     * <p>{@code parentId} is optional: when blank or null the listing
     * defaults to the workspace root (i.e. children of {@code rootItemId}).
     * When provided, the parent must be a non-deleted folder belonging to
     * the same workspace; cross-scope or cross-workspace ids are rejected
     * with 400 to prevent membership-by-id-guessing leaks.
     *
     * <p>Result ordering mirrors the personal-drive listing convention used
     * elsewhere in the codebase: folders first, then most-recently updated,
     * then ascending by name as a stable tiebreaker.
     */
    @Transactional(readOnly = true)
    public List<DriveItemResponse> listItems(AuthLoginResponse session, String workspaceId, String parentId) {
        if (!StringUtils.hasText(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "workspace not found");
        }
        String wsId = workspaceId.trim();

        Optional<WorkspaceMember> myMembership = workspaceMemberRepository
                .findByWorkspaceIdAndPersonOaId(wsId, session.getPersonOaId());
        if (!myMembership.isPresent()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "workspace access denied");
        }

        Workspace workspace = workspaceRepository.findById(wsId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "workspace not found"));
        assertActiveWorkspace(workspace);

        String effectiveParentId;
        if (!StringUtils.hasText(parentId)) {
            effectiveParentId = workspace.getRootItemId();
        } else {
            String requested = parentId.trim();
            DriveItem parent = driveItemRepository.findById(requested)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "parent folder is invalid"));
            if (parent.isDeleted() || parent.getItemType() != DriveItemType.FOLDER) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "parent folder is invalid");
            }
            // Cross-scope leak guard: parent must live in this workspace.
            if (parent.getScopeType() != DriveScopeType.WORKSPACE || !wsId.equals(parent.getScopeId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "parent folder is invalid");
            }
            effectiveParentId = requested;
        }

        List<DriveItem> items = driveItemRepository
                .findByScopeTypeAndScopeIdAndDeletedFalseAndParentId(
                        DriveScopeType.WORKSPACE, wsId, effectiveParentId);

        // folders first, then updatedAt desc, then name asc as a tiebreak.
        items.sort(Comparator
                .comparing((DriveItem item) -> item.getItemType() != DriveItemType.FOLDER)
                .thenComparing((DriveItem item) -> item.getUpdatedAt() == null ? "" : item.getUpdatedAt(),
                        Comparator.reverseOrder())
                .thenComparing((DriveItem item) -> item.getName() == null ? "" : item.getName()));

        return driveItemResponseAssembler.toResponses(items);
    }

    @Transactional(readOnly = true)
    public DrivePageResponse queryItems(
            AuthLoginResponse session,
            String workspaceId,
            DriveQueryRequest request) {
        if (!StringUtils.hasText(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "workspace not found");
        }
        String wsId = workspaceId.trim();
        if (!workspaceMemberRepository.findByWorkspaceIdAndPersonOaId(wsId, session.getPersonOaId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "workspace access denied");
        }
        Workspace workspace = requireActiveWorkspace(wsId);
        DriveQueryRequest safeRequest = request == null ? new DriveQueryRequest() : request;
        String requestedParentId = safeRequest.getParentId();
        String effectiveParentId = StringUtils.hasText(requestedParentId)
                ? requestedParentId.trim()
                : workspace.getRootItemId();
        DriveItem parent = driveItemRepository.findById(effectiveParentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "parent folder is invalid"));
        if (parent.isDeleted()
                || parent.getItemType() != DriveItemType.FOLDER
                || parent.getScopeType() != DriveScopeType.WORKSPACE
                || !wsId.equals(parent.getScopeId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "parent folder is invalid");
        }
        safeRequest.setParentId(effectiveParentId);
        DriveItemQueryResult result = driveItemReadRepository.queryWorkspaceDirectoryItems(wsId, safeRequest);
        return driveItemResponseAssembler.toPageResponse(result);
    }

    /**
     * Create a folder under a workspace, delegating to the scope-aware
     * internal in {@link DriveService} so personal and workspace flows share
     * one implementation. Validation order:
     * <ol>
     *   <li>Caller must be a non-VIEWER member ({@link #requireWritableMember}).</li>
     *   <li>Workspace must exist (404 otherwise).</li>
     *   <li>Empty {@code parentId} resolves to the workspace root, matching
     *       {@link #listItems(AuthLoginResponse, String, String)}.</li>
     *   <li>Parent accessibility, name normalization and sibling-name
     *       conflict are enforced by
     *       {@link DriveService#createFolderInScope(DriveScopeContext, String, String)}.</li>
     * </ol>
     */
    @Transactional
    public DriveItemResponse createFolder(AuthLoginResponse session, String workspaceId, WorkspaceFolderCreateRequest req) {
        WorkspaceMember member = requireWritableMember(session, workspaceId);
        Workspace workspace = requireActiveWorkspace(workspaceId.trim());
        String rawParentId = req == null ? "" : req.getParentId();
        String effectiveParentId = StringUtils.hasText(rawParentId) ? rawParentId.trim() : workspace.getRootItemId();
        String name = req == null ? "" : req.getName();
        DriveScopeContext scope = DriveScopeContext.workspace(
                workspace.getId(), session, resolveActorName(session, member));
        return driveService.createFolderInScope(scope, effectiveParentId, name);
    }

    /**
     * Upload a file into a workspace, delegating to the scope-aware internal
     * in {@link DriveService}. The conflict-resolution / sibling-name /
     * storage-write semantics are owned by {@code DriveService} so they stay
     * byte-identical with the personal upload path; this entry point only
     * adds the membership gate and the "blank parentId means workspace root"
     * convenience.
     */
    @Transactional
    public DriveItemResponse uploadFile(
            AuthLoginResponse session,
            String workspaceId,
            String parentId,
            MultipartFile file,
            DriveConflictStrategy conflictStrategy) {
        WorkspaceMember member = requireWritableMember(session, workspaceId);
        Workspace workspace = requireActiveWorkspace(workspaceId.trim());
        String effectiveParentId = StringUtils.hasText(parentId) ? parentId.trim() : workspace.getRootItemId();
        DriveScopeContext scope = DriveScopeContext.workspace(
                workspace.getId(), session, resolveActorName(session, member));
        return driveService.uploadFileInScope(scope, effectiveParentId, file, conflictStrategy);
    }

    /**
     * Soft-delete a workspace item into the WorkspaceTrash. The trash
     * isolation guarantee (Requirements 4.7 / 4.8) comes from the
     * scope-aware recursive marker inside {@code DriveService}: children are
     * located via {@code findByScopeTypeAndScopeIdAndDeletedFalseAndParentId},
     * so deletes never cross between PERSONAL and WORKSPACE subtrees even
     * when both share an {@code ownerPersonOaId}.
     *
     * <p>VIEWERs are blocked at the membership gate; non-members get the
     * same opaque "item not found" they would get for any cross-workspace
     * id-guessing attempt.
     */
    @Transactional
    public DriveItemResponse deleteItem(AuthLoginResponse session, String workspaceId, String itemId) {
        WorkspaceMember member = requireWritableMember(session, workspaceId);
        if (!StringUtils.hasText(itemId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found");
        }
        String wsId = workspaceId.trim();
        DriveItem item = driveItemRepository.findById(itemId.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found"));
        if (item.getScopeType() != DriveScopeType.WORKSPACE || !wsId.equals(item.getScopeId())) {
            // Mirror the personal-flow contract: hide existence of items the
            // caller cannot see (security-by-obscurity preserved).
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found");
        }
        // Cannot delete the workspace root itself — it is structural, not user content.
        Workspace workspace = requireActiveWorkspace(wsId);
        if (StringUtils.hasText(workspace.getRootItemId())
                && workspace.getRootItemId().equals(item.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot delete workspace root");
        }
        DriveScopeContext scope = DriveScopeContext.workspace(
                wsId, session, resolveActorName(session, member));
        return driveService.deleteItemInScope(scope, item);
    }

    /**
     * Resolve the caller's membership in a workspace. Non-members get 403,
     * missing or blank workspace ids get 404. Used as the entry gate for
     * every workspace-write code path before delegating to
     * {@link DriveService}.
     */
    private WorkspaceMember requireMember(AuthLoginResponse session, String workspaceId) {
        if (!StringUtils.hasText(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "workspace not found");
        }
        return workspaceMemberRepository
                .findByWorkspaceIdAndPersonOaId(workspaceId.trim(), session.getPersonOaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "workspace access denied"));
    }

    /**
     * Membership + write-permission gate. {@link WorkspaceRole#VIEWER}
     * hits {@code 403 workspace write denied} so the access-gateway matrix in
     * {@link com.x.cloudnext.server.drive.DriveScopeAccessService} (which is
     * not consulted on this code path because we already have the member row
     * in hand) and this entry point agree on the deny semantics.
     */
    private WorkspaceMember requireWritableMember(AuthLoginResponse session, String workspaceId) {
        WorkspaceMember member = requireMember(session, workspaceId);
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "workspace write denied");
        }
        return member;
    }

    /**
     * Pick the most informative non-blank display name for the actor —
     * preferring the membership row's {@code personName} (which mirrors what
     * appears in the member list UI) and falling back to the session name,
     * then the OA id. Mirrors {@code DriveService.resolveOwnerName} so the
     * personal and workspace pipelines surface a consistent {@code ownerName}.
     */
    private String resolveActorName(AuthLoginResponse session, WorkspaceMember member) {
        if (member != null && StringUtils.hasText(member.getPersonName())) {
            return member.getPersonName();
        }
        if (session != null && StringUtils.hasText(session.getPersonName())) {
            return session.getPersonName();
        }
        return session == null ? "" : session.getPersonOaId();
    }

    /**
     * Map a {@link Workspace} entity to its detail DTO. Extracted so future
     * tasks (1.4 / 1.6) reuse the same projection without drift.
     */
    private WorkspaceDetailResponse toDetailResponse(Workspace ws, WorkspaceRole myRole, long memberCount) {
        WorkspaceDetailResponse response = new WorkspaceDetailResponse();
        response.setId(ws.getId());
        response.setName(ws.getName());
        response.setStatus(ws.getStatus());
        response.setMyRole(myRole == null ? null : myRole.name());
        response.setRootItemId(ws.getRootItemId());
        response.setCreatorPersonOaId(ws.getCreatorPersonOaId());
        response.setCreatorName(ws.getCreatorName());
        response.setMemberCount(memberCount);
        response.setCreatedAt(ws.getCreatedAt());
        response.setUpdatedAt(ws.getUpdatedAt());
        return response;
    }

    // ---------------------------------------------------------------------
    // Member management (task 1.6)
    // ---------------------------------------------------------------------

    /**
     * List all members of a workspace, ordered OWNER → ADMIN → MEMBER →
     * VIEWER and then by {@code createdAt} ascending so the founding owner
     * always sits at the top of the table.
     *
     * <p>Only owners and admins are allowed through this endpoint
     * (Requirements 4.4 / 4.5 / 4.6); regular members and viewers receive
     * 403. The role gate is enforced by {@link #requireManagementMember}.
     */
    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> listMembers(AuthLoginResponse session, String workspaceId) {
        requireManagementMember(session, workspaceId);
        requireActiveWorkspace(workspaceId.trim());
        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspaceId.trim());
        members.sort(Comparator
                .comparing((WorkspaceMember m) -> roleSortRank(m.getRole()))
                .thenComparing(WorkspaceMember::getCreatedAt, Comparator.nullsLast(String::compareTo)));
        return members.stream().map(this::toMemberResponse).collect(Collectors.toList());
    }

    /**
     * Add a person to a workspace.
     *
     * <p>Constraints (Requirements 3.8, 3.9, 4.4, 4.5):
     * <ul>
     *   <li>Caller must be {@link WorkspaceRole#OWNER} or
     *       {@link WorkspaceRole#ADMIN} — anyone else gets 403.</li>
     *   <li>{@code personOaId} is required; missing or blank returns 400.</li>
     *   <li>An admin caller cannot grant {@link WorkspaceRole#OWNER}; only an
     *       owner can mint another owner. Violations return 403 so the matrix
     *       in {@link com.x.cloudnext.server.drive.DriveScopeAccessService}
     *       and this entry point agree.</li>
     *   <li>Re-adding an existing member returns 409 rather than silently
     *       overwriting their role — role changes flow through
     *       {@link #updateMemberRole}.</li>
     * </ul>
     */
    @Transactional
    public WorkspaceMemberResponse addMember(
            AuthLoginResponse session, String workspaceId, WorkspaceMemberAddRequest req) {
        WorkspaceMember caller = requireManagementMember(session, workspaceId);
        requireActiveWorkspace(workspaceId.trim());
        if (req == null || (!StringUtils.hasText(req.getPersonKey()) && !StringUtils.hasText(req.getPersonOaId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "member personKey is required");
        }
        if (StringUtils.hasText(req.getPersonKey()) && StringUtils.hasText(req.getPersonOaId())
                && !req.getPersonKey().trim().equals(req.getPersonOaId().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "personKey and personOaId must identify the same canonical person");
        }
        String personOaId = (StringUtils.hasText(req.getPersonKey()) ? req.getPersonKey() : req.getPersonOaId()).trim();
        targetValidationService.requirePerson(personOaId);
        WorkspaceRole desiredRole = parseRole(req.getRole());

        // Admin cannot grant OWNER role (Requirement 4.5).
        if (caller.getRole() == WorkspaceRole.ADMIN && desiredRole == WorkspaceRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "workspace admin cannot grant owner role");
        }

        String wsId = workspaceId.trim();
        Optional<WorkspaceMember> existing = workspaceMemberRepository
                .findByWorkspaceIdAndPersonOaId(wsId, personOaId);
        if (existing.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "member already exists");
        }

        String personName = StringUtils.hasText(req.getPersonName())
                ? req.getPersonName().trim()
                : personOaId;

        WorkspaceMember member = new WorkspaceMember();
        member.setId(UUID.randomUUID().toString());
        member.setWorkspaceId(wsId);
        member.setPersonOaId(personOaId);
        member.setPersonName(personName);
        member.setRole(desiredRole);
        workspaceMemberRepository.save(member);
        touchWorkspace(wsId);
        auditLogService.record(
                session.getPersonOaId(),
                com.x.cloudnext.server.audit.AuditAction.MEMBER_ADD,
                "WORKSPACE_MEMBER",
                member.getId(),
                "workspaceId", wsId,
                "memberPersonOaId", personOaId,
                "memberPersonName", personName,
                "role", desiredRole == null ? "" : desiredRole.name());
        return toMemberResponse(member);
    }

    /**
     * Change an existing member's role.
     *
     * <p>Owner-conservation (Requirement 4.5): any change that would let
     * {@code count(role=OWNER) == 0} is rejected with
     * {@code 400 workspace must keep at least one owner}. The check uses
     * {@link WorkspaceMemberRepository#countByWorkspaceIdAndRole} inside the
     * same transaction, so a concurrent owner-removal between read and
     * write would surface either as a JPA optimistic conflict or as a
     * second rejected request — never as a silent drop.
     *
     * <p>Admins cannot promote anyone to {@code OWNER} nor demote an
     * existing {@code OWNER}. A no-op role change (target already has the
     * desired role) returns the current row without touching the database.
     */
    @Transactional
    public WorkspaceMemberResponse updateMemberRole(
            AuthLoginResponse session,
            String workspaceId,
            String memberId,
            WorkspaceMemberUpdateRequest req) {
        WorkspaceMember caller = requireManagementMember(session, workspaceId);
        requireActiveWorkspace(workspaceId.trim());
        WorkspaceRole newRole = parseRole(req == null ? null : req.getRole());

        String wsId = workspaceId.trim();
        WorkspaceMember target = loadMemberInWorkspace(memberId, wsId);

        // Admin cannot grant or strip OWNER (Requirement 4.5).
        if (caller.getRole() == WorkspaceRole.ADMIN) {
            if (target.getRole() == WorkspaceRole.OWNER || newRole == WorkspaceRole.OWNER) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "workspace admin cannot modify owner");
            }
        }

        WorkspaceRole previousRole = target.getRole();
        if (previousRole == newRole) {
            return toMemberResponse(target);
        }

        // Owner conservation: cannot strip the last OWNER.
        if (previousRole == WorkspaceRole.OWNER && newRole != WorkspaceRole.OWNER) {
            long ownerCount = workspaceMemberRepository.countByWorkspaceIdAndRole(wsId, WorkspaceRole.OWNER);
            if (ownerCount <= 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "workspace must keep at least one owner");
            }
        }

        target.setRole(newRole);
        workspaceMemberRepository.save(target);
        if (newRole == WorkspaceRole.VIEWER && previousRole != WorkspaceRole.VIEWER) {
            workspaceAuthorizationCleanupService.revokeWorkspaceGrantsCreatedBy(wsId, target.getPersonOaId());
        }
        touchWorkspace(wsId);
        return toMemberResponse(target);
    }

    /**
     * Remove a member from a workspace.
     *
     * <p>Owner-conservation mirrors {@link #updateMemberRole}: removing the
     * last owner is rejected with
     * {@code 400 workspace must keep at least one owner}. Admins cannot
     * remove an owner under any circumstance — that requires another owner
     * to act (Requirement 4.5).
     */
    @Transactional
    public void removeMember(AuthLoginResponse session, String workspaceId, String memberId) {
        WorkspaceMember caller = requireManagementMember(session, workspaceId);
        String wsId = workspaceId.trim();
        requireActiveWorkspace(wsId);
        WorkspaceMember target = loadMemberInWorkspace(memberId, wsId);

        if (caller.getRole() == WorkspaceRole.ADMIN && target.getRole() == WorkspaceRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "workspace admin cannot remove owner");
        }

        if (target.getRole() == WorkspaceRole.OWNER) {
            long ownerCount = workspaceMemberRepository.countByWorkspaceIdAndRole(wsId, WorkspaceRole.OWNER);
            if (ownerCount <= 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "workspace must keep at least one owner");
            }
        }

        workspaceAuthorizationCleanupService.revokeWorkspaceGrantsCreatedBy(wsId, target.getPersonOaId());
        workspaceMemberRepository.delete(target);
        touchWorkspace(wsId);
        auditLogService.record(
                session.getPersonOaId(),
                com.x.cloudnext.server.audit.AuditAction.MEMBER_REMOVE,
                "WORKSPACE_MEMBER",
                target.getId(),
                "workspaceId", wsId,
                "memberPersonOaId", target.getPersonOaId(),
                "memberPersonName", target.getPersonName(),
                "role", target.getRole() == null ? "" : target.getRole().name());
    }

    /**
     * Membership + management-permission gate. Members and viewers are
     * blocked at this point so they never reach the member-management
     * branches below; the access-gateway matrix in
     * {@link com.x.cloudnext.server.drive.DriveScopeAccessService} (which is
     * not consulted on this code path because we already have the member
     * row in hand) and this entry point agree on the deny semantics.
     */
    private WorkspaceMember requireManagementMember(AuthLoginResponse session, String workspaceId) {
        WorkspaceMember member = requireMember(session, workspaceId);
        if (member.getRole() != WorkspaceRole.OWNER && member.getRole() != WorkspaceRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "workspace member management denied");
        }
        return member;
    }

    private WorkspaceMember requireOwnerMember(AuthLoginResponse session, String workspaceId) {
        WorkspaceMember member = requireMember(session, workspaceId);
        if (member.getRole() != WorkspaceRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "workspace owner required");
        }
        return member;
    }

    private Workspace requireActiveWorkspace(String workspaceId) {
        if (!StringUtils.hasText(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "workspace not found");
        }
        Workspace workspace = workspaceRepository.findById(workspaceId.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "workspace not found"));
        assertActiveWorkspace(workspace);
        return workspace;
    }

    private void assertActiveWorkspace(Workspace workspace) {
        if (!isActiveWorkspace(workspace)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "workspace not found");
        }
    }

    private boolean isActiveWorkspace(Workspace workspace) {
        return workspace != null && Workspace.STATUS_ACTIVE.equals(workspace.getStatus());
    }

    private String normalizeWorkspaceName(String rawName) {
        String name = StringUtils.hasText(rawName) ? rawName.trim() : "";
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workspace name is required");
        }
        if (name.length() > MAX_WORKSPACE_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workspace name too long");
        }
        return name;
    }

    /**
     * Parse a request-supplied role string to a {@link WorkspaceRole}.
     * Empty / unknown values become {@code 400} with a stable message so
     * callers can map errors without inspecting Jackson failures.
     */
    private WorkspaceRole parseRole(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "member role is required");
        }
        try {
            return WorkspaceRole.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "member role is invalid");
        }
    }

    /**
     * Load a member by id while enforcing that it belongs to the requested
     * workspace. Cross-workspace ids return 404 to avoid leaking the
     * existence of memberships in other workspaces.
     */
    private WorkspaceMember loadMemberInWorkspace(String memberId, String workspaceId) {
        if (!StringUtils.hasText(memberId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
        }
        WorkspaceMember member = workspaceMemberRepository.findById(memberId.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found"));
        if (!workspaceId.equals(member.getWorkspaceId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
        }
        return member;
    }

    /**
     * Bump the workspace's {@code updatedAt} so the list-my-workspaces sort
     * (Workspace.updatedAt desc) reflects member-management activity. The
     * timestamp refresh is driven by the {@code @PreUpdate} hook on
     * {@link Workspace}.
     */
    private void touchWorkspace(String workspaceId) {
        workspaceRepository.findById(workspaceId).ifPresent(workspace -> workspaceRepository.save(workspace));
    }

    /**
     * Stable sort rank used by {@link #listMembers} so the OWNER row
     * appears first regardless of insertion order. Unknown / null roles
     * sort to the bottom rather than throwing — defensive against rows
     * persisted by future migrations.
     */
    private int roleSortRank(WorkspaceRole role) {
        if (role == null) {
            return 99;
        }
        switch (role) {
            case OWNER:
                return 0;
            case ADMIN:
                return 1;
            case MEMBER:
                return 2;
            case VIEWER:
                return 3;
            default:
                return 99;
        }
    }

    private WorkspaceMemberResponse toMemberResponse(WorkspaceMember member) {
        WorkspaceMemberResponse r = new WorkspaceMemberResponse();
        r.setId(member.getId());
        r.setWorkspaceId(member.getWorkspaceId());
        r.setPersonOaId(member.getPersonOaId());
        r.setPersonKey(member.getPersonOaId());
        r.setPersonName(member.getPersonName());
        principalBindingRepository.findByCanonicalPersonId(member.getPersonOaId()).ifPresent(binding->r.setEmployeeNo(binding.getEmployeeNoSnapshot()));
        r.setRole(member.getRole() == null ? null : member.getRole().name());
        r.setCreatedAt(member.getCreatedAt());
        r.setUpdatedAt(member.getUpdatedAt());
        return r;
    }
}
