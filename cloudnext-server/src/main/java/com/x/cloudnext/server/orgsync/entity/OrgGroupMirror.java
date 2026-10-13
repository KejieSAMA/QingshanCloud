package com.x.cloudnext.server.orgsync.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "cloudnext_org_group")
public class OrgGroupMirror extends BaseMirrorEntity {

    @Column(name = "oa_id", nullable = false, length = 64, unique = true)
    private String oaId;

    @Column(name = "unique_name", length = 255)
    private String uniqueName;

    @Column(name = "distinguished_name", length = 255)
    private String distinguishedName;

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
}
