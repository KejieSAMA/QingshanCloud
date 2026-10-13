package com.x.cloudnext.server.drive;

public class UnifiedSearchRequest extends DriveSearchRequest {
    private String sortBy = "relevance";
    private String sortOrder = "desc";
    public UnifiedSearchRequest() { setScope("all"); setSize(30); }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String value) { sortBy = value; }
    public String getSortOrder() { return sortOrder; }
    public void setSortOrder(String value) { sortOrder = value; }
}
