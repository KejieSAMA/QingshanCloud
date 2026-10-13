package com.x.cloudnext.server.web;

import java.util.ArrayList;
import java.util.List;

public class CloudNextConfigResponse {

    private String authMode;
    private String authProvider;
    private Boolean authConfigured;
    private Integer syncBatchSize;
    private Integer syncIntervalMinutes;
    private String syncCron;
    private Boolean hasReadonlyJdbcSource;
    private String objectStorageEndpoint;
    private String objectStorageBucket;
    private String callbackBaseUrl;
    private String webBaseUrl;
    private String maxUploadFileSize;
    private Long maxUploadFileSizeBytes;
    private Integer trashRetentionDays;
    private Integer previewTicketTtlSeconds;
    private Boolean onlyofficePreviewConfigured;
    private Boolean onlyofficeJwtEnabled;
    private List<String> onlyofficeMissingConfigKeys = new ArrayList<>();
    private Boolean orgMirrorReady;
    private Boolean orgMirrorEmpty;
    private Boolean orgMirrorLastSyncSucceeded;

    public String getAuthMode() {
        return authMode;
    }

    public void setAuthMode(String authMode) {
        this.authMode = authMode;
    }
    public String getAuthProvider(){return authProvider;} public void setAuthProvider(String v){authProvider=v;}
    public Boolean getAuthConfigured(){return authConfigured;} public void setAuthConfigured(Boolean v){authConfigured=v;}

    public Integer getSyncBatchSize() {
        return syncBatchSize;
    }

    public void setSyncBatchSize(Integer syncBatchSize) {
        this.syncBatchSize = syncBatchSize;
    }

    public Integer getSyncIntervalMinutes() {
        return syncIntervalMinutes;
    }

    public void setSyncIntervalMinutes(Integer syncIntervalMinutes) {
        this.syncIntervalMinutes = syncIntervalMinutes;
    }

    public String getSyncCron() {
        return syncCron;
    }

    public void setSyncCron(String syncCron) {
        this.syncCron = syncCron;
    }

    public Boolean getHasReadonlyJdbcSource() {
        return hasReadonlyJdbcSource;
    }

    public void setHasReadonlyJdbcSource(Boolean hasReadonlyJdbcSource) {
        this.hasReadonlyJdbcSource = hasReadonlyJdbcSource;
    }

    public String getObjectStorageEndpoint() {
        return objectStorageEndpoint;
    }

    public void setObjectStorageEndpoint(String objectStorageEndpoint) {
        this.objectStorageEndpoint = objectStorageEndpoint;
    }

    public String getObjectStorageBucket() {
        return objectStorageBucket;
    }

    public void setObjectStorageBucket(String objectStorageBucket) {
        this.objectStorageBucket = objectStorageBucket;
    }

    public String getCallbackBaseUrl() {
        return callbackBaseUrl;
    }

    public void setCallbackBaseUrl(String callbackBaseUrl) {
        this.callbackBaseUrl = callbackBaseUrl;
    }

    public String getWebBaseUrl() {
        return webBaseUrl;
    }

    public void setWebBaseUrl(String webBaseUrl) {
        this.webBaseUrl = webBaseUrl;
    }

    public String getMaxUploadFileSize() {
        return maxUploadFileSize;
    }

    public void setMaxUploadFileSize(String maxUploadFileSize) {
        this.maxUploadFileSize = maxUploadFileSize;
    }

    public Long getMaxUploadFileSizeBytes() {
        return maxUploadFileSizeBytes;
    }

    public void setMaxUploadFileSizeBytes(Long maxUploadFileSizeBytes) {
        this.maxUploadFileSizeBytes = maxUploadFileSizeBytes;
    }

    public Integer getTrashRetentionDays() {
        return trashRetentionDays;
    }

    public void setTrashRetentionDays(Integer trashRetentionDays) {
        this.trashRetentionDays = trashRetentionDays;
    }

    public Integer getPreviewTicketTtlSeconds() {
        return previewTicketTtlSeconds;
    }

    public void setPreviewTicketTtlSeconds(Integer previewTicketTtlSeconds) {
        this.previewTicketTtlSeconds = previewTicketTtlSeconds;
    }

    public Boolean getOnlyofficePreviewConfigured() {
        return onlyofficePreviewConfigured;
    }

    public void setOnlyofficePreviewConfigured(Boolean onlyofficePreviewConfigured) {
        this.onlyofficePreviewConfigured = onlyofficePreviewConfigured;
    }

    public Boolean getOnlyofficeJwtEnabled() {
        return onlyofficeJwtEnabled;
    }

    public void setOnlyofficeJwtEnabled(Boolean onlyofficeJwtEnabled) {
        this.onlyofficeJwtEnabled = onlyofficeJwtEnabled;
    }

    public List<String> getOnlyofficeMissingConfigKeys() {
        return onlyofficeMissingConfigKeys;
    }

    public void setOnlyofficeMissingConfigKeys(List<String> onlyofficeMissingConfigKeys) {
        this.onlyofficeMissingConfigKeys = onlyofficeMissingConfigKeys;
    }

    public Boolean getOrgMirrorReady() {
        return orgMirrorReady;
    }

    public void setOrgMirrorReady(Boolean orgMirrorReady) {
        this.orgMirrorReady = orgMirrorReady;
    }

    public Boolean getOrgMirrorEmpty() {
        return orgMirrorEmpty;
    }

    public void setOrgMirrorEmpty(Boolean orgMirrorEmpty) {
        this.orgMirrorEmpty = orgMirrorEmpty;
    }

    public Boolean getOrgMirrorLastSyncSucceeded() {
        return orgMirrorLastSyncSucceeded;
    }

    public void setOrgMirrorLastSyncSucceeded(Boolean orgMirrorLastSyncSucceeded) {
        this.orgMirrorLastSyncSucceeded = orgMirrorLastSyncSucceeded;
    }
}
