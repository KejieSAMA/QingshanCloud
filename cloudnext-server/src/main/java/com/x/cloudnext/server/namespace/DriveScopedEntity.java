package com.x.cloudnext.server.namespace;

import javax.persistence.*;

@MappedSuperclass
public abstract class DriveScopedEntity {
    @Column(name = "drive_id", nullable = false, length = 64, updatable = false)
    private String driveId = DriveContext.id();
    public String getDriveId() { return driveId; }
    public void setDriveId(String value) {
        if (driveId != null && !driveId.equals(value)) throw new IllegalStateException("drive ownership is immutable");
        driveId = value;
    }
    @PrePersist
    public void assignDriveNamespace() {
        if (driveId == null) driveId = DriveContext.requireId();
        DriveContext.assertSame(driveId);
    }
    // Hibernate filters do not apply to EntityManager.find()/getReference(). This is an additional ID-read guard.
    @PostLoad @PreUpdate @PreRemove
    public void checkDriveNamespace() { DriveContext.assertSame(driveId); }
}
