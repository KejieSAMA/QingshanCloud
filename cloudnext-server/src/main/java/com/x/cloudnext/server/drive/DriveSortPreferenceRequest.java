package com.x.cloudnext.server.drive;

public class DriveSortPreferenceRequest {
    private String context;
    private String sortBy;
    private String sortOrder;

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public String getSortOrder() { return sortOrder; }
    public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }
}
