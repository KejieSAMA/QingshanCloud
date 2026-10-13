package com.x.cloudnext.server.drive;

import java.util.ArrayList;
import java.util.List;

public class DriveCopyItemsRequest {
    private List<String> itemIds = new ArrayList<>();
    private String targetScopeType = "PERSONAL";
    private String targetScopeId = "";
    private String targetParentId = "";
    private DriveConflictStrategy conflictStrategy = DriveConflictStrategy.KEEP_BOTH;

    public List<String> getItemIds() { return itemIds; }
    public void setItemIds(List<String> itemIds) { this.itemIds = itemIds; }
    public String getTargetScopeType() { return targetScopeType; }
    public void setTargetScopeType(String targetScopeType) { this.targetScopeType = targetScopeType; }
    public String getTargetScopeId() { return targetScopeId; }
    public void setTargetScopeId(String targetScopeId) { this.targetScopeId = targetScopeId; }
    public String getTargetParentId() { return targetParentId; }
    public void setTargetParentId(String targetParentId) { this.targetParentId = targetParentId; }
    public DriveConflictStrategy getConflictStrategy() { return conflictStrategy; }
    public void setConflictStrategy(DriveConflictStrategy conflictStrategy) { this.conflictStrategy = conflictStrategy; }
}
