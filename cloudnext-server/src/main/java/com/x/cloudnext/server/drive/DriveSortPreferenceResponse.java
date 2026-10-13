package com.x.cloudnext.server.drive;

public class DriveSortPreferenceResponse {
    private String context;
    private String sortBy;
    private String sortOrder;

    public DriveSortPreferenceResponse() { }
    public DriveSortPreferenceResponse(String context, String sortBy, String sortOrder) {
        this.context = context;
        this.sortBy = sortBy;
        this.sortOrder = sortOrder;
    }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public String getSortOrder() { return sortOrder; }
    public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }
}
