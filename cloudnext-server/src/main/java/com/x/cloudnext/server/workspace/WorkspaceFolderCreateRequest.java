package com.x.cloudnext.server.workspace;

import javax.validation.constraints.Size;

/**
 * Request body for {@code POST /api/workspaces/{id}/folders}. Mirrors the
 * shape of {@code DriveCreateFolderRequest} so the workspace surface keeps
 * the same conflict / naming semantics as the personal drive.
 *
 * <p>An empty {@code parentId} means "create directly under the workspace
 * root", which is normalized inside {@link WorkspaceService} the same way
 * {@link com.x.cloudnext.server.drive.DriveAccessService#normalizeParentId(String)}
 * normalizes personal-scope paths.
 */
public class WorkspaceFolderCreateRequest {

    @Size(max = 64)
    private String parentId = "";

    @Size(max = 255)
    private String name;

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
