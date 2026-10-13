package com.x.cloudnext.server.drive;

public class DriveShareResponse {

    private String id;
    private DriveItemResponse item;
    private String ownerPersonOaId;
    private String ownerName;
    private String targetType;
    private String targetId;
    private String targetName;
    private String permission;
    private String expiresAt;
    private Boolean revoked;
    private String createdAt;
    private String updatedAt;
    private String status;
    private String shareId;
    private String effectivePermission;
    private Boolean allowPreview;
    private Boolean allowDownload;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DriveItemResponse getItem() {
        return item;
    }

    public void setItem(DriveItemResponse item) {
        this.item = item;
    }

    public String getOwnerPersonOaId() {
        return ownerPersonOaId;
    }

    public void setOwnerPersonOaId(String ownerPersonOaId) {
        this.ownerPersonOaId = ownerPersonOaId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Boolean getRevoked() {
        return revoked;
    }

    public void setRevoked(Boolean revoked) {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getShareId() {
        return shareId;
    }

    public void setShareId(String shareId) {
        this.shareId = shareId;
    }

    public String getEffectivePermission() {
        return effectivePermission;
    }

    public void setEffectivePermission(String effectivePermission) {
        this.effectivePermission = effectivePermission;
    }

    public Boolean getAllowPreview() {
        return allowPreview;
    }

    public void setAllowPreview(Boolean allowPreview) {
        this.allowPreview = allowPreview;
    }

    public Boolean getAllowDownload() {
        return allowDownload;
    }

    public void setAllowDownload(Boolean allowDownload) {
        this.allowDownload = allowDownload;
    }
}
