package com.x.cloudnext.server.drive;

import java.util.ArrayList;
import java.util.List;

public class DrivePreviewTicketPayload {
    private String sourceIdentity;
    private String publicAccessTicket;
    public String getSourceIdentity(){return sourceIdentity;} public void setSourceIdentity(String v){sourceIdentity=v;}
    public String getPublicAccessTicket(){return publicAccessTicket;} public void setPublicAccessTicket(String v){publicAccessTicket=v;}
    private String driveId;
    private String publicLinkToken;
    public String getPublicLinkToken() { return publicLinkToken; }
    public void setPublicLinkToken(String v) { publicLinkToken = v; }
    private String tenantId;
    private String formEmployeeId;
    private long authVersion;
    public String getDriveId() { return driveId; }
    public void setDriveId(String v) { driveId = v; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String v) { tenantId = v; }
    public String getFormEmployeeId() { return formEmployeeId; }
    public void setFormEmployeeId(String v) { formEmployeeId = v; }
    public long getAuthVersion() { return authVersion; }
    public void setAuthVersion(long v) { authVersion = v; }
    private String versionId;
    public String getVersionId() { return versionId; }
    public void setVersionId(String value) { versionId = value; }

    private String itemId;
    private String ownerPersonOaId;
    private String viewerPersonOaId;
    private String viewerName;
    private String viewerPersonOaIdRaw;
    private List<String> viewerIdentityIds = new ArrayList<>();
    private List<String> viewerUnitIds = new ArrayList<>();
    private String effectivePermission;
    private boolean allowDownload;
    private boolean allowEdit;
    private String scopeType;
    private String scopeId;
    private String documentKey;
    private long expiresAt;
    private long issuedAt;

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getOwnerPersonOaId() {
        return ownerPersonOaId;
    }

    public void setOwnerPersonOaId(String ownerPersonOaId) {
        this.ownerPersonOaId = ownerPersonOaId;
    }

    public String getViewerPersonOaId() {
        return viewerPersonOaId;
    }

    public void setViewerPersonOaId(String viewerPersonOaId) {
        this.viewerPersonOaId = viewerPersonOaId;
    }

    public String getViewerName() {
        return viewerName;
    }

    public void setViewerName(String viewerName) {
        this.viewerName = viewerName;
    }

    public String getViewerPersonOaIdRaw() {
        return viewerPersonOaIdRaw;
    }

    public void setViewerPersonOaIdRaw(String viewerPersonOaIdRaw) {
        this.viewerPersonOaIdRaw = viewerPersonOaIdRaw;
    }

    public List<String> getViewerIdentityIds() {
        return viewerIdentityIds;
    }

    public void setViewerIdentityIds(List<String> viewerIdentityIds) {
        this.viewerIdentityIds = viewerIdentityIds == null ? new ArrayList<>() : viewerIdentityIds;
    }

    public List<String> getViewerUnitIds() {
        return viewerUnitIds;
    }

    public void setViewerUnitIds(List<String> viewerUnitIds) {
        this.viewerUnitIds = viewerUnitIds == null ? new ArrayList<>() : viewerUnitIds;
    }

    public String getEffectivePermission() {
        return effectivePermission;
    }

    public void setEffectivePermission(String effectivePermission) {
        this.effectivePermission = effectivePermission;
    }

    public boolean isAllowDownload() {
        return allowDownload;
    }

    public void setAllowDownload(boolean allowDownload) {
        this.allowDownload = allowDownload;
    }

    public boolean isAllowEdit() {
        return allowEdit;
    }

    public void setAllowEdit(boolean allowEdit) {
        this.allowEdit = allowEdit;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public String getDocumentKey() {
        return documentKey;
    }

    public void setDocumentKey(String documentKey) {
        this.documentKey = documentKey;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }
    public long getIssuedAt(){return issuedAt;} public void setIssuedAt(long v){issuedAt=v;}
}
