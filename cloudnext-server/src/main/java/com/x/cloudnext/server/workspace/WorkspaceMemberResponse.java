package com.x.cloudnext.server.workspace;

/**
 * DTO returned by {@code GET /api/workspaces/{id}/members}, member create,
 * and member role update endpoints.
 */
public class WorkspaceMemberResponse {

    private String id;
    private String workspaceId;
    private String personOaId;
    private String personKey;
    private String personName;
    private String employeeNo;
    private String role;
    private String createdAt;
    private String updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getPersonOaId() {
        return personOaId;
    }

    public void setPersonOaId(String personOaId) {
        this.personOaId = personOaId;
    }
    public String getPersonKey(){return personKey;} public void setPersonKey(String v){personKey=v;}

    public String getPersonName() {
        return personName;
    }
    public String getEmployeeNo(){return employeeNo;} public void setEmployeeNo(String v){employeeNo=v;}

    public void setPersonName(String personName) {
        this.personName = personName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
