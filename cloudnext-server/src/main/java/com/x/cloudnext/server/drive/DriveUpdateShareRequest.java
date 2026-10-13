package com.x.cloudnext.server.drive;

public class DriveUpdateShareRequest {

    private String permission = "";
    private String expiresAt = "";
    /** null = 不变；true/false 显式覆盖 share.canReshare */
    private Boolean canReshare;

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

    public Boolean getCanReshare() {
        return canReshare;
    }

    public void setCanReshare(Boolean canReshare) {
        this.canReshare = canReshare;
    }
}
