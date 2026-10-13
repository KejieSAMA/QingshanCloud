package com.x.cloudnext.server.drive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DriveBatchOperationResponse {

    private String message;
    private Integer totalRequested = 0;
    private Integer totalSucceeded = 0;
    private Integer totalFailed = 0;
    private List<String> succeededIds = new ArrayList<>();
    private Map<String, String> failedItems = new LinkedHashMap<>();

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getTotalRequested() {
        return totalRequested;
    }

    public void setTotalRequested(Integer totalRequested) {
        this.totalRequested = totalRequested;
    }

    public Integer getTotalSucceeded() {
        return totalSucceeded;
    }

    public void setTotalSucceeded(Integer totalSucceeded) {
        this.totalSucceeded = totalSucceeded;
    }

    public Integer getTotalFailed() {
        return totalFailed;
    }

    public void setTotalFailed(Integer totalFailed) {
        this.totalFailed = totalFailed;
    }

    public List<String> getSucceededIds() {
        return succeededIds;
    }

    public void setSucceededIds(List<String> succeededIds) {
        this.succeededIds = succeededIds;
    }

    public Map<String, String> getFailedItems() {
        return failedItems;
    }

    public void setFailedItems(Map<String, String> failedItems) {
        this.failedItems = failedItems;
    }
}
