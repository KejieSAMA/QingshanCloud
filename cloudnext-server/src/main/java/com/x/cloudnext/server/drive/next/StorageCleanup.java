package com.x.cloudnext.server.drive.next;
import javax.persistence.*;
@Entity @Table(name="cloudnext_storage_cleanup")
public class StorageCleanup {
 @Id @Column(name="storage_key",length=255) public String storageKey;
 @Column(name="queued_at",nullable=false,length=64) public String queuedAt;
}
