package com.x.cloudnext.server.drive.next;
import javax.persistence.*;
/** Durable callback idempotency. Stores only a SHA-256 digest, never an editor ticket or URL. */
@Entity @Table(name="cloudnext_drive_write_receipt",indexes=@Index(name="idx_receipt_item",columnList="item_id"))
public class DriveWriteReceipt {
 @Id @Column(length=64) public String id;
 @Column(name="item_id",nullable=false,length=64) public String itemId;
}
