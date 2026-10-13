package com.x.cloudnext.server.drive;

import java.util.ArrayList;
import java.util.List;

public class DriveShareSummaryResponse {

    private String itemId;
    private String itemName;
    private String itemType;
    private String ownerName;
    private Boolean shared;
    private Integer targetCount = 0;
    private Integer activeTargetCount = 0;
    private List<DriveShareCollaboratorSummaryResponse> collaborators = new ArrayList<>();

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public Boolean getShared() {
        return shared;
    }

    public void setShared(Boolean shared) {
        this.shared = shared;
    }

    public Integer getTargetCount() {
        return targetCount;
    }

    public void setTargetCount(Integer targetCount) {
        this.targetCount = targetCount;
    }

    public Integer getActiveTargetCount() {
        return activeTargetCount;
    }

    public void setActiveTargetCount(Integer activeTargetCount) {
        this.activeTargetCount = activeTargetCount;
    }

    public List<DriveShareCollaboratorSummaryResponse> getCollaborators() {
        return collaborators;
    }

    public void setCollaborators(List<DriveShareCollaboratorSummaryResponse> collaborators) {
        this.collaborators = collaborators;
    }
}
