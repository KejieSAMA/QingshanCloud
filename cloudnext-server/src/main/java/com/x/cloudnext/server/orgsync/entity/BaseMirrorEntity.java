package com.x.cloudnext.server.orgsync.entity;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;

@MappedSuperclass
public abstract class BaseMirrorEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "status", length = 64)
    private String status;

    @Column(name = "updated_at", nullable = false, length = 64)
    private String updatedAt = "";

    @Column(name = "sync_version", nullable = false, length = 64)
    private String syncVersion = "";

    @Column(name = "synced_at", nullable = false, length = 64)
    private String syncedAt = "";

    @PrePersist
    @PreUpdate
    public void touchSyncedAt() {
        this.syncedAt = Instant.now().toString();
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getSyncVersion() {
        return syncVersion;
    }

    public void setSyncVersion(String syncVersion) {
        this.syncVersion = syncVersion;
    }

    public String getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(String syncedAt) {
        this.syncedAt = syncedAt;
    }
}
