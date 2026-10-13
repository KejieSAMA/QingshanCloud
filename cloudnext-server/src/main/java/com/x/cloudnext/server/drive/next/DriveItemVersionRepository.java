package com.x.cloudnext.server.drive.next;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DriveItemVersionRepository extends JpaRepository<DriveItemVersion,String> {
 boolean existsByStorageKey(String storageKey);
 List<DriveItemVersion> findByItemIdOrderByVersionNumberDesc(String itemId);
 List<DriveItemVersion> findByItemIdIn(Collection<String> itemIds);
 void deleteByItemId(String itemId);
}
