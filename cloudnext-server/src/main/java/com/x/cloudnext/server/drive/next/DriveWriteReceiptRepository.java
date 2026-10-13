package com.x.cloudnext.server.drive.next;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DriveWriteReceiptRepository extends JpaRepository<DriveWriteReceipt,String> {
 void deleteByItemId(String itemId);
}
