package com.x.cloudnext.server.drive.next;
import org.springframework.data.jpa.repository.JpaRepository;
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public interface DriveWriteReceiptRepository extends JpaRepository<DriveWriteReceipt,String> {
 void deleteByItemId(String itemId);
}
