package com.x.cloudnext.server.workspace;

import javax.validation.constraints.Size;

/**
 * Request body for {@code POST /api/workspaces/{id}/members}.
 *
 * <p>{@code role} is accepted as a free-form string and parsed inside
 * {@link WorkspaceService} into {@link com.x.cloudnext.server.drive.WorkspaceRole}
 * so that an unknown value produces a 400 with a stable error message
 * ({@code workspace role is invalid}) instead of a Jackson deserialization
 * failure.
 */
public class WorkspaceMemberAddRequest {

    @Size(max = 64)
    private String personOaId;

    @Size(max = 64)
    private String personKey;

    @Size(max = 255)
    private String personName = "";

    @Size(max = 16)
    private String role;

    public String getPersonOaId() {
        return personOaId;
    }

    public void setPersonOaId(String personOaId) {
        this.personOaId = personOaId;
    }

    public String getPersonKey() { return personKey; }
    public void setPersonKey(String personKey) { this.personKey = personKey; }

    public String getPersonName() {
        return personName;
    }

    public void setPersonName(String personName) {
        this.personName = personName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
