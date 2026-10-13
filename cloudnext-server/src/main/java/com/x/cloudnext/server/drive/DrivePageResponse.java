package com.x.cloudnext.server.drive;

import java.util.ArrayList;
import java.util.List;

public class DrivePageResponse {

    private List<DriveItemResponse> items = new ArrayList<>();
    private Integer page = 1;
    private Integer size = 50;
    private Long total = 0L;
    private Integer totalPages = 0;
    private Boolean hasNext = false;

    public List<DriveItemResponse> getItems() {
        return items;
    }

    public void setItems(List<DriveItemResponse> items) {
        this.items = items;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Integer getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }

    public Boolean getHasNext() {
        return hasNext;
    }

    public void setHasNext(Boolean hasNext) {
        this.hasNext = hasNext;
    }
}
