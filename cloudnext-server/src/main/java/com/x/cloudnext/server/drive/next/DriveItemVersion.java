package com.x.cloudnext.server.drive.next;
import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
@Entity
@Table(name="cloudnext_drive_item_version",uniqueConstraints=@UniqueConstraint(columnNames={"item_id","version_number"}),
 indexes=@Index(name="idx_version_item",columnList="item_id"))
public class DriveItemVersion {
 @Id @Column(length=64) public String id;
 @Column(name="item_id",length=64,nullable=false) public String itemId;
 @Column(name="version_number",nullable=false) public long versionNumber;
 @JsonIgnore @Column(name="storage_key",length=255,nullable=false) public String storageKey;
 @Column(name="file_name",length=255,nullable=false) public String fileName;
 @Column(name="content_type",length=255,nullable=false) public String contentType;
 @Column(name="size_bytes",nullable=false) public long sizeBytes;
 @Column(length=64,nullable=false) public String sha256;
 @Column(name="person_key",length=64,nullable=false) public String personKey="";
 @Column(name="person_name",length=255,nullable=false) public String personName="未知";
 @Column(name="created_at",length=64,nullable=false) public String createdAt;
}
