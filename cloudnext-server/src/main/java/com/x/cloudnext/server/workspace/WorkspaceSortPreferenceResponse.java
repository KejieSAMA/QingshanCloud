package com.x.cloudnext.server.workspace;

public class WorkspaceSortPreferenceResponse {

    private String sortMode;

    public WorkspaceSortPreferenceResponse() {
    }

    public WorkspaceSortPreferenceResponse(String sortMode) {
        this.sortMode = sortMode;
    }

    public String getSortMode() {
        return sortMode;
    }

    public void setSortMode(String sortMode) {
        this.sortMode = sortMode;
    }
}
