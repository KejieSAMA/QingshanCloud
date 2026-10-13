package com.x.cloudnext.server.drive;

import java.util.Locale;

import com.x.cloudnext.server.auth.AuthLoginResponse;

/**
 * Immutable access-subject descriptor used by Drive preview / download flows.
 *
 * <p>This carrier replaces the implicit "the calling person is always a logged-in
 * user" assumption that the legacy {@code DriveService.getPreview} relied on.
 * Once {@code DrivePublicLinkService} (task 2.5) starts driving previews from an
 * anonymous public-link request, the same per-item logic in
 * {@link DriveService#buildPreviewForItem(com.x.cloudnext.server.drive.entity.DriveItem, AccessContext)}
 * needs to know:
 * <ul>
 *   <li>Who the audit subject is (a logged-in user vs. a public-link token).</li>
 *   <li>Whether preview / download are allowed (so OnlyOffice ticket and
 *       {@code allowDownload} flag are populated identically to today).</li>
 *   <li>What permission label to embed in the OnlyOffice ticket payload.</li>
 * </ul>
 *
 * <p>Java 11 has no {@code sealed} keyword, so this is modeled as a final class
 * with a private constructor and a small set of factory methods. The two valid
 * "kinds" (USER / PUBLIC_LINK) are exposed via {@link Kind} and are mutually
 * exclusive — a USER context never carries a public-link token, and a
 * PUBLIC_LINK context never carries a session. The corresponding accessors
 * intentionally throw {@link IllegalStateException} when called on the wrong
 * kind so misuse fails loudly instead of silently producing a wrong audit row.
 *
 * <p><b>Behavior preservation note:</b> {@link #user(AuthLoginResponse)} models
 * "owner-equivalent" access (preview + download both allowed, permission label
 * {@code "edit"}). The legacy {@code getPreview} path goes through
 * {@link #user(AuthLoginResponse, DriveResolvedShareAccess)} so that the
 * existing share semantics — including read-only shares whose
 * {@code allowDownload} must remain {@code false} — are preserved exactly.
 */
public final class AccessContext {

    /** Kind of access subject. */
    public enum Kind {
        /** Authenticated CloudNext user (personal drive, shared file, workspace). */
        USER,
        /** Anonymous request authenticated only by a public-link token. */
        PUBLIC_LINK
    }

    private final Kind kind;
    private final AuthLoginResponse session;
    private final String publicLinkToken;
    private final boolean previewAllowed;
    private final boolean downloadAllowed;
    private final String effectivePermission;

    private AccessContext(
            Kind kind,
            AuthLoginResponse session,
            String publicLinkToken,
            boolean previewAllowed,
            boolean downloadAllowed,
            String effectivePermission) {
        this.kind = kind;
        this.session = session;
        this.publicLinkToken = publicLinkToken == null ? "" : publicLinkToken;
        this.previewAllowed = previewAllowed;
        this.downloadAllowed = downloadAllowed;
        this.effectivePermission = effectivePermission == null ? "" : effectivePermission;
    }

    /**
     * Owner-equivalent USER context. Preview and download are both allowed, and
     * {@link #getEffectivePermission()} reports {@code "edit"}.
     *
     * <p>Use this factory when the caller already knows the session has full
     * access to the target {@link com.x.cloudnext.server.drive.entity.DriveItem}
     * (for example, an internal flow that has bypassed share resolution).
     */
    public static AccessContext user(AuthLoginResponse session) {
        return new AccessContext(Kind.USER, session, "", true, true, "edit");
    }

    /**
     * USER context derived from a {@link DriveResolvedShareAccess}. The
     * preview / download allowances and the permission label are taken straight
     * from the resolved share, so the resulting AccessContext mirrors the
     * legacy {@code getPreview} payload byte-for-byte (read-only shares stay
     * read-only, owner / EDIT shares get download, etc.).
     */
    public static AccessContext user(AuthLoginResponse session, DriveResolvedShareAccess access) {
        boolean previewAllowed = access != null && access.allowsPreview();
        boolean downloadAllowed = access != null && access.allowsDownload();
        String permission = access == null || access.getEffectivePermission() == null
                ? ""
                : access.getEffectivePermission().name().toLowerCase(Locale.ROOT);
        return new AccessContext(Kind.USER, session, "", previewAllowed, downloadAllowed, permission);
    }

    /**
     * USER context with explicit preview / download / permission, used by
     * Workspace-scope preview flows where the role matrix (in
     * {@link DriveScopeAccessService}) — not a {@link DriveResolvedShareAccess} —
     * determines the caller's allowances.
     *
     * <p>Typical mapping (caller-side; this method does not interpret the role):
     * <ul>
     *   <li>OWNER / ADMIN / MEMBER → ({@code true}, {@code true}, {@code "edit"})</li>
     *   <li>VIEWER → ({@code true}, {@code false}, {@code "read"})</li>
     * </ul>
     */
    public static AccessContext userWithPermissions(
            AuthLoginResponse session,
            boolean previewAllowed,
            boolean downloadAllowed,
            String permission) {
        return new AccessContext(Kind.USER, session, "", previewAllowed, downloadAllowed,
                permission == null ? "" : permission);
    }

    /**
     * PUBLIC_LINK context. The audit subject is the {@code token} string; the
     * permission label is derived from {@code downloadAllowed} so the
     * OnlyOffice ticket payload still carries a non-empty value.
     */
    public static AccessContext publicLink(String token, boolean previewAllowed, boolean downloadAllowed) {
        String permission = downloadAllowed ? "download" : "read";
        return new AccessContext(Kind.PUBLIC_LINK, null, token, previewAllowed, downloadAllowed, permission);
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * @return the underlying CloudNext session.
     * @throws IllegalStateException if this context is not of kind {@link Kind#USER}.
     */
    public AuthLoginResponse getSession() {
        requireKind(Kind.USER);
        return session;
    }

    /**
     * @return the calling person's OA id.
     * @throws IllegalStateException if this context is not of kind {@link Kind#USER}.
     */
    public String getUserId() {
        requireKind(Kind.USER);
        return session == null || session.getPersonOaId() == null ? "" : session.getPersonOaId();
    }

    /**
     * @return the public-link token used to identify the audit subject.
     * @throws IllegalStateException if this context is not of kind {@link Kind#PUBLIC_LINK}.
     */
    public String getPublicLinkToken() {
        requireKind(Kind.PUBLIC_LINK);
        return publicLinkToken;
    }

    public boolean isPreviewAllowed() {
        return previewAllowed;
    }

    public boolean isDownloadAllowed() {
        return downloadAllowed;
    }

    /**
     * Lowercase permission label embedded in the OnlyOffice ticket payload.
     * For USER contexts derived from a share, this matches
     * {@code DriveSharePermission.name().toLowerCase()}; for PUBLIC_LINK
     * contexts, this is {@code "download"} or {@code "read"}.
     */
    public String getEffectivePermission() {
        return effectivePermission;
    }

    private void requireKind(Kind expected) {
        if (kind != expected) {
            throw new IllegalStateException(
                    "AccessContext kind mismatch: expected " + expected + " but was " + kind);
        }
    }
}
