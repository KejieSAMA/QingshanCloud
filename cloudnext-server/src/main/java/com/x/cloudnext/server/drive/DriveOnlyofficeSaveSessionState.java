package com.x.cloudnext.server.drive;

public class DriveOnlyofficeSaveSessionState {

    private final String editTicket;
    private volatile String state;
    private volatile String message;
    private volatile Long updatedAt;
    private volatile Long sizeBytes;

    public DriveOnlyofficeSaveSessionState(String editTicket) {
        this.editTicket = editTicket;
        this.state = "idle";
    }

    public String getEditTicket() {
        return editTicket;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public boolean isPending() {
        return "pending".equals(state);
    }

    public boolean isSaved() {
        return "saved".equals(state);
    }

    public boolean isFailed() {
        return "failed".equals(state);
    }
}
