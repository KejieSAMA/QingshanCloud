package com.x.cloudnext.server.drive;

public class DrivePreviewResponse {

    private String itemId;
    private String kind;
    private Boolean supported;
    private String mode;
    private String fileName;
    private String contentType;
    private String previewUrl;
    private String previewTicket;
    private String previewPageUrl;
    private Boolean allowDownload;
    private String unavailableCode;
    private String unavailableMessage;

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public Boolean getSupported() {
        return supported;
    }

    public void setSupported(Boolean supported) {
        this.supported = supported;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public String getPreviewTicket() {
        return previewTicket;
    }

    public void setPreviewTicket(String previewTicket) {
        this.previewTicket = previewTicket;
    }

    public String getPreviewPageUrl() {
        return previewPageUrl;
    }

    public void setPreviewPageUrl(String previewPageUrl) {
        this.previewPageUrl = previewPageUrl;
    }

    public Boolean getAllowDownload() {
        return allowDownload;
    }

    public void setAllowDownload(Boolean allowDownload) {
        this.allowDownload = allowDownload;
    }

    public String getUnavailableCode() {
        return unavailableCode;
    }

    public void setUnavailableCode(String unavailableCode) {
        this.unavailableCode = unavailableCode;
    }

    public String getUnavailableMessage() {
        return unavailableMessage;
    }

    public void setUnavailableMessage(String unavailableMessage) {
        this.unavailableMessage = unavailableMessage;
    }
}
