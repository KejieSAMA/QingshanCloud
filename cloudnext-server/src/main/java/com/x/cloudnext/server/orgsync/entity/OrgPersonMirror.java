package com.x.cloudnext.server.orgsync.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

@Entity
@Table(name = "cloudnext_org_person")
public class OrgPersonMirror extends BaseMirrorEntity {

    @Column(name = "oa_id", nullable = false, length = 64, unique = true)
    private String oaId;

    @Column(name = "unique_name", length = 255)
    private String uniqueName;

    @Column(name = "employee_no", length = 128)
    private String employeeNo;

    @Column(name = "distinguished_name", length = 255)
    private String distinguishedName;

    @Column(name = "mobile", length = 128)
    private String mobile;

    @Column(name = "mail", length = 255)
    private String mail;
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
    public String getEmployeeNo(){return employeeNo;} public void setEmployeeNo(String v){employeeNo=v;}

    public String getDistinguishedName() {
        return distinguishedName;
    }

    public void setDistinguishedName(String distinguishedName) {
        this.distinguishedName = distinguishedName;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getMail() {
        return mail;
    }

    public void setMail(String mail) {
        this.mail = mail;
    }
    public long getAffectedReferenceCount(){return affectedReferenceCount;} public void setAffectedReferenceCount(long v){affectedReferenceCount=v;}
}
