package com.x.cloudnext.server.drive.next;
import javax.persistence.*;
@Entity @Table(name="cloudnext_storage_cleanup", indexes = @javax.persistence.Index(name = "idx_namespace_storage_cleanup", columnList = "drive_id"))
@org.hibernate.annotations.Filter(name = "driveNamespace", condition = "drive_id = :driveId")
public class StorageCleanup extends com.x.cloudnext.server.namespace.DriveScopedEntity {
 @Id @Column(name="storage_key",length=255) public String storageKey;
 @Column(name="queued_at",nullable=false,length=64) public String queuedAt;
}
