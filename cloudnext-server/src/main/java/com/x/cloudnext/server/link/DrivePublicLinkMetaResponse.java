package com.x.cloudnext.server.link;

/**
 * Sanitized public-end projection returned by
 * {@code GET /public/links/{token}/meta} (iteration two,
 * Requirement 8.1 / 8.2 — design §"DrivePublicLinkService" 元信息脱敏).
 *
 * <p>Per Requirement 8.2, this DTO MUST NOT carry any field that is not on
 * the design's whitelist:
 * <ul>
 *   <li>{@code fileName} — the underlying DriveItem name; empty string when
 *       the item has been hard- or soft-deleted out from under the link.</li>
 *   <li>{@code fileType} — coarse-grained type label sourced from
 *       {@code DriveItem.fileType} (e.g. {@code "image"}, {@code "doc"},
 *       {@code "pdf"}, {@code "file"}); empty when the item is gone.</li>
 *   <li>{@code permission} — {@code "preview"} or {@code "download"},
 *       lowercased.</li>
 *   <li>{@code requirePassword} — derived: {@code true} iff the link has a
 *       non-empty {@code passwordHash}. The hash itself is never sent.</li>
 *   <li>{@code open} — composite "is this link still usable right now"
 *       indicator. {@code false} when the link is revoked, past its
 *       {@code expiresAt}, has hit its {@code downloadLimit}, or its
 *       underlying DriveItem is missing / soft-deleted.</li>
 * </ul>
 *
 * <p>Fields explicitly omitted (would leak owner / scope information):
 * {@code creatorName}, {@code creatorPersonOaId}, {@code itemId},
 * {@code expiresAt}, {@code downloadLimit}, {@code downloadCount}, the
 * password hash, the link id and the token. The token is part of the URL
 * path the caller already holds, so it is also not echoed in the body.
 */
public class DrivePublicLinkMetaResponse {

    private String fileName;
    private String fileType;
    private String permission;
    private boolean requirePassword;
    private boolean open;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
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

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }
}
