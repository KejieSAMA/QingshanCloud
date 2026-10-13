package com.x.cloudnext.server.form.entity;

import java.time.Instant;
import javax.persistence.*;

@Entity
@Table(name="cloudnext_directory_sync_state")
public class DirectorySyncState {
    @Id @Column(name="source_key",nullable=false,length=64) private String sourceKey;
    @Column(name="etag",length=128) private String etag;
    @Column(name="last_outcome",nullable=false,length=32) private String lastOutcome="NOT_STARTED";
    @Column(name="last_started_at",length=64) private String lastStartedAt;
    @Column(name="last_success_at",length=64) private String lastSuccessAt;
    @Column(name="attempt_generation") private Long attemptGeneration;
    @Column(name="last_success_generation") private Long lastSuccessGeneration;
    @Column(name="last_success_cutover_version",length=128) private String lastSuccessCutoverVersion;
    @Column(name="last_failure_at",length=64) private String lastFailureAt;
    @Lob @Column(name="failure_message") private String failureMessage;
    @Column(name="snapshot_tenant_id",length=128) private String snapshotTenantId;
    @Column(name="foundation_sot",length=32) private String foundationSot;
    @Column(name="foundation_write_impl",length=32) private String foundationWriteImpl;
    @Column(name="person_count") private long personCount;
    @Column(name="unit_count") private long unitCount;
    @Column(name="identity_count") private long identityCount;
    @Column(name="updated_at",nullable=false,length=64) private String updatedAt;
    @Version @Column(name="entity_version",nullable=false) private long entityVersion;
    @PrePersist @PreUpdate public void touch(){updatedAt=Instant.now().toString();}
    public String getSourceKey(){return sourceKey;} public void setSourceKey(String v){sourceKey=v;}
    public String getEtag(){return etag;} public void setEtag(String v){etag=v;}
    public String getLastOutcome(){return lastOutcome;} public void setLastOutcome(String v){lastOutcome=v;}
    public String getLastStartedAt(){return lastStartedAt;} public void setLastStartedAt(String v){lastStartedAt=v;}
    public String getLastSuccessAt(){return lastSuccessAt;} public void setLastSuccessAt(String v){lastSuccessAt=v;}
    public long getAttemptGeneration(){return attemptGeneration==null?0L:attemptGeneration;} public void setAttemptGeneration(long v){attemptGeneration=v;}
    public long getLastSuccessGeneration(){return lastSuccessGeneration==null?0L:lastSuccessGeneration;} public void setLastSuccessGeneration(long v){lastSuccessGeneration=v;}
    public String getLastSuccessCutoverVersion(){return lastSuccessCutoverVersion;} public void setLastSuccessCutoverVersion(String v){lastSuccessCutoverVersion=v;}
    public String getLastFailureAt(){return lastFailureAt;} public void setLastFailureAt(String v){lastFailureAt=v;}
    public String getFailureMessage(){return failureMessage;} public void setFailureMessage(String v){failureMessage=v;}
    public String getSnapshotTenantId(){return snapshotTenantId;} public void setSnapshotTenantId(String v){snapshotTenantId=v;}
    public String getFoundationSot(){return foundationSot;} public void setFoundationSot(String v){foundationSot=v;}
    public String getFoundationWriteImpl(){return foundationWriteImpl;} public void setFoundationWriteImpl(String v){foundationWriteImpl=v;}
    public long getPersonCount(){return personCount;} public void setPersonCount(long v){personCount=v;}
    public long getUnitCount(){return unitCount;} public void setUnitCount(long v){unitCount=v;}
    public long getIdentityCount(){return identityCount;} public void setIdentityCount(long v){identityCount=v;}
    public String getUpdatedAt(){return updatedAt;}
}
