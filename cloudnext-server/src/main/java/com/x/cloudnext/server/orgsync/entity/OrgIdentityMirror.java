package com.x.cloudnext.server.orgsync.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

@Entity
@Table(name = "cloudnext_org_identity")
public class OrgIdentityMirror extends BaseMirrorEntity {

    @Column(name = "oa_id", nullable = false, length = 64, unique = true)
    private String oaId;

    @Column(name = "unique_name", length = 255)
    private String uniqueName;

    @Column(name = "distinguished_name", length = 255)
    private String distinguishedName;

    @Column(name = "person_oa_id", length = 64)
    private String personOaId;

    @Column(name = "unit_oa_id", length = 64)
    private String unitOaId;

    @Column(name = "unit_name", length = 255)
    private String unitName;

    @Column(name = "unit_level")
    private Integer unitLevel;

    @Column(name = "unit_level_name", length = 1024)
    private String unitLevelName;

    @Column(name = "major_flag")
    private Boolean majorFlag;
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

    public String getPersonOaId() {
        return personOaId;
    }

    public void setPersonOaId(String personOaId) {
        this.personOaId = personOaId;
    }

    public String getUnitOaId() {
        return unitOaId;
    }

    public void setUnitOaId(String unitOaId) {
        this.unitOaId = unitOaId;
    }

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public Integer getUnitLevel() {
        return unitLevel;
    }

    public void setUnitLevel(Integer unitLevel) {
        this.unitLevel = unitLevel;
    }

    public String getUnitLevelName() {
        return unitLevelName;
    }

    public void setUnitLevelName(String unitLevelName) {
        this.unitLevelName = unitLevelName;
    }

    public Boolean getMajorFlag() {
        return majorFlag;
    }

    public void setMajorFlag(Boolean majorFlag) {
        this.majorFlag = majorFlag;
    }
    public long getAffectedReferenceCount(){return affectedReferenceCount;} public void setAffectedReferenceCount(long v){affectedReferenceCount=v;}
}
