package com.x.cloudnext.server.link;

/**
 * Request body for {@code POST /api/links} (iteration two,
 * design §"DrivePublicLinkService" 管理端方法).
 *
 * <p>All fields except {@code itemId} and {@code permission} are optional.
 * The DTO sticks to nullable wrappers and {@link String} sentinels so the
 * service layer can apply the same validation rules whether the request
 * arrives from JSON or from a service-direct caller (integration tests).
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code itemId} — id of the {@code DriveItem} to share. Required.</li>
 *   <li>{@code permission} — {@code PREVIEW} or {@code DOWNLOAD}. Required.
 *       Case-insensitive at the service layer.</li>
 *   <li>{@code password} — optional access password. {@code null} or blank
 *       means "no password" and is stored as the empty-string sentinel in
 *       {@code DrivePublicLink.passwordHash}. A non-blank value is hashed
 *       with the {@code publicLinkPasswordEncoder} bean before persistence.
 *       Plaintext is never stored.</li>
 *   <li>{@code downloadLimit} — {@code null} resolves to {@code -1}
 *       (unlimited). Otherwise must be either {@code -1} (explicit
 *       unlimited) or in the inclusive range {@code [1, 1_000_000]}.</li>
 *   <li>{@code expiresAt} — optional ISO-8601 instant. Blank means "never
 *       expires"; non-blank values must parse and lie strictly in the
 *       future.</li>
 * </ul>
 */
public class DrivePublicLinkCreateRequest {

    private String itemId;
    private String permission;
    private String password;
    private Long downloadLimit;
    private String expiresAt;

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

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
