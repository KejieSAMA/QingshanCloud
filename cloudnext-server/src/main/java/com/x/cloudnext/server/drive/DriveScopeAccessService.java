package com.x.cloudnext.server.drive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.auth.AuthSessionService;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;
import com.x.cloudnext.server.workspace.entity.WorkspaceMember;
import com.x.cloudnext.server.workspace.repository.WorkspaceMemberRepository;
import com.x.cloudnext.server.workspace.entity.Workspace;
import com.x.cloudnext.server.workspace.repository.WorkspaceRepository;

/**
 * Access gateway for DriveItem operations across all Scopes (PERSONAL / WORKSPACE).
 *
 * <p>This service is the single decision point for "can the current session perform
 * {@link DriveScopeAction} on this {@link DriveItem}?". It implements the 4-role × 5-action
 * permission matrix defined in
 * {@code .kiro/specs/cloudnext-iteration-plan/design.md} → §"DriveScopeAccessService".
 *
 * <p><b>Wiring status:</b> task 0.4 introduced this service as a skeleton; task 0.5 swapped
 * the existing call sites in {@code DriveService} / {@code DriveShareService} /
 * {@code DriveReadService.getPath} from {@link DriveAccessService#requireOwnedItem(String, String)}
 * over to {@link #requireItem(AuthLoginResponse, String, DriveScopeAction)}. The remaining
 * {@code requireOwnedItem} call sites are intentional: {@code DriveAccessService} itself,
 * its internal {@code assertParentAccessible} helper, and the OnlyOffice ticket-based
 * preview/content path which trusts a server-signed payload owner instead of a session.
 *
 * <p><b>WORKSPACE branch:</b> task 1.1 introduced the {@code Workspace} /
 * {@code WorkspaceMember} entities and wired
 * {@link #resolveWorkspaceRole(AuthLoginResponse, String)} to
 * {@link WorkspaceMemberRepository#findByWorkspaceIdAndPersonOaId(String, String)}.
 * Non-members resolve to {@code null} so the matrix logic in {@link #canActOn} denies them.
 */
@Service
public class DriveScopeAccessService {

    private final DriveItemRepository driveItemRepository;
    private final AuthSessionService authSessionService;
    private final DriveShareService driveShareService;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceRepository workspaceRepository;

    public DriveScopeAccessService(
            DriveItemRepository driveItemRepository,
            AuthSessionService authSessionService,
            @Lazy DriveShareService driveShareService,
            WorkspaceMemberRepository workspaceMemberRepository,
            WorkspaceRepository workspaceRepository) {
        this.driveItemRepository = driveItemRepository;
        this.authSessionService = authSessionService;
        // @Lazy avoids a constructor-time cycle: DriveShareService depends on
        // DriveAccessService and may transitively need DriveScopeAccessService once
        // task 0.5 swaps the call sites.
        this.driveShareService = driveShareService;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * Resolve the auth session associated with the given token, raising 401 if absent.
     *
     * <p>Convenience helper so callers do not need to also wire {@link AuthSessionService}.
     */
    public AuthLoginResponse requireSession(String token) {
        AuthLoginResponse session = authSessionService.currentSession(token);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "cloudnext session not found");
        }
        return session;
    }

    /**
     * Load a DriveItem by id and assert that {@code session} may perform {@code action} on it.
     *
     * <p>Error semantics:
     * <ul>
     *   <li>404 {@code item not found} when the id has no row (regardless of scope/owner).</li>
     *   <li>404 {@code item not found} when a PERSONAL-scope item exists but the caller is
     *       neither the owner nor an active share recipient. This matches the legacy
     *       {@link DriveAccessService#requireOwnedItem(String, String)} contract that hides
     *       the existence of items the caller cannot see (security-by-obscurity preserved
     *       so existing personal-flow tests / clients keep working unchanged).</li>
     *   <li>403 {@code workspace access denied} when the caller is not a member of the
     *       containing Workspace.</li>
     *   <li>403 {@code workspace write denied} when a Workspace VIEWER attempts a non-READ
     *       action.</li>
     *   <li>403 {@code workspace access denied} for any other Workspace-scope denial.</li>
     * </ul>
     */
    public DriveItem requireItem(AuthLoginResponse session, String itemId, DriveScopeAction action) {
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "cloudnext session not found");
        }
        if (action == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action is required");
        }
        DriveItem item = driveItemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found"));
        if (canActOn(session, item, action)) {
            return item;
        }
        throw buildDenialException(session, item, action);
    }

    /**
     * Pure predicate: can {@code session} perform {@code action} on {@code item}?
     *
     * <p>Returns {@code false} (never throws) for null inputs, deleted items left aside —
     * deletion semantics are enforced by the calling Service, not by the access gateway.
     */
    public boolean canActOn(AuthLoginResponse session, DriveItem item, DriveScopeAction action) {
        if (session == null || item == null || action == null) {
            return false;
        }
        DriveScopeType scopeType = item.getScopeType();
        if (scopeType == DriveScopeType.WORKSPACE) {
            WorkspaceRole role = resolveWorkspaceRole(session, item.getScopeId());
            if (role == null) {
                return false; // non-member: all actions denied
            }
            return workspaceMatrixAllows(role, action);
        }
        // PERSONAL scope
        if (isOwner(session, item)) {
            // owner can perform all actions on their personal items.
            // MANAGE_MEMBER is "n/a" for PERSONAL scope but we report true for the
            // benefit of generic callers; in practice PERSONAL items have no member set.
            return true;
        }
        // Non-owner: only READ may be allowed via shares.
        if (action == DriveScopeAction.READ) {
            return delegateToShareReadCheck(session, item);
        }
        return false;
    }

    /**
     * N+2.7 F6: 批量权限过滤，避免逐项 {@link #canActOn} 调用导致的 N 次重复
     * workspace role 查询。
     *
     * <p>策略：先按 scope 分组（PERSONAL owner / PERSONAL 共享 / 按 workspaceId 聚合的
     * WORKSPACE），每个 workspaceId 只查一次 role；PERSONAL owner 不查；PERSONAL 共享
     * 仍逐项委托 share 检查（DriveShareService 当前无批量 API；该场景在搜索结果
     * 中占比小，本期不优化）。
     *
     * <p>语义与 {@link #canActOn} 完全一致 — 仅做调用次数压缩。
     */
    public List<DriveItem> filterByAccess(
            AuthLoginResponse session, List<DriveItem> items, DriveScopeAction action) {
        if (session == null || items == null || items.isEmpty() || action == null) {
            return Collections.emptyList();
        }
        List<DriveItem> personalOwned = new ArrayList<>();
        List<DriveItem> personalShared = new ArrayList<>();
        Map<String, List<DriveItem>> workspaceGroups = new HashMap<>();
        for (DriveItem item : items) {
            if (item == null) {
                continue;
            }
            DriveScopeType scopeType = item.getScopeType();
            if (scopeType == DriveScopeType.WORKSPACE) {
                workspaceGroups
                        .computeIfAbsent(item.getScopeId() == null ? "" : item.getScopeId(),
                                k -> new ArrayList<>())
                        .add(item);
            } else if (isOwner(session, item)) {
                personalOwned.add(item);
            } else {
                personalShared.add(item);
            }
        }

        List<DriveItem> allowed = new ArrayList<>(personalOwned);

        for (Map.Entry<String, List<DriveItem>> entry : workspaceGroups.entrySet()) {
            WorkspaceRole role = resolveWorkspaceRole(session, entry.getKey());
            if (role != null && workspaceMatrixAllows(role, action)) {
                allowed.addAll(entry.getValue());
            }
        }

        if (action == DriveScopeAction.READ && !personalShared.isEmpty()) {
            for (DriveItem item : personalShared) {
                if (delegateToShareReadCheck(session, item)) {
                    allowed.add(item);
                }
            }
        }
        return allowed;
    }

    /**
     * Resolve the caller's role within a Workspace, or {@code null} when they are not a member.
     *
     * <p>Backed by {@link WorkspaceMemberRepository#findByWorkspaceIdAndPersonOaId(String, String)}.
     * The lookup is keyed by the Workspace id (which equals {@code DriveItem.scopeId} for
     * any item with {@code scopeType=WORKSPACE}) and the caller's {@code personOaId}.
     *
     * <p>Returns {@code null} when {@code session} is missing, {@code workspaceId} is
     * empty, or the {@code (workspaceId, personOaId)} pair has no membership row. The
     * caller-facing matrix logic in {@link #canActOn} treats {@code null} as "non-member".
     */
    public WorkspaceRole resolveWorkspaceRole(AuthLoginResponse session, String workspaceId) {
        if (session == null || workspaceId == null || workspaceId.isEmpty()) {
            return null;
        }
        boolean active = workspaceRepository.findById(workspaceId)
                .map(workspace -> Workspace.STATUS_ACTIVE.equals(workspace.getStatus()))
                .orElse(false);
        if (!active) {
            return null;
        }
        return workspaceMemberRepository
                .findByWorkspaceIdAndPersonOaId(workspaceId, session.getPersonOaId())
                .map(WorkspaceMember::getRole)
                .orElse(null);
    }

    // -- internals ---------------------------------------------------------

    /**
     * Apply the WORKSPACE permission matrix once the caller's role has been resolved.
     *
     * <pre>
     *  Role     | READ | WRITE | DELETE | MANAGE_SHARE | MANAGE_MEMBER
     *  ---------+------+-------+--------+--------------+---------------
     *  VIEWER   |  ✔   |  ✘    |  ✘     |  ✘           |  ✘
     *  MEMBER   |  ✔   |  ✔    |  ✔     |  ✔           |  ✘
     *  ADMIN    |  ✔   |  ✔    |  ✔     |  ✔           |  ✔ (*)
     *  OWNER    |  ✔   |  ✔    |  ✔     |  ✔           |  ✔
     * </pre>
     *
     * <p>(*) ADMIN cannot modify or remove the OWNER. That refinement lives in
     * {@code WorkspaceService} (task 1.6) because it depends on the target member, not the
     * caller. This matrix only answers "may the caller access member management at all".
     */
    private boolean workspaceMatrixAllows(WorkspaceRole role, DriveScopeAction action) {
        switch (role) {
            case OWNER:
                return true;
            case ADMIN:
                return true;
            case MEMBER:
                return action != DriveScopeAction.MANAGE_MEMBER;
            case VIEWER:
                return action == DriveScopeAction.READ;
            default:
                return false;
        }
    }

    /**
     * Personal-scope share lookup. Returns {@code true} when an active share grants the
     * caller at least preview-level access to the item (the share's effective permission
     * itself is checked by {@link DriveShareService} during real read flows).
     */
    private boolean delegateToShareReadCheck(AuthLoginResponse session, DriveItem item) {
        try {
            return driveShareService.resolveAccess(session, item).isPresent();
        } catch (RuntimeException e) {
            // The access gateway is a predicate; never let a downstream lookup error
            // bubble up as an authorization decision. Caller-facing error semantics come
            // from requireItem(...).
            return false;
        }
    }

    private boolean isOwner(AuthLoginResponse session, DriveItem item) {
        String personOaId = session.getPersonOaId();
        return personOaId != null && personOaId.equals(item.getOwnerPersonOaId());
    }

    private ResponseStatusException buildDenialException(
            AuthLoginResponse session, DriveItem item, DriveScopeAction action) {
        if (item.getScopeType() == DriveScopeType.WORKSPACE) {
            WorkspaceRole role = resolveWorkspaceRole(session, item.getScopeId());
            if (role == null) {
                return new ResponseStatusException(HttpStatus.FORBIDDEN, "workspace access denied");
            }
            if (role == WorkspaceRole.VIEWER && action != DriveScopeAction.READ) {
                return new ResponseStatusException(HttpStatus.FORBIDDEN, "workspace write denied");
            }
            return new ResponseStatusException(HttpStatus.FORBIDDEN, "workspace access denied");
        }
        // PERSONAL scope: preserve the legacy DriveAccessService.requireOwnedItem contract
        // which hides the existence of items the caller cannot see by returning 404
        // ("item not found") instead of 403. This keeps existing personal-flow tests and
        // API clients (e.g. the Vue UI's not-found handling) working unchanged after task
        // 0.5 swaps the call sites over to requireItem(...).
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found");
    }
}
