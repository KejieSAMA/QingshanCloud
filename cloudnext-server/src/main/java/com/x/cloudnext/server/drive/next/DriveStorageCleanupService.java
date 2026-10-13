package com.x.cloudnext.server.drive.next;
import java.time.Instant;
import java.util.*;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import com.x.cloudnext.server.drive.DriveStorageService;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;
@Service
public class DriveStorageCleanupService {
 private static final Logger LOG=LoggerFactory.getLogger(DriveStorageCleanupService.class);
 private final StorageCleanupRepository queue;
 private final DriveStorageService storage;
 private final DriveItemRepository items;
 private final DriveItemVersionRepository versions;
 private final TransactionTemplate independent;
 public DriveStorageCleanupService(StorageCleanupRepository queue,DriveStorageService storage,DriveItemRepository items,DriveItemVersionRepository versions,PlatformTransactionManager manager){
  this.queue=queue;this.storage=storage;this.items=items;this.versions=versions;independent=new TransactionTemplate(manager);independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
 }
 public void afterCommit(Collection<String> keys){
  for(String key:keys){StorageCleanup row=new StorageCleanup();row.storageKey=key;row.queuedAt=Instant.now().toString();queue.save(row);}
  if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
   @Override public void afterCommit(){keys.forEach(DriveStorageCleanupService.this::clean);}
  });else keys.forEach(this::clean);
 }
 public void onRollback(String key){
  if(!TransactionSynchronizationManager.isSynchronizationActive())return;
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
   @Override public void afterCompletion(int status){
    if(status==STATUS_COMMITTED)return;
    try{independent.execute(s->{StorageCleanup row=new StorageCleanup();row.storageKey=key;row.queuedAt=Instant.now().toString();queue.save(row);return null;});clean(key);}
    catch(RuntimeException e){LOG.error("Could not enqueue rolled-back object for cleanup key={}",key,e);}
   }
  });
 }
 @Scheduled(fixedDelayString="${cloudnext.drive.storage-cleanup-delay-ms:60000}",initialDelayString="${cloudnext.drive.storage-cleanup-delay-ms:60000}")
 public void retry(){queue.findAll().stream().limit(100).map(r->r.storageKey).forEach(this::clean);}
 private void clean(String key){
  try{independent.execute(s->{
   if(!queue.existsById(key))return null;
   if(!items.existsByStorageKey(key)&&!versions.existsByStorageKey(key))storage.delete(key);
   queue.deleteById(key);return null;
  });}catch(RuntimeException e){LOG.warn("Storage cleanup will retry key={}",key,e);}
 }
 public Set<String> pendingKeys(){Set<String> keys=new HashSet<>();queue.findAll().forEach(r->keys.add(r.storageKey));return keys;}
}
