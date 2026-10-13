package com.x.cloudnext.server.drive;

import java.util.ArrayList;
import java.util.List;

public class DriveMoveItemsRequest {

    private List<String> itemIds = new ArrayList<>();
    private String targetParentId = "";
    private DriveConflictStrategy conflictStrategy = DriveConflictStrategy.ABORT;

    public List<String> getItemIds() {
        return itemIds;
    }

    public void setItemIds(List<String> itemIds) {
        this.itemIds = itemIds;
    }

    public String getTargetParentId() {
        return targetParentId;
    }

    public void setTargetParentId(String targetParentId) {
        this.targetParentId = targetParentId;
    }

    public DriveConflictStrategy getConflictStrategy() {
        return conflictStrategy;
    }

    public void setConflictStrategy(DriveConflictStrategy conflictStrategy) {
        this.conflictStrategy = conflictStrategy;
    }
}
