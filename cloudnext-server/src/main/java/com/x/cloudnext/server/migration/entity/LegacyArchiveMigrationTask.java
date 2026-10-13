package com.x.cloudnext.server.migration.entity;

import java.time.Instant;
import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties({"snapshotJson"})
@Entity
@Table(name="cloudnext_legacy_archive_task")
public class LegacyArchiveMigrationTask {
    @Id @Column(name="version",length=128) private String version;
    @Column(name="status",length=32,nullable=false) private String status;
    @Column(name="checksum",length=64,nullable=false) private String checksum;
    @Lob @Column(name="snapshot_json",nullable=false) private String snapshotJson;
    @Lob @Column(name="inventory_json") private String inventoryJson;
    @Column(name="form_snapshot_etag",length=128) private String formSnapshotEtag;
    @Column(name="form_snapshot_generation") private Long formSnapshotGeneration;
    @Column(name="form_snapshot_success_at",length=64) private String formSnapshotSuccessAt;
    @Column(name="unit_count") private long unitCount;
    @Column(name="person_count") private long personCount;
    @Column(name="identity_count") private long identityCount;
    @Column(name="role_count") private long roleCount;
    @Column(name="group_count") private long groupCount;
    @Column(name="membership_count") private long membershipCount;
    @Column(name="created_by",length=128) private String createdBy;
    @Column(name="approved_by",length=128) private String approvedBy;
    @Column(name="applied_by",length=128) private String appliedBy;
    @Column(name="verified_by",length=128) private String verifiedBy;
    @Column(name="created_at",length=64) private String createdAt;
    @Column(name="approved_at",length=64) private String approvedAt;
    @Column(name="applied_at",length=64) private String appliedAt;
    @Column(name="verified_at",length=64) private String verifiedAt;
    @PrePersist public void create(){createdAt=Instant.now().toString();}
    public String getVersion(){return version;}public void setVersion(String v){version=v;}public String getStatus(){return status;}public void setStatus(String v){status=v;}public String getChecksum(){return checksum;}public void setChecksum(String v){checksum=v;}public String getSnapshotJson(){return snapshotJson;}public void setSnapshotJson(String v){snapshotJson=v;}public String getInventoryJson(){return inventoryJson;}public void setInventoryJson(String v){inventoryJson=v;}
    public String getFormSnapshotEtag(){return formSnapshotEtag;}public void setFormSnapshotEtag(String v){formSnapshotEtag=v;}
    public long getFormSnapshotGeneration(){return formSnapshotGeneration==null?0L:formSnapshotGeneration;}public void setFormSnapshotGeneration(long v){formSnapshotGeneration=v;}
    public String getFormSnapshotSuccessAt(){return formSnapshotSuccessAt;}public void setFormSnapshotSuccessAt(String v){formSnapshotSuccessAt=v;}
    public long getUnitCount(){return unitCount;}public void setUnitCount(long v){unitCount=v;}public long getPersonCount(){return personCount;}public void setPersonCount(long v){personCount=v;}public long getIdentityCount(){return identityCount;}public void setIdentityCount(long v){identityCount=v;}public long getRoleCount(){return roleCount;}public void setRoleCount(long v){roleCount=v;}public long getGroupCount(){return groupCount;}public void setGroupCount(long v){groupCount=v;}public long getMembershipCount(){return membershipCount;}public void setMembershipCount(long v){membershipCount=v;}
    public String getCreatedBy(){return createdBy;}public void setCreatedBy(String v){createdBy=v;}public String getApprovedBy(){return approvedBy;}public void setApprovedBy(String v){approvedBy=v;}public String getAppliedBy(){return appliedBy;}public void setAppliedBy(String v){appliedBy=v;}public String getVerifiedBy(){return verifiedBy;}public void setVerifiedBy(String v){verifiedBy=v;}public String getCreatedAt(){return createdAt;}public String getApprovedAt(){return approvedAt;}public void setApprovedAt(String v){approvedAt=v;}public String getAppliedAt(){return appliedAt;}public void setAppliedAt(String v){appliedAt=v;}public String getVerifiedAt(){return verifiedAt;}public void setVerifiedAt(String v){verifiedAt=v;}
}
