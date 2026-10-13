package com.x.cloudnext.server.migration.entity;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;

@Entity
@Table(name = "cloudnext_meta")
public class CloudNextMeta {

    @Id
    @Column(name = "meta_key", nullable = false, length = 64)
    private String metaKey;

    @Column(name = "meta_value", nullable = false, length = 255)
    private String metaValue = "";

    @Column(name = "updated_at", nullable = false, length = 64)
    private String updatedAt = "";

    @PrePersist
    public void prePersist() {
        this.updatedAt = Instant.now().toString();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now().toString();
    }

    public String getMetaKey() {
        return metaKey;
    }

    public void setMetaKey(String metaKey) {
        this.metaKey = metaKey;
    }

    public String getMetaValue() {
        return metaValue;
    }

    public void setMetaValue(String metaValue) {
        this.metaValue = metaValue;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
