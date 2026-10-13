package com.x.cloudnext.server.form.entity;

import java.time.Instant;
import javax.persistence.*;

@Entity
@Table(name = "cloudnext_identity_binding", uniqueConstraints = {
        @UniqueConstraint(name = "uk_identity_form_subject", columnNames = {"tenant_id", "form_assignment_id"})
})
public class IdentityBinding {
    @Id @Column(name="id",nullable=false,length=64) private String id;
    @Column(name="tenant_id",nullable=false,length=128) private String tenantId;
    @Column(name="form_assignment_id",nullable=false,length=128) private String formAssignmentId;
    @Column(name="canonical_identity_id",nullable=false,length=128) private String canonicalIdentityId;
    @Column(name="canonical_person_id",nullable=false,length=128) private String canonicalPersonId;
    @Column(name="canonical_unit_id",nullable=false,length=128) private String canonicalUnitId;
    @Column(name="legacy_oa_identity_id",length=128) private String legacyOaIdentityId;
    @Column(name="position_id",length=128) private String positionId;
    @Column(name="identity_name",length=255) private String identityName;
    @Column(name="display_path",length=1024) private String displayPath;
    @Column(name="primary_assignment",nullable=false) private boolean primaryAssignment;
    @Column(name="record_status",nullable=false,length=32) private String status="ACTIVE";
    @Column(name="directory_status",nullable=false,length=32) private String directoryStatus="ACTIVE";
    @Column(name="migration_decision",nullable=false,length=32) private String migrationDecision="PENDING";
    @Column(name="decision_note",length=2000) private String decisionNote; @Column(name="decision_by",length=128) private String decisionBy; @Column(name="decided_at",length=64) private String decidedAt;
    @Column(name="last_seen_etag",length=128) private String lastSeenEtag;
    @Column(name="created_at",nullable=false,length=64) private String createdAt;
    @Column(name="updated_at",nullable=false,length=64) private String updatedAt;
    @Version @Column(name="entity_version",nullable=false) private Long entityVersion;
    @Transient private long affectedReferenceCount;
    @PrePersist public void prePersist(){String now=Instant.now().toString();createdAt=now;updatedAt=now;}
    @PreUpdate public void preUpdate(){updatedAt=Instant.now().toString();}
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;}
    public String getFormAssignmentId(){return formAssignmentId;} public void setFormAssignmentId(String v){formAssignmentId=v;}
    public String getCanonicalIdentityId(){return canonicalIdentityId;} public void setCanonicalIdentityId(String v){canonicalIdentityId=v;}
    public String getCanonicalPersonId(){return canonicalPersonId;} public void setCanonicalPersonId(String v){canonicalPersonId=v;}
    public String getCanonicalUnitId(){return canonicalUnitId;} public void setCanonicalUnitId(String v){canonicalUnitId=v;}
    public String getLegacyOaIdentityId(){return legacyOaIdentityId;} public void setLegacyOaIdentityId(String v){legacyOaIdentityId=v;}
    public String getPositionId(){return positionId;} public void setPositionId(String v){positionId=v;}
    public String getIdentityName(){return identityName;} public void setIdentityName(String v){identityName=v;}
    public String getDisplayPath(){return displayPath;} public void setDisplayPath(String v){displayPath=v;}
    public boolean isPrimaryAssignment(){return primaryAssignment;} public void setPrimaryAssignment(boolean v){primaryAssignment=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getDirectoryStatus(){return directoryStatus;} public void setDirectoryStatus(String v){directoryStatus=v;}
    public String getMigrationDecision(){return migrationDecision;} public void setMigrationDecision(String v){migrationDecision=v;}
    public String getDecisionNote(){return decisionNote;} public void setDecisionNote(String v){decisionNote=v;} public String getDecisionBy(){return decisionBy;} public void setDecisionBy(String v){decisionBy=v;} public String getDecidedAt(){return decidedAt;} public void setDecidedAt(String v){decidedAt=v;}
    public String getLastSeenEtag(){return lastSeenEtag;} public void setLastSeenEtag(String v){lastSeenEtag=v;}
    public String getCreatedAt(){return createdAt;} public String getUpdatedAt(){return updatedAt;}
    public long getEntityVersion(){return entityVersion==null?0L:entityVersion;}
    public long getAffectedReferenceCount(){return affectedReferenceCount;} public void setAffectedReferenceCount(long v){affectedReferenceCount=v;}
}
