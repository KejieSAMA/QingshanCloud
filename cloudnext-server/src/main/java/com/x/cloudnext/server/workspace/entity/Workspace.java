package com.x.cloudnext.server.workspace.entity;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;

/**
 * A collaborative Workspace.
 *
 * <p>The Workspace owns a single {@code rootItemId} pointing at a
 * {@code DriveItem} whose {@code scopeType=WORKSPACE}, {@code parentId=""},
 * {@code itemType=FOLDER}, {@code name=""} and {@code storageKey=""}. All
 * files / folders inside the Workspace hang off that tree, so previews,
 * downloads, trash and audit reuse the existing DriveItem pipeline.
 *
 * <p>Status currently has only one operational value: {@code ACTIVE}.
 * {@code ARCHIVED} is reserved for a later iteration and is not exposed
 * through the API in iteration one (per design §"State Machine").
 */
@Entity
@Table(
        name = "cloudnext_workspace",
        indexes = @Index(name = "idx_workspace_status_updated", columnList = "status, updated_at"))
public class Workspace {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "creator_person_oa_id", nullable = false, length = 64)
    private String creatorPersonOaId;

    @Column(name = "creator_name", nullable = false, length = 255)
    private String creatorName = "";

    @Column(name = "status", nullable = false, length = 16)
    private String status = STATUS_ACTIVE; // ACTIVE | ARCHIVED

    @Column(name = "root_item_id", nullable = false, length = 64)
    private String rootItemId = "";

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRootItemId() {
        return rootItemId;
    }

    public void setRootItemId(String rootItemId) {
        this.rootItemId = rootItemId;
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
