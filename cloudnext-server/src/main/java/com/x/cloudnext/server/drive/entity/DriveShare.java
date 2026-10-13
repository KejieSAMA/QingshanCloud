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

import com.x.cloudnext.server.drive.DriveSharePermission;
import com.x.cloudnext.server.drive.DriveShareTargetType;

@Entity
@Table(
        name = "cloudnext_drive_share",
        indexes = {
                @Index(name = "idx_drive_share_owner_item_target", columnList = "owner_person_oa_id, item_id, target_type, target_id"),
                @Index(name = "idx_drive_share_owner_revoked_updated", columnList = "owner_person_oa_id, revoked, updated_at"),
                @Index(name = "idx_drive_share_target_revoked_updated", columnList = "target_type, target_id, revoked, updated_at")
        })
public class DriveShare {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "item_id", nullable = false, length = 64)
    private String itemId;

    @Column(name = "owner_person_oa_id", nullable = false, length = 64)
    private String ownerPersonOaId;

    @Column(name = "owner_name", nullable = false, length = 255)
    private String ownerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private DriveShareTargetType targetType = DriveShareTargetType.PERSON;

    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    @Column(name = "target_name", nullable = false, length = 255)
    private String targetName;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 16)
    private DriveSharePermission permission = DriveSharePermission.READ;

    @Column(name = "expires_at", nullable = false, length = 64)
    private String expiresAt = "";

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    /**
     * N+2.4：是否允许被共享者把此项继续共享给他人。
     * 默认 false，必须由 owner / 管理员在共享 dialog 显式开启。
     * 当前不在 service 层强制 caller 自己拥有 canReshare 权限，
     * 仅作为前端 toggle + 后端透传字段；下一阶段引入"再共享传播限制"时再校验。
     */
    @org.hibernate.annotations.ColumnDefault("b'0'")
    @Column(name = "can_reshare", nullable = false)
    private boolean canReshare = false;

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

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
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

    public DriveShareTargetType getTargetType() {
        return targetType;
    }

    public void setTargetType(DriveShareTargetType targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public DriveSharePermission getPermission() {
        return permission;
    }

    public void setPermission(DriveSharePermission permission) {
        this.permission = permission;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public boolean isCanReshare() {
        return canReshare;
    }

    public void setCanReshare(boolean canReshare) {
        this.canReshare = canReshare;
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
