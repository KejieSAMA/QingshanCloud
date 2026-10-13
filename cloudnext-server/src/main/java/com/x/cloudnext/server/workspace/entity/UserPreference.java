package com.x.cloudnext.server.workspace.entity;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity
@Table(
        name = "cloudnext_user_preference",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cloudnext_user_preference",
                columnNames = { "person_oa_id", "preference_key" }),
        indexes = {
                @Index(name = "idx_cloudnext_user_preference_person", columnList = "person_oa_id")
        })
public class UserPreference {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "person_oa_id", nullable = false, length = 64)
    private String personOaId;

    @Column(name = "preference_key", nullable = false, length = 64)
    private String preferenceKey;

    @Column(name = "preference_value", nullable = false, length = 255)
    private String preferenceValue = "";

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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPersonOaId() {
        return personOaId;
    }

    public void setPersonOaId(String personOaId) {
        this.personOaId = personOaId;
    }

    public String getPreferenceKey() {
        return preferenceKey;
    }

    public void setPreferenceKey(String preferenceKey) {
        this.preferenceKey = preferenceKey;
    }

    public String getPreferenceValue() {
        return preferenceValue;
    }

    public void setPreferenceValue(String preferenceValue) {
        this.preferenceValue = preferenceValue;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
