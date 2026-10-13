package com.x.cloudnext.server.form.entity;

import java.time.Instant;
import javax.persistence.*;

@Entity
@Table(name = "cloudnext_unit_binding", uniqueConstraints = {
        @UniqueConstraint(name = "uk_unit_form_subject", columnNames = {"tenant_id", "form_org_id"})
})
public class UnitBinding {
    @Id @Column(name = "id", nullable = false, length = 64) private String id;
    @Column(name = "tenant_id", nullable = false, length = 128) private String tenantId;
    @Column(name = "form_org_id", nullable = false, length = 128) private String formOrgId;
    @Column(name = "canonical_unit_id", nullable = false, length = 128) private String canonicalUnitId;
    @Column(name = "legacy_oa_unit_id", length = 128) private String legacyOaUnitId;
    @Column(name = "parent_canonical_unit_id", length = 128) private String parentCanonicalUnitId;
    @Column(name = "unit_name", length = 255) private String unitName;
    @Column(name = "display_path", length = 1024) private String displayPath;
    @Column(name = "record_status", nullable = false, length = 32) private String status = "ACTIVE";
    @Column(name = "directory_status", nullable = false, length = 32) private String directoryStatus = "ACTIVE";
    @Column(name = "migration_decision", nullable = false, length = 32) private String migrationDecision = "PENDING";
    @Column(name="decision_note",length=2000) private String decisionNote; @Column(name="decision_by",length=128) private String decisionBy; @Column(name="decided_at",length=64) private String decidedAt;
    @Lob @Column(name = "canonical_aliases_json") private String canonicalAliasesJson = "[]";
    @Column(name = "last_seen_etag", length = 128) private String lastSeenEtag;
    @Column(name = "created_at", nullable = false, length = 64) private String createdAt;
    @Column(name = "updated_at", nullable = false, length = 64) private String updatedAt;
    @Version @Column(name="entity_version",nullable=false) private Long entityVersion;
    @Transient private long affectedReferenceCount;
    @PrePersist public void prePersist() { String now=Instant.now().toString(); createdAt=now; updatedAt=now; }
    @PreUpdate public void preUpdate() { updatedAt=Instant.now().toString(); }
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;}
    public String getFormOrgId(){return formOrgId;} public void setFormOrgId(String v){formOrgId=v;}
    public String getCanonicalUnitId(){return canonicalUnitId;} public void setCanonicalUnitId(String v){canonicalUnitId=v;}
    public String getLegacyOaUnitId(){return legacyOaUnitId;} public void setLegacyOaUnitId(String v){legacyOaUnitId=v;}
    public String getParentCanonicalUnitId(){return parentCanonicalUnitId;} public void setParentCanonicalUnitId(String v){parentCanonicalUnitId=v;}
    public String getUnitName(){return unitName;} public void setUnitName(String v){unitName=v;}
    public String getDisplayPath(){return displayPath;} public void setDisplayPath(String v){displayPath=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getDirectoryStatus(){return directoryStatus;} public void setDirectoryStatus(String v){directoryStatus=v;}
    public String getMigrationDecision(){return migrationDecision;} public void setMigrationDecision(String v){migrationDecision=v;}
    public String getDecisionNote(){return decisionNote;} public void setDecisionNote(String v){decisionNote=v;} public String getDecisionBy(){return decisionBy;} public void setDecisionBy(String v){decisionBy=v;} public String getDecidedAt(){return decidedAt;} public void setDecidedAt(String v){decidedAt=v;}
    public String getCanonicalAliasesJson(){return canonicalAliasesJson;} public void setCanonicalAliasesJson(String v){canonicalAliasesJson=v;}
    public String getLastSeenEtag(){return lastSeenEtag;} public void setLastSeenEtag(String v){lastSeenEtag=v;}
    public String getCreatedAt(){return createdAt;} public String getUpdatedAt(){return updatedAt;}
    public long getEntityVersion(){return entityVersion==null?0L:entityVersion;}
    public long getAffectedReferenceCount(){return affectedReferenceCount;} public void setAffectedReferenceCount(long v){affectedReferenceCount=v;}
}
