package com.x.cloudnext.server.namespace;

import javax.persistence.*;

@Entity
@Table(name = "cloudnext_drive_namespace", uniqueConstraints = @UniqueConstraint(name = "uk_drive_namespace_entry", columnNames = {"tenant_id", "entry_id"}))
public class DriveNamespace {
    @Id @Column(length = 64, nullable = false) private String id;
    @Column(name = "tenant_id", length = 128, nullable = false) private String tenantId;
    @Column(name = "entry_id", length = 80, nullable = false) private String entryId;
    public String getId() { return id; }
    public void setId(String v) { id = v; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String v) { tenantId = v; }
    public String getEntryId() { return entryId; }
    public void setEntryId(String v) { entryId = v; }
}
