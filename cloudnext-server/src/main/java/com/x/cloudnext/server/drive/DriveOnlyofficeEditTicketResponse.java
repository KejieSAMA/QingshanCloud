package com.x.cloudnext.server.drive;

public class DriveOnlyofficeEditTicketResponse {

    private String itemId;
    private String kind;
    private Boolean supported;
    private String mode;
    private String fileName;
    private String editTicket;
    private String editPageUrl;
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

    public String getEditTicket() {
        return editTicket;
    }

    public void setEditTicket(String editTicket) {
        this.editTicket = editTicket;
    }

    public String getEditPageUrl() {
        return editPageUrl;
    }

    public void setEditPageUrl(String editPageUrl) {
        this.editPageUrl = editPageUrl;
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
