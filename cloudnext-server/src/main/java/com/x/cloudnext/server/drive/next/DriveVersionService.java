package com.x.cloudnext.server.drive.next;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.*;
import com.x.cloudnext.server.drive.*;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.auth.AuthLoginResponse;

@Service
public class DriveVersionService {
 private static final Logger LOG=LoggerFactory.getLogger(DriveVersionService.class);
 private final DriveItemVersionRepository versions;
 private final DriveStorageService storage;
 private final DriveScopeAccessService access;
 private final DriveUserItemRepository userItems;
 @org.springframework.beans.factory.annotation.Autowired private DriveWriteReceiptRepository receipts;
 @org.springframework.beans.factory.annotation.Autowired private DriveStorageCleanupService cleanup;
 @PersistenceContext private EntityManager em;
 public DriveVersionService(DriveItemVersionRepository versions,DriveStorageService storage,DriveScopeAccessService access,DriveUserItemRepository userItems){
  this.versions=versions;this.storage=storage;this.access=access;this.userItems=userItems;
 }
 @Transactional
 public DriveStoredFile store(DriveItem item,MultipartFile file,String actor,String name) {
  lock(item);
  String key=UUID.randomUUID().toString();
  DriveStoredFile stored=storage.store(key,file);
  cleanupOnRollback(stored.getStorageKey());
  return commit(item,stored,file.getOriginalFilename(),actor,name);
 }
 @Transactional
 public DriveStoredFile store(DriveItem item,byte[] bytes,String actor,String name,String callbackKey) {
  lock(item);
  String receiptKey=hash(new org.springframework.core.io.ByteArrayResource(callbackKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  if(receipts.existsById(receiptKey))return new DriveStoredFile(item.getStorageKey(),item.getSizeBytes(),item.getContentType());
  DriveWriteReceipt receipt=new DriveWriteReceipt();receipt.id=receiptKey;receipt.itemId=item.getId();receipts.save(receipt);
  DriveStoredFile stored=storage.store(UUID.randomUUID().toString(),item.getName(),bytes,item.getContentType());
  cleanupOnRollback(stored.getStorageKey());
  return commit(item,stored,item.getName(),actor,name);
 }
 private void lock(DriveItem item) {
  if(em.contains(item)) em.refresh(item,LockModeType.PESSIMISTIC_WRITE);
 }
 private DriveStoredFile commit(DriveItem item,DriveStoredFile stored,String fileName,String actor,String actorName){
  List<DriveItemVersion> previous=versions.findByItemIdOrderByVersionNumberDesc(item.getId());
  String digest=hash(storage.load(stored.getStorageKey()));
  if(previous.isEmpty() && item.getStorageKey()!=null && !item.getStorageKey().isEmpty()){
   DriveItemVersion baseline=record(item,item.getStorageKey(),item.getName(),item.getContentType(),item.getSizeBytes(),hash(storage.load(item.getStorageKey())),1,"","未知");
   baseline.createdAt=item.getUpdatedAt().isEmpty()?Instant.now().toString():item.getUpdatedAt();
   versions.save(baseline);previous=Collections.singletonList(baseline);
  }
  if(!previous.isEmpty() && previous.get(0).sha256.equals(digest)){
   afterCommitDelete(Collections.singleton(stored.getStorageKey()));
   DriveItemVersion current=previous.get(0);
   return new DriveStoredFile(current.storageKey,current.sizeBytes,current.contentType);
  }
  long number=previous.isEmpty()?1:previous.get(0).versionNumber+1;
  versions.save(record(item,stored.getStorageKey(),fileName,stored.getContentType(),stored.getSizeBytes(),digest,number,actor,actorName));
  return stored;
 }
 private DriveItemVersion record(DriveItem item,String key,String name,String type,Long size,String hash,long number,String actor,String actorName){
  DriveItemVersion v=new DriveItemVersion();v.id=UUID.randomUUID().toString();v.itemId=item.getId();v.versionNumber=number;
  v.storageKey=key;v.fileName=name==null?item.getName():name;v.contentType=type==null?"application/octet-stream":type;
  v.sizeBytes=size==null?0:size;v.sha256=hash;v.personKey=actor==null?"":actor;v.personName=actorName==null||actorName.isEmpty()?"未知":actorName;
  v.createdAt=Instant.now().toString();return v;
 }
 @Transactional
 public void recordCopy(DriveItem item,String actor,String name){
  if(item.getItemType()==DriveItemType.FILE)
   versions.save(record(item,item.getStorageKey(),item.getName(),item.getContentType(),item.getSizeBytes(),hash(storage.load(item.getStorageKey())),1,actor,name));
 }
 public void cleanupOnRollback(String key){
  cleanup.onRollback(key);
 }
 private void afterCommitDelete(Collection<String> keys){
  cleanup.afterCommit(keys);
 }
 private void delete(String key){try{storage.delete(key);}catch(RuntimeException e){LOG.error("Version storage cleanup failed key={}",key,e);}}
 @Transactional
 public void purge(DriveItem item){
  Set<String> keys=new HashSet<>();versions.findByItemIdOrderByVersionNumberDesc(item.getId()).forEach(v->keys.add(v.storageKey));
  if(item.getStorageKey()!=null&&!item.getStorageKey().isEmpty())keys.add(item.getStorageKey());
  versions.deleteByItemId(item.getId());userItems.deleteByItemId(item.getId());receipts.deleteByItemId(item.getId());afterCommitDelete(keys);
 }
 @Transactional(readOnly=true)
 public List<DriveItemVersion> list(AuthLoginResponse session,String id){
  DriveItem item=require(session,id);
  List<DriveItemVersion> list=versions.findByItemIdOrderByVersionNumberDesc(id);
  if(list.isEmpty()){
   DriveItemVersion current=record(item,item.getStorageKey(),item.getName(),item.getContentType(),item.getSizeBytes(),"",1,"","未知");
   current.id="current";current.createdAt=item.getUpdatedAt();return Collections.singletonList(current);
  }return list;
 }
 @Transactional(readOnly=true)
 public DriveItemVersion requireVersion(AuthLoginResponse session,String id,String versionId){
  DriveItem item=require(session,id);
  if("current".equals(versionId))return list(session,id).get(0);
  DriveItemVersion v=versions.findById(versionId).orElseThrow(()->notFound());
  if(!id.equals(v.itemId))throw notFound();return v;
 }
 private DriveItem require(AuthLoginResponse session,String id){
  DriveItem item=access.requireItem(session,id,DriveScopeAction.WRITE);
  if(item.isDeleted()||item.getItemType()!=DriveItemType.FILE)throw notFound();
  // Every ancestor must still be live; historical content cannot bypass deleted folders.
  Set<String> seen=new HashSet<>();DriveItem parent=item;
  while(parent!=null){if(parent.isDeleted()||!seen.add(parent.getId()))throw notFound();if(parent.getParentId().isEmpty())break;
   parent=em.find(DriveItem.class,parent.getParentId());if(parent==null)throw notFound();}
  return item;
 }
 public Resource content(DriveItemVersion v){Resource resource=storage.load(v.storageKey);if(!resource.exists())throw notFound();return resource;}
 public Set<String> storageKeys(){Set<String> keys=new HashSet<>();versions.findAll().forEach(v->keys.add(v.storageKey));return keys;}
 private ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"file version not found");}
 private String hash(Resource resource){
  if(resource==null||!resource.exists())throw new IllegalStateException("file version content is missing");
  try(InputStream in=resource.getInputStream()){MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];int n;
   while((n=in.read(b))!=-1)if(n>0)d.update(b,0,n);StringBuilder s=new StringBuilder();for(byte v:d.digest())s.append(String.format("%02x",v));return s.toString();
  }catch(Exception e){throw new IllegalStateException("cannot read file version",e);}
 }
}
