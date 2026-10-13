package com.x.cloudnext.server.drive.entity;

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

import com.x.cloudnext.server.drive.DriveItemType;
import com.x.cloudnext.server.drive.DriveScopeType;

@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(
        name = "cloudnext_drive_item",
        indexes = {
                @Index(
                        name = "idx_drive_item_owner_deleted_parent_type_updated",
                        columnList = "owner_person_oa_id, deleted, parent_id, item_type, updated_at"),
                @Index(
                        name = "idx_drive_item_owner_deleted_parent_name",
                        columnList = "owner_person_oa_id, deleted, parent_id, name"),
                @Index(
                        name = "idx_drive_item_owner_deleted_deleted_at",
                        columnList = "owner_person_oa_id, deleted, deleted_at"),
                @Index(
                        name = "idx_drive_item_owner_deleted_last_accessed",
                        columnList = "owner_person_oa_id, deleted, last_accessed_at"),
                @Index(
                        name = "idx_drive_item_scope_deleted_parent_updated",
                        columnList = "scope_type, scope_id, deleted, parent_id, updated_at")
        })
public class DriveItem {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "parent_id", nullable = false, length = 64)
    private String parentId = "";

    @Column(name = "owner_person_oa_id", nullable = false, length = 64)
    private String ownerPersonOaId;

    @Column(name = "owner_name", nullable = false, length = 255)
    private String ownerName;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 16)
    private DriveItemType itemType = DriveItemType.FILE;

    @Column(name = "file_type", nullable = false, length = 32)
    private String fileType = "file";

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType = "";

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes = 0L;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 16, columnDefinition = "VARCHAR(16) DEFAULT 'PERSONAL'")
    private DriveScopeType scopeType = DriveScopeType.PERSONAL;

    @Column(name = "scope_id", nullable = false, length = 64, columnDefinition = "VARCHAR(64) DEFAULT ''")
    private String scopeId = "";

    @Column(name = "storage_backend", nullable = false, length = 16, columnDefinition = "VARCHAR(16) DEFAULT 'local'")
    private String storageBackend = "local";

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @Column(name = "deleted_at", nullable = false, length = 64)
    private String deletedAt = "";

    @Column(name = "created_at", nullable = false, length = 64)
    private String createdAt = "";

    @Column(name = "updated_at", nullable = false, length = 64)
    private String updatedAt = "";

    @Column(name = "last_accessed_at", nullable = false, length = 64, columnDefinition = "VARCHAR(64) DEFAULT ''")
    private String lastAccessedAt = "";

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

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getOwnerPersonOaId() {
        return ownerPersonOaId;
    }

    public void setOwnerPersonOaId(String ownerPersonOaId) {
        this.ownerPersonOaId = ownerPersonOaId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DriveItemType getItemType() {
        return itemType;
    }

    public void setItemType(DriveItemType itemType) {
        this.itemType = itemType;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public DriveScopeType getScopeType() {
        return scopeType;
    }

    public void setScopeType(DriveScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public String getStorageBackend() {
        return storageBackend;
    }

    public void setStorageBackend(String storageBackend) {
        this.storageBackend = storageBackend;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public String getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(String deletedAt) {
        this.deletedAt = deletedAt;
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

    public String getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(String lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }
}
