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
import javax.persistence.UniqueConstraint;

import com.x.cloudnext.server.drive.PublicLinkPermission;

/**
 * A public share link for a {@code DriveItem} (iteration two,
 * design §"迭代二：Public Link 闭环 / 实体").
 *
 * <p>Lifecycle: {@code OPEN → REVOKED (terminal)}. While {@code OPEN} the
 * link can additionally be {@code expired} or {@code exhausted}; both are
 * derived from {@code expiresAt} and {@code downloadLimit / downloadCount}
 * at access time and not persisted.
 *
 * <p>Sentinel values follow the design contract:
 * <ul>
 *   <li>{@code passwordHash == ""} → no password required.</li>
 *   <li>{@code expiresAt == ""} → never expires.</li>
 *   <li>{@code downloadLimit == -1L} → unlimited downloads.</li>
 * </ul>
 *
 * <p>Indexes mirror the access patterns of {@code DrivePublicLinkService}:
 * <ul>
 *   <li>{@code uk_drive_public_link_token} — global uniqueness for
 *       {@code findByToken(...)} on the public endpoint.</li>
 *   <li>{@code idx_dpl_creator_revoked_created} — "list my links" with
 *       active/revoked filtering, ordered by creation time.</li>
 *   <li>{@code idx_dpl_item} — locate links for a given {@code DriveItem}
 *       (e.g. cascade revoke when an item is permanently deleted).</li>
 * </ul>
 */
@Entity
@Table(
        name = "cloudnext_drive_public_link",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_drive_public_link_token",
                columnNames = "token"),
        indexes = {
                @Index(
                        name = "idx_dpl_creator_revoked_created",
                        columnList = "creator_person_oa_id, revoked, created_at"),
                @Index(
                        name = "idx_dpl_item",
                        columnList = "item_id")
        })
public class DrivePublicLink {
    @Column(name = "record_hidden", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean recordHidden;
    public boolean isRecordHidden() { return recordHidden; }
    public void setRecordHidden(boolean value) { recordHidden = value; }

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "item_id", nullable = false, length = 64)
    private String itemId;

    @Column(name = "creator_person_oa_id", nullable = false, length = 64)
    private String creatorPersonOaId;

    @Column(name = "creator_name", nullable = false, length = 255)
    private String creatorName = "";

    @Column(name = "token", nullable = false, length = 64)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 16)
    private PublicLinkPermission permission = PublicLinkPermission.PREVIEW;

    @Column(name = "password_hash", nullable = false, length = 128)
    private String passwordHash = "";

    @Column(name = "expires_at", nullable = false, length = 64)
    private String expiresAt = "";

    @Column(name = "download_limit", nullable = false)
    private Long downloadLimit = -1L;

    @Column(name = "download_count", nullable = false)
    private Long downloadCount = 0L;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public PublicLinkPermission getPermission() {
        return permission;
    }

    public void setPermission(PublicLinkPermission permission) {
        this.permission = permission;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getDownloadLimit() {
        return downloadLimit;
    }

    public void setDownloadLimit(Long downloadLimit) {
        this.downloadLimit = downloadLimit;
    }

    public Long getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(Long downloadCount) {
        this.downloadCount = downloadCount;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
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
