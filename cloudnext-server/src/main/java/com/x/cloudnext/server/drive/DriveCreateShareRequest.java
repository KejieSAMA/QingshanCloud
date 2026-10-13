package com.x.cloudnext.server.drive;

import java.util.ArrayList;
import java.util.List;

public class DriveCreateShareRequest {

    private String itemId = "";
    private List<String> itemIds = new ArrayList<>();
    private List<DriveShareTargetRequest> targets = new ArrayList<>();
    private String permission = "read";
    private String expiresAt = "";
    private boolean canReshare = false;

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public List<String> getItemIds() {
        return itemIds;
    }

    public void setItemIds(List<String> itemIds) {
        this.itemIds = itemIds;
    }

    public List<DriveShareTargetRequest> getTargets() {
        return targets;
    }

    public void setTargets(List<DriveShareTargetRequest> targets) {
        this.targets = targets;
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

    public boolean isCanReshare() {
        return canReshare;
    }

    public void setCanReshare(boolean canReshare) {
        this.canReshare = canReshare;
    }
}
