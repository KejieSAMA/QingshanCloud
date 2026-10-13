package com.x.cloudnext.server.workspace;

/**
 * Lightweight projection used by {@code GET /api/workspaces}; full detail
 * comes from {@link WorkspaceDetailResponse} via {@code GET /api/workspaces/{id}}.
 *
 * <p>Carries {@code myRole} and {@code memberCount} so the listing page can
 * render the role badge and member counter without issuing N additional
 * detail/member calls.
 */
public class WorkspaceSummaryResponse {

    private String id;
    private String name;
    private String status;
    private String myRole;
    private String creatorName;
    private String createdAt;
    private String updatedAt;
    private long memberCount;

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

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(long memberCount) {
        this.memberCount = memberCount;
    }
}
