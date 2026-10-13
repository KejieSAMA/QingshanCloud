package com.x.cloudnext.server.form.entity;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cloudnext_principal_binding", uniqueConstraints = {
        @UniqueConstraint(name = "uk_principal_form_subject", columnNames = {"auth_provider", "tenant_id", "form_employee_id"}),
        @UniqueConstraint(name = "uk_principal_canonical", columnNames = {"canonical_person_id"})
})
public class PrincipalBinding {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;
    @Column(name = "auth_provider", nullable = false, length = 32)
    private String authProvider = "FORM";
    @Column(name = "tenant_id", nullable = false, length = 128)
    private String tenantId;
    @Column(name = "form_employee_id", nullable = false, length = 128)
    private String formEmployeeId;
    @Column(name = "canonical_person_id", nullable = false, length = 128)
    private String canonicalPersonId;
    @Column(name = "legacy_oa_person_id", length = 128)
    private String legacyOaPersonId;
    @Column(name = "employee_no_snapshot", length = 128)
    private String employeeNoSnapshot;
    @Column(name = "display_name", length = 255)
    private String displayName;
    @Column(name = "display_path", length = 1024)
    private String displayPath;
    @Column(name = "account_enabled", nullable = false)
    private boolean accountEnabled;
    @Column(name = "source_account_enabled", nullable = false)
    private boolean sourceAccountEnabled;
    @Column(name = "auth_version", nullable = false)
    private long authVersion;
    @Column(name = "record_status", nullable = false, length = 32)
    private String status = "ACTIVE";
    @Column(name = "directory_status", nullable = false, length = 32)
    private String directoryStatus = "ACTIVE";
    @Column(name = "migration_decision", nullable = false, length = 32)
    private String migrationDecision = "PENDING";
    @Column(name = "decision_note", length = 2000) private String decisionNote;
    @Column(name = "decision_by", length = 128) private String decisionBy;
    @Column(name = "decided_at", length = 64) private String decidedAt;
    @Lob
    @Column(name = "role_list_json")
    private String roleListJson = "[]";
    @Column(name = "last_seen_etag", length = 128)
    private String lastSeenEtag;
    @Column(name = "created_at", nullable = false, length = 64)
    private String createdAt;
    @Column(name = "updated_at", nullable = false, length = 64)
    private String updatedAt;
    // Nullable wrapper lets Spring Data recognize assigned-id instances as new and call persist.
    @Version @Column(name="entity_version",nullable=false) private Long entityVersion;
    @Transient private long affectedReferenceCount;
    @Transient private List<String> suggestedLegacyOaIds=new ArrayList<>();

    @PrePersist public void prePersist() { String now = Instant.now().toString(); createdAt = now; updatedAt = now; }
    @PreUpdate public void preUpdate() { updatedAt = Instant.now().toString(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAuthProvider() { return authProvider; }
    public void setAuthProvider(String authProvider) { this.authProvider = authProvider; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getFormEmployeeId() { return formEmployeeId; }
    public void setFormEmployeeId(String formEmployeeId) { this.formEmployeeId = formEmployeeId; }
    public String getCanonicalPersonId() { return canonicalPersonId; }
    public void setCanonicalPersonId(String canonicalPersonId) { this.canonicalPersonId = canonicalPersonId; }
    public String getLegacyOaPersonId() { return legacyOaPersonId; }
    public void setLegacyOaPersonId(String legacyOaPersonId) { this.legacyOaPersonId = legacyOaPersonId; }
    public String getEmployeeNoSnapshot() { return employeeNoSnapshot; }
    public void setEmployeeNoSnapshot(String employeeNoSnapshot) { this.employeeNoSnapshot = employeeNoSnapshot; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDisplayPath() { return displayPath; }
    public void setDisplayPath(String displayPath) { this.displayPath = displayPath; }
    public boolean isAccountEnabled() { return accountEnabled; }
    public void setAccountEnabled(boolean accountEnabled) { this.accountEnabled = accountEnabled; }
    public boolean isSourceAccountEnabled() { return sourceAccountEnabled; }
    public void setSourceAccountEnabled(boolean sourceAccountEnabled) { this.sourceAccountEnabled = sourceAccountEnabled; }
    public long getAuthVersion() { return authVersion; }
    public void setAuthVersion(long authVersion) { this.authVersion = authVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDirectoryStatus() { return directoryStatus; }
    public void setDirectoryStatus(String directoryStatus) { this.directoryStatus = directoryStatus; }
    public String getMigrationDecision() { return migrationDecision; }
    public void setMigrationDecision(String migrationDecision) { this.migrationDecision = migrationDecision; }
    public String getDecisionNote(){return decisionNote;} public void setDecisionNote(String v){decisionNote=v;}
    public String getDecisionBy(){return decisionBy;} public void setDecisionBy(String v){decisionBy=v;}
    public String getDecidedAt(){return decidedAt;} public void setDecidedAt(String v){decidedAt=v;}
    public String getRoleListJson() { return roleListJson; }
    public void setRoleListJson(String roleListJson) { this.roleListJson = roleListJson; }
    public String getLastSeenEtag() { return lastSeenEtag; }
    public void setLastSeenEtag(String lastSeenEtag) { this.lastSeenEtag = lastSeenEtag; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public long getEntityVersion(){return entityVersion==null?0L:entityVersion;}
    public long getAffectedReferenceCount(){return affectedReferenceCount;} public void setAffectedReferenceCount(long v){affectedReferenceCount=v;}
    public List<String> getSuggestedLegacyOaIds(){return suggestedLegacyOaIds;} public void setSuggestedLegacyOaIds(List<String> v){suggestedLegacyOaIds=v;}
}
