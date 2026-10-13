package com.x.cloudnext.server.drive;

import java.util.ArrayList;
import java.util.List;

public class DriveBatchRenameRequest {

    private List<String> itemIds = new ArrayList<>();
    private String baseName = "";
    private Integer startNumber = 1;
    private Integer numberWidth = 2;

    public List<String> getItemIds() {
        return itemIds;
    }

    public void setItemIds(List<String> itemIds) {
        this.itemIds = itemIds;
    }

    public String getBaseName() {
        return baseName;
    }

    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }

    public Integer getStartNumber() {
        return startNumber;
    }

    public void setStartNumber(Integer startNumber) {
        this.startNumber = startNumber;
    }

    public Integer getNumberWidth() {
        return numberWidth;
    }

    public void setNumberWidth(Integer numberWidth) {
        this.numberWidth = numberWidth;
    }
}
