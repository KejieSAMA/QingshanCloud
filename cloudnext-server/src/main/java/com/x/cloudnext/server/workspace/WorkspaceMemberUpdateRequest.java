package com.x.cloudnext.server.workspace;

import javax.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/workspaces/{id}/members/{memberId}}.
 *
 * <p>Only the {@code role} field is mutable in iteration one. The string is
 * parsed into {@link com.x.cloudnext.server.drive.WorkspaceRole} inside
 * {@link WorkspaceService}; an unknown value produces a 400 instead of a
 * Jackson failure.
 */
public class WorkspaceMemberUpdateRequest {

    @Size(max = 16)
    private String role;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
