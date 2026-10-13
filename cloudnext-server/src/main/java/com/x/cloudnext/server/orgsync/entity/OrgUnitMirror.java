package com.x.cloudnext.server.orgsync.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

@Entity
@Table(name = "cloudnext_org_unit")
public class OrgUnitMirror extends BaseMirrorEntity {

    @Column(name = "oa_id", nullable = false, length = 64, unique = true)
    private String oaId;

    @Column(name = "unique_name", length = 255)
    private String uniqueName;

    @Column(name = "distinguished_name", length = 255)
    private String distinguishedName;

    @Column(name = "parent_oa_id", length = 64)
    private String parentOaId;

    @Column(name = "level_value")
    private Integer levelValue;

    @Column(name = "level_name", length = 1024)
    private String levelName;
    @Transient private long affectedReferenceCount;

    public String getOaId() {
        return oaId;
    }

    public void setOaId(String oaId) {
        this.oaId = oaId;
    }

    public String getUniqueName() {
        return uniqueName;
    }

    public void setUniqueName(String uniqueName) {
        this.uniqueName = uniqueName;
    }

    public String getDistinguishedName() {
        return distinguishedName;
    }

    public void setDistinguishedName(String distinguishedName) {
        this.distinguishedName = distinguishedName;
    }

    public String getParentOaId() {
        return parentOaId;
    }

    public void setParentOaId(String parentOaId) {
        this.parentOaId = parentOaId;
    }

    public Integer getLevelValue() {
        return levelValue;
    }

    public void setLevelValue(Integer levelValue) {
        this.levelValue = levelValue;
    }

    public String getLevelName() {
        return levelName;
    }

    public void setLevelName(String levelName) {
        this.levelName = levelName;
    }
    public long getAffectedReferenceCount(){return affectedReferenceCount;} public void setAffectedReferenceCount(long v){affectedReferenceCount=v;}
}
