package com.x.cloudnext.server.form.entity;

import java.time.Instant;
import javax.persistence.*;

@Entity
@Table(name="cloudnext_canonical_alias", uniqueConstraints={
        @UniqueConstraint(name="uk_canonical_alias", columnNames={"subject_type","alias_key","canonical_key"})
})
public class CanonicalAlias {
    @Id @Column(name="id",nullable=false,length=64) private String id;
    @Column(name="subject_type",nullable=false,length=32) private String subjectType;
    @Column(name="alias_key",nullable=false,length=128) private String aliasKey;
    @Column(name="canonical_key",nullable=false,length=128) private String canonicalKey;
    @Column(name="relation_type",nullable=false,length=32) private String relationType;
    @Column(name="created_at",nullable=false,length=64) private String createdAt;
    @PrePersist public void prePersist(){createdAt=Instant.now().toString();}
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getSubjectType(){return subjectType;} public void setSubjectType(String v){subjectType=v;}
    public String getAliasKey(){return aliasKey;} public void setAliasKey(String v){aliasKey=v;}
    public String getCanonicalKey(){return canonicalKey;} public void setCanonicalKey(String v){canonicalKey=v;}
    public String getRelationType(){return relationType;} public void setRelationType(String v){relationType=v;}
    public String getCreatedAt(){return createdAt;}
}
