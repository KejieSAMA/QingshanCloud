package com.x.cloudnext.server.drive.next;
import javax.persistence.*;
/** Per-principal state; never an access grant. */
@Entity
@Table(name="cloudnext_drive_user_item", uniqueConstraints=@UniqueConstraint(columnNames={"person_key","item_id"}),
 indexes = { @Index(name="idx_user_item_person",columnList="person_key"), @javax.persistence.Index(name = "idx_namespace_drive_user_item", columnList = "drive_id") })
@org.hibernate.annotations.Filter(name = "driveNamespace", condition = "drive_id = :driveId")
public class DriveUserItem extends com.x.cloudnext.server.namespace.DriveScopedEntity {
 @Id @Column(length=64) public String id;
 @Column(name="person_key",nullable=false,length=64) public String personKey;
 @Column(name="item_id",nullable=false,length=64) public String itemId;
 @Column(nullable=false) public boolean favorite;
 @Column(name="opened_at",length=64,nullable=false) public String openedAt="";
}
