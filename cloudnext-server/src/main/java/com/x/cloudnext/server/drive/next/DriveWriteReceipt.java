package com.x.cloudnext.server.drive.next;
import javax.persistence.*;
/** Durable callback idempotency. Stores only a SHA-256 digest, never an editor ticket or URL. */
@Entity @Table(name="cloudnext_drive_write_receipt",indexes = { @Index(name="idx_receipt_item",columnList="item_id"), @javax.persistence.Index(name = "idx_namespace_drive_write_receipt", columnList = "drive_id") })
@org.hibernate.annotations.Filter(name = "driveNamespace", condition = "drive_id = :driveId")
public class DriveWriteReceipt extends com.x.cloudnext.server.namespace.DriveScopedEntity {
 @Id @Column(length=64) public String id;
 @Column(name="item_id",nullable=false,length=64) public String itemId;
}
