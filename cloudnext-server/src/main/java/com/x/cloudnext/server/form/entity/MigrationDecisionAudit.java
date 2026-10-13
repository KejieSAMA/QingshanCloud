package com.x.cloudnext.server.form.entity;

import java.time.Instant;import javax.persistence.*;
@Entity @Table(name="cloudnext_migration_decision_audit")
public class MigrationDecisionAudit {
 @Id @Column(name="id",length=64) private String id;@Column(name="subject_type",length=32,nullable=false)private String subjectType;@Column(name="subject_id",length=128,nullable=false)private String subjectId;@Column(name="actor_person_key",length=128,nullable=false)private String actorPersonKey;@Column(name="before_value",length=2000)private String beforeValue;@Column(name="after_value",length=2000)private String afterValue;@Column(name="note",length=2000)private String note;@Column(name="created_at",length=64,nullable=false)private String createdAt;
 @PrePersist public void create(){createdAt=Instant.now().toString();}public String getId(){return id;}public void setId(String v){id=v;}public String getSubjectType(){return subjectType;}public void setSubjectType(String v){subjectType=v;}public String getSubjectId(){return subjectId;}public void setSubjectId(String v){subjectId=v;}public String getActorPersonKey(){return actorPersonKey;}public void setActorPersonKey(String v){actorPersonKey=v;}public String getBeforeValue(){return beforeValue;}public void setBeforeValue(String v){beforeValue=v;}public String getAfterValue(){return afterValue;}public void setAfterValue(String v){afterValue=v;}public String getNote(){return note;}public void setNote(String v){note=v;}public String getCreatedAt(){return createdAt;}
}
