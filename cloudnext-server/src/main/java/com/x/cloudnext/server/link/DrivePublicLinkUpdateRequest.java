package com.x.cloudnext.server.link;

/**
 * Request body for {@code PATCH /api/links/{id}} (iteration two,
 * design §"DrivePublicLinkService" 管理端方法).
 *
 * <p>This is a tri-state partial update DTO: every field uses a wrapper
 * type so the service layer can distinguish between "not supplied",
 * "supplied as empty/clearing sentinel", and "supplied with a real value".
 *
 * <p>Per-field semantics applied by {@code DrivePublicLinkService.update}:
 * <ul>
 *   <li>{@code permission} — {@code null} leaves the current value
 *       untouched. A non-null value must be {@code PREVIEW} or
 *       {@code DOWNLOAD}.</li>
 *   <li>{@code password} — {@code null} leaves the current
 *       {@code passwordHash} untouched. An empty string clears the
 *       password (stores the empty-string sentinel). A non-blank value is
 *       re-hashed with {@code publicLinkPasswordEncoder}. Plaintext is
 *       never stored.</li>
 *   <li>{@code downloadLimit} — {@code null} leaves the current value
 *       untouched. Otherwise must be either {@code -1} (explicit unlimited)
 *       or in the inclusive range {@code [1, 1_000_000]}.</li>
 *   <li>{@code expiresAt} — {@code null} leaves the current value
 *       untouched. An empty string clears the expiry (link becomes
 *       permanent). A non-blank value must parse as ISO-8601 and lie
 *       strictly in the future.</li>
 * </ul>
 */
public class DrivePublicLinkUpdateRequest {

    private String permission;
    private String password;
    private Long downloadLimit;
    private String expiresAt;

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Long getDownloadLimit() {
        return downloadLimit;
    }

    public void setDownloadLimit(Long downloadLimit) {
        this.downloadLimit = downloadLimit;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }
}
