package com.x.cloudnext.server.drive.next;
import javax.persistence.*;
/** Per-principal state; never an access grant. */
@Entity
@Table(name="cloudnext_drive_user_item", uniqueConstraints=@UniqueConstraint(columnNames={"person_key","item_id"}),
 indexes=@Index(name="idx_user_item_person",columnList="person_key"))
public class DriveUserItem {
 @Id @Column(length=64) public String id;
 @Column(name="person_key",nullable=false,length=64) public String personKey;
 @Column(name="item_id",nullable=false,length=64) public String itemId;
 @Column(nullable=false) public boolean favorite;
 @Column(name="opened_at",length=64,nullable=false) public String openedAt="";
}
