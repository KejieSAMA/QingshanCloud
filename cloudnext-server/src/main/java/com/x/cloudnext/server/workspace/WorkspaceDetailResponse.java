package com.x.cloudnext.server.workspace;

/**
 * Full projection returned by {@code GET /api/workspaces/{id}} and the
 * {@code POST /api/workspaces} create path. Carries {@code rootItemId} so the
 * Vue detail page can immediately fetch the directory listing without a
 * second roundtrip, and {@code myRole} so the page can hide write controls
 * for VIEWER members up front.
 */
public class WorkspaceDetailResponse {

    private String id;
    private String name;
    private String status;
    private String myRole;
    private String rootItemId;
    private String creatorPersonOaId;
    private String creatorName;
    private long memberCount;
    private String createdAt;
    private String updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMyRole() {
        return myRole;
    }

    public void setMyRole(String myRole) {
        this.myRole = myRole;
    }

    public String getRootItemId() {
        return rootItemId;
    }

    public void setRootItemId(String rootItemId) {
        this.rootItemId = rootItemId;
    }

    public String getCreatorPersonOaId() {
        return creatorPersonOaId;
    }

    public void setCreatorPersonOaId(String creatorPersonOaId) {
        this.creatorPersonOaId = creatorPersonOaId;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public long getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(long memberCount) {
        this.memberCount = memberCount;
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
