package com.x.cloudnext.server.orgsync.entity;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;

@Entity
@Table(name = "cloudnext_org_membership")
public class OrgMembershipMirror {

    @Id
    @Column(name = "id", nullable = false, length = 128)
    private String id;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_oa_id", nullable = false, length = 64)
    private String sourceOaId;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_oa_id", nullable = false, length = 64)
    private String targetOaId;

    @Column(name = "relation_type", nullable = false, length = 32)
    private String relationType;

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

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceOaId() {
        return sourceOaId;
    }

    public void setSourceOaId(String sourceOaId) {
        this.sourceOaId = sourceOaId;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetOaId() {
        return targetOaId;
    }

    public void setTargetOaId(String targetOaId) {
        this.targetOaId = targetOaId;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
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
