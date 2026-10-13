package com.x.cloudnext.server.workspace;

import javax.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/workspaces/{id}}.
 */
public class WorkspaceUpdateRequest {

    @Size(max = 100)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
