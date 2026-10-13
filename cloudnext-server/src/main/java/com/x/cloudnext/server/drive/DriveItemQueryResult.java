package com.x.cloudnext.server.drive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.x.cloudnext.server.drive.entity.DriveItem;

public class DriveItemQueryResult {

    private final List<DriveItem> items;
    private final long total;
    private final int page;
    private final int size;

    public DriveItemQueryResult(List<DriveItem> items, long total, int page, int size) {
        this.items = items == null ? Collections.emptyList() : new ArrayList<>(items);
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public List<DriveItem> getItems() {
        return items;
    }

    public long getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public int getTotalPages() {
        return total == 0 ? 0 : (int) Math.ceil(total / (double) size);
    }

    public boolean isHasNext() {
        return getTotalPages() > 0 && page < getTotalPages();
    }
}
