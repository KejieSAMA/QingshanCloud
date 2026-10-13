package com.x.cloudnext.server.drive;

import com.x.cloudnext.server.auth.AuthLoginResponse;

/**
 * Immutable bundle of the four facts a Scope-aware DriveService internal needs
 * to create, query, or mutate a {@link com.x.cloudnext.server.drive.entity.DriveItem}.
 *
 * <p>The split between {@link #scopeId} and {@link #ownerPersonOaId} is what
 * lets the same internal code path handle both PERSONAL and WORKSPACE writes:
 * <ul>
 *   <li>For PERSONAL scope, both equal the calling person's OA id.</li>
 *   <li>For WORKSPACE scope, {@code scopeId} is the Workspace id (so trash,
 *       member-bound listing, and the scope index all stay aligned), while
 *       {@code ownerPersonOaId} is whoever actually uploaded the file. The
 *       audit / OnlyOffice / share flows still use {@code ownerPersonOaId}
 *       to identify the principal, so the existing personal pipeline keeps
 *       working unchanged.</li>
 * </ul>
 *
 * <p>Constructed via the {@link #personal(AuthLoginResponse, String)} and
 * {@link #workspace(String, AuthLoginResponse, String)} factories — direct
 * instantiation is intentionally not exposed so callers cannot accidentally
 * mix scope identities.
 */
public final class DriveScopeContext {

    private final DriveScopeType scopeType;
    private final String scopeId;
    private final String ownerPersonOaId;
    private final String ownerName;

    private DriveScopeContext(DriveScopeType scopeType, String scopeId, String ownerPersonOaId, String ownerName) {
        this.scopeType = scopeType;
        this.scopeId = scopeId == null ? "" : scopeId;
        this.ownerPersonOaId = ownerPersonOaId == null ? "" : ownerPersonOaId;
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    /**
     * Build a personal-scope context. Mirrors the legacy DriveService behavior:
     * {@code scopeId == ownerPersonOaId == session.personOaId}.
     */
    public static DriveScopeContext personal(AuthLoginResponse session, String ownerName) {
        String personOaId = session == null ? "" : session.getPersonOaId();
        return new DriveScopeContext(DriveScopeType.PERSONAL, personOaId, personOaId, ownerName);
    }

    /**
     * Build a workspace-scope context. {@code ownerPersonOaId} is the caller
     * (whoever is performing the write) and {@code scopeId} is the Workspace id.
     */
    public static DriveScopeContext workspace(String workspaceId, AuthLoginResponse session, String actorName) {
        String personOaId = session == null ? "" : session.getPersonOaId();
        return new DriveScopeContext(DriveScopeType.WORKSPACE, workspaceId, personOaId, actorName);
    }

    /**
     * Generic factory used internally by {@code DriveService} when it needs to
     * reconstruct the scope of a pre-existing {@link com.x.cloudnext.server.drive.entity.DriveItem}
     * (for example, recursive trash traversal that started from
     * {@code DriveScopeAccessService.requireItem}). Prefer
     * {@link #personal(AuthLoginResponse, String)} or
     * {@link #workspace(String, AuthLoginResponse, String)} at call sites that
     * have a session in hand.
     */
    public static DriveScopeContext of(
            DriveScopeType scopeType, String scopeId, String ownerPersonOaId, String ownerName) {
        return new DriveScopeContext(scopeType, scopeId, ownerPersonOaId, ownerName);
    }

    public DriveScopeType getScopeType() {
        return scopeType;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getOwnerPersonOaId() {
        return ownerPersonOaId;
    }

    public String getOwnerName() {
        return ownerName;
    }
}
