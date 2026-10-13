package com.x.cloudnext.server.workspace.entity;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import com.x.cloudnext.server.drive.WorkspaceRole;

/**
 * A person's membership in a {@link Workspace}.
 *
 * <p>The composite {@code (workspace_id, person_oa_id)} is unique so a person
 * cannot hold two roles in the same Workspace. The two secondary indexes
 * support the access-gateway lookups:
 * <ul>
 *   <li>{@code idx_ws_member_person} — "what workspaces is this person in?"</li>
 *   <li>{@code idx_ws_member_workspace_role} — "how many owners does this
 *       workspace still have?" (owner-conservation guard, task 1.6).</li>
 * </ul>
 */
@Entity
@Table(
        name = "cloudnext_workspace_member",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ws_member",
                columnNames = { "workspace_id", "person_oa_id" }),
        indexes = {
                @Index(name = "idx_ws_member_person", columnList = "person_oa_id, workspace_id"),
                @Index(name = "idx_ws_member_workspace_role", columnList = "workspace_id, role")
        })
public class WorkspaceMember {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "person_oa_id", nullable = false, length = 64)
    private String personOaId;

    @Column(name = "person_name", nullable = false, length = 255)
    private String personName = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private WorkspaceRole role = WorkspaceRole.MEMBER;

    @Column(name = "created_at", nullable = false, length = 64)
    private String createdAt = "";

    @Column(name = "updated_at", nullable = false, length = 64)
    private String updatedAt = "";

    @PrePersist
    public void prePersist() {
        String now = Instant.now().toString();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now().toString();
    }

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

    public String getPersonName() {
        return personName;
    }

    public void setPersonName(String personName) {
        this.personName = personName;
    }

    public WorkspaceRole getRole() {
        return role;
    }

    public void setRole(WorkspaceRole role) {
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
