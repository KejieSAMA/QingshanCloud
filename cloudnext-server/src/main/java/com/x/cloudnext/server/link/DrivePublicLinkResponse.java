package com.x.cloudnext.server.link;

/**
 * Response projection for {@code DrivePublicLink} on the management
 * surface ({@code POST/GET/PATCH/POST .../revoke /api/links*}).
 *
 * <p>Exposes only fields the link creator needs in the LinksPage UI and
 * deliberately omits the persisted {@code passwordHash}: only
 * {@link #requirePassword} (a derived boolean) is sent over the wire so
 * the on-disk hash never leaks.
 *
 * <p>{@code shareUrl} composition rules
 * (design §"DrivePublicLinkService" / §"Configuration Inventory"):
 * <ul>
 *   <li>If {@code cloudnext.public-link.base-url} is configured, the URL
 *       is {@code base-url + "/" + token} (any trailing slash on the base
 *       URL is dropped).</li>
 *   <li>If the base URL is blank, the URL falls back to the relative
 *       form {@code "/" + token}, leaving the front-end to attach the
 *       current origin.</li>
 * </ul>
 */
public class DrivePublicLinkResponse {

    private String id;
    private String token;
    private String itemId;
    private String fileName;
    private String permission;
    private boolean requirePassword;
    private String expiresAt;
    private long downloadLimit;
    private long downloadCount;
    private boolean revoked;
    private String createdAt;
    private String updatedAt;
    private String shareUrl;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public boolean isRequirePassword() {
        return requirePassword;
    }

    public void setRequirePassword(boolean requirePassword) {
        this.requirePassword = requirePassword;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public long getDownloadLimit() {
        return downloadLimit;
    }

    public void setDownloadLimit(long downloadLimit) {
        this.downloadLimit = downloadLimit;
    }

    public long getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(long downloadCount) {
        this.downloadCount = downloadCount;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getShareUrl() {
        return shareUrl;
    }

    public void setShareUrl(String shareUrl) {
        this.shareUrl = shareUrl;
    }
}
