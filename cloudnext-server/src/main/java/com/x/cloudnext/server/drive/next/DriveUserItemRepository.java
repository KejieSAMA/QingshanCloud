package com.x.cloudnext.server.drive.next;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DriveUserItemRepository extends JpaRepository<DriveUserItem,String> {
 List<DriveUserItem> findByPersonKey(String personKey);
 Optional<DriveUserItem> findByPersonKeyAndItemId(String personKey,String itemId);
 void deleteByItemId(String itemId);
}
