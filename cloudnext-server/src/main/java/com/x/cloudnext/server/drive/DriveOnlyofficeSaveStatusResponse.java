package com.x.cloudnext.server.drive;

public class DriveOnlyofficeSaveStatusResponse {

    private String editTicket;
    private String state;
    private boolean pending;
    private boolean saved;
    private boolean failed;
    private String message;
    private Long updatedAt;
    private Long sizeBytes;

    public String getEditTicket() {
        return editTicket;
    }

    public void setEditTicket(String editTicket) {
        this.editTicket = editTicket;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public boolean isPending() {
        return pending;
    }

    public void setPending(boolean pending) {
        this.pending = pending;
    }

    public boolean isSaved() {
        return saved;
    }

    public void setSaved(boolean saved) {
        this.saved = saved;
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
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
}
