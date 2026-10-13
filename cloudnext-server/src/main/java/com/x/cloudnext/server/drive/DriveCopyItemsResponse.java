package com.x.cloudnext.server.drive;

import java.util.LinkedHashMap;
import java.util.Map;

public class DriveCopyItemsResponse extends DriveBatchOperationResponse {
    private Map<String, String> createdIdsBySource = new LinkedHashMap<>();

    public Map<String, String> getCreatedIdsBySource() { return createdIdsBySource; }
    public void setCreatedIdsBySource(Map<String, String> createdIdsBySource) {
        this.createdIdsBySource = createdIdsBySource;
    }
}
