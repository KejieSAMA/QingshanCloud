package com.x.cloudnext.server.drive;

import java.util.ArrayList;
import java.util.List;

public class DriveBatchIdsRequest {

    private List<String> itemIds = new ArrayList<>();

    public List<String> getItemIds() {
        return itemIds;
    }

    public void setItemIds(List<String> itemIds) {
        this.itemIds = itemIds;
    }
}
