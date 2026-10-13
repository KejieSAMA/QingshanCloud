package com.x.cloudnext.server.workspace;

import javax.validation.constraints.Size;

/**
 * Request body for {@code POST /api/workspaces}.
 *
 * <p>Validation is intentionally minimal at the DTO layer; the authoritative
 * checks (non-blank, length 1..100) live in {@link WorkspaceService} so that
 * the same rules apply when the controller is bypassed (e.g. integration
 * tests calling the service directly).
 */
public class WorkspaceCreateRequest {

    @Size(max = 100)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
