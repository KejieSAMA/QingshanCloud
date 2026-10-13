package com.x.cloudnext.server.drive.next;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.PageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.drive.*;
import com.x.cloudnext.server.drive.UnifiedSearchResponse.Result;
import com.x.cloudnext.server.drive.entity.*;
import com.x.cloudnext.server.drive.repository.*;
import com.x.cloudnext.server.workspace.entity.*;
import com.x.cloudnext.server.workspace.repository.*;
import com.x.cloudnext.server.audit.entity.AuditLog;
import com.x.cloudnext.server.audit.repository.AuditLogRepository;

@Service
public class DriveExperienceService {
 private final UnifiedSearchService search;
 private final DriveUserItemRepository userItems;
 private final UserPreferenceRepository preferences;
 private final DriveItemRepository items;
 private final DriveItemVersionRepository versions;
 private final DriveScopeAccessService access;
 private final WorkspaceRepository workspaces;
 private final AuditLogRepository audits;
 private final ObjectMapper mapper;
 @javax.persistence.PersistenceContext private javax.persistence.EntityManager em;
 @org.springframework.beans.factory.annotation.Autowired private DriveShareRepository shareRecords;
 @org.springframework.beans.factory.annotation.Autowired private com.x.cloudnext.server.form.repository.PrincipalBindingRepository principals;
 @Value("${CLOUDNEXT_PERSONAL_REFERENCE_CAPACITY_BYTES:107374182400}") private long referenceBytes;
 public DriveExperienceService(UnifiedSearchService search,DriveUserItemRepository userItems,UserPreferenceRepository preferences,
 DriveItemRepository items,DriveItemVersionRepository versions,DriveScopeAccessService access,WorkspaceRepository workspaces,AuditLogRepository audits,ObjectMapper mapper){
  this.search=search;this.userItems=userItems;this.preferences=preferences;this.items=items;this.versions=versions;
  this.access=access;this.workspaces=workspaces;this.audits=audits;this.mapper=mapper;
 }
 public static Map<String,Object> map(Object... values){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)m.put((String)values[i],values[i+1]);return m;}
 @Transactional(readOnly=true)
 public Map<String,Object> prefs(AuthLoginResponse session){
  Map<String,Object> result=map("primaryColor","blue","density","compact","layout","list","motion",true,"pageSize",50);
  for(UserPreference p:preferences.findByPersonOaId(session.getPersonOaId()))if(p.getPreferenceKey().startsWith("next.ui.")){
   String key=p.getPreferenceKey().substring(8),v=p.getPreferenceValue();if(!result.containsKey(key))continue;
   if("motion".equals(key))result.put(key,Boolean.parseBoolean(v));else if("pageSize".equals(key)){try{result.put(key,Integer.parseInt(v));}catch(NumberFormatException ignored){}}
   else result.put(key,v);
  }return result;
 }
 @Transactional
 public Map<String,Object> savePrefs(AuthLoginResponse session,Map<String,Object> request){
  Map<String,Object> result=prefs(session);
  for(Map.Entry<String,Object> e:request.entrySet()){
   String k=e.getKey(),v=String.valueOf(e.getValue());
   boolean valid=("primaryColor".equals(k)&&Arrays.asList("green","blue").contains(v))
     ||("density".equals(k)&&Arrays.asList("compact","comfortable").contains(v))
     ||("layout".equals(k)&&Arrays.asList("list","grid").contains(v))
     ||("motion".equals(k)&&Arrays.asList("true","false").contains(v))
     ||("pageSize".equals(k)&&Arrays.asList("20","50","100").contains(v));
   if(!valid)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"invalid UI preference");
   String key="next.ui."+k;
   UserPreference p=preferences.findByPersonOaIdAndPreferenceKey(session.getPersonOaId(),key).orElseGet(()->{UserPreference n=new UserPreference();n.setId(UUID.randomUUID().toString());n.setPersonOaId(session.getPersonOaId());n.setPreferenceKey(key);return n;});
   p.setPreferenceValue(v);preferences.save(p);result.put(k,e.getValue());
  }return result;
 }
 private Map<String,Result> visible(AuthLoginResponse session){
  Map<String,Result> visible=search.visibleItems(session,"all");
  Map<String,DriveUserItem> states=userItems.findByPersonKey(session.getPersonOaId()).stream().collect(Collectors.toMap(s->s.itemId,s->s));
  Set<String> ids=visible.values().stream().filter(r->!"workspace".equals(r.resultType)).map(Result::getId).collect(Collectors.toSet());
  Map<String,DriveItemVersion> latest=new HashMap<>();
  if(!ids.isEmpty())for(DriveItemVersion v:versions.findByItemIdIn(ids)){DriveItemVersion old=latest.get(v.itemId);if(old==null||old.versionNumber<v.versionNumber)latest.put(v.itemId,v);}
  Set<String> sharedIds=shareRecords.findAll().stream().filter(s->!s.isRevoked()&&(safe(s.getExpiresAt()).isEmpty()||s.getExpiresAt().compareTo(Instant.now().toString())>0)).map(DriveShare::getItemId).collect(Collectors.toSet());
  Map<String,DriveItem> nodes=new HashMap<>();if(!ids.isEmpty())items.findAllById(ids).forEach(i->nodes.put(i.getId(),i));
  for(DriveItem i:nodes.values()){DriveItem p=i;Set<String> seen=new HashSet<>();while(p!=null&&seen.add(p.getId())){if(sharedIds.contains(p.getId())){sharedIds.add(i.getId());break;}p=nodes.get(p.getParentId());}}
  Map<String,String> names=new HashMap<>();principals.findAll().forEach(p->names.put(p.getCanonicalPersonId(),p.getDisplayName()));
  Map<String,AuditLog> lastChange=new HashMap<>();
  if(!ids.isEmpty())audits.findByTargetTypeAndTargetIdInOrderByCreatedAtDesc("DRIVE_ITEM",ids).stream()
    .filter(a->Arrays.asList("UPLOAD","RENAME","MOVE","EDIT_COMPLETED").contains(a.getAction().name())).forEach(a->lastChange.putIfAbsent(a.getTargetId(),a));
  visible.values().forEach(r->{DriveUserItem state=states.get(r.getId());r.favorite=state!=null&&state.favorite;r.shared=sharedIds.contains(r.getId());r.setLastAccessedAt(state==null?"":state.openedAt);
   DriveItemVersion v=latest.get(r.getId());if(v!=null)r.modifiedByName=v.personName;
   AuditLog change=lastChange.get(r.getId());if(change!=null)r.modifiedByName=names.getOrDefault(change.getPersonOaId(),r.modifiedByName);});
  return visible;
 }
 @Transactional(readOnly=true)
 public List<Result> presentation(AuthLoginResponse session,List<String> ids){
  if(ids==null||ids.size()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"at most 100 item IDs");
  Map<String,Result> visible=visible(session);List<Result> result=new ArrayList<>();
  for(String id:new LinkedHashSet<>(ids)){Result r=visible.get(id);if(r!=null)result.add(r);}return result;
 }
 @Transactional(readOnly=true)
 public Map<String,Object> query(AuthLoginResponse session,String kind,Map<String,String> q){
  List<Result> list=new ArrayList<>(visible(session).values());list.removeIf(r->"workspace".equals(r.resultType));
  if("recent".equals(kind))list.removeIf(r->r.getLastAccessedAt()==null||r.getLastAccessedAt().isEmpty());
  if("favorites".equals(kind))list.removeIf(r->!r.favorite);
  if("home".equals(kind))list.removeIf(r->!"file".equals(r.resultType));
  String keyword=q.getOrDefault("keyword","").trim().toLowerCase(Locale.ROOT);
  String type=q.getOrDefault("itemType","all"),fileType=q.getOrDefault("fileType",""),owner=q.getOrDefault("owner","");
  list.removeIf(r->!Arrays.stream(keyword.split("\\s+")).allMatch(w->r.getName().toLowerCase(Locale.ROOT).contains(w))
   ||(!"all".equals(type)&&!type.equals(r.resultType))||(!fileType.isEmpty()&&!fileType.equals(r.getFileType()))||(!owner.isEmpty()&&!owner.equals(r.getOwnerPersonOaId())));
  sort(list,q,"recent".equals(kind)?"lastAccessedAt":"updatedAt");
  return page(list,q);
 }
 public static void sort(List<Result> list,Map<String,String> q,String defaultSort){
  String sort=q.getOrDefault("sortBy",defaultSort),order=q.getOrDefault("sortOrder","desc");
  Comparator<Result> comparator;
  if("name".equals(sort))comparator=(a,b)->NaturalOrder.compare(a.getName(),b.getName());
  else if("sizeBytes".equals(sort))comparator=Comparator.comparingLong(a->a.getSizeBytes()==null?0:a.getSizeBytes());
  else if("ownerName".equals(sort))comparator=(a,b)->NaturalOrder.compare(a.getOwnerName(),b.getOwnerName());
  else comparator=Comparator.comparing(a->safe("lastAccessedAt".equals(sort)?a.getLastAccessedAt():"createdAt".equals(sort)?a.getCreatedAt():"deletedAt".equals(sort)?a.getDeletedAt():a.getUpdatedAt()));
  if("desc".equals(order))comparator=comparator.reversed();
  list.sort(Comparator.comparingInt((Result r)->"folder".equals(r.resultType)?0:1).thenComparing(comparator).thenComparing((a,b)->NaturalOrder.compare(a.getName(),b.getName())).thenComparing(Result::getId));
 }
 public static Map<String,Object> page(List<?> list,Map<String,String> q){
  int size=Math.min(100,Math.max(1,number(q.get("size"),50))),totalPages=Math.max(1,(list.size()+size-1)/size);
  int page=Math.min(totalPages,Math.max(1,number(q.get("page"),1))),start=(page-1)*size;
  return map("items",list.subList(start,Math.min(list.size(),start+size)),"total",list.size(),"page",page,"size",size,"totalPages",totalPages,"hasNext",page<totalPages);
 }
 private static int number(String v,int d){try{return Integer.parseInt(v);}catch(Exception e){return d;}}
 private static String safe(String v){return v==null?"":v;}
 @Transactional(readOnly=true)
 public Map<String,Object> home(AuthLoginResponse session,Map<String,String> q){
  Map<String,Object> result=query(session,"home",q);
  List<Result> all=new ArrayList<>(visible(session).values());
  List<Result> folders=all.stream().filter(r->"folder".equals(r.resultType)&&(r.favorite||("personal".equals(r.source)&&safe(r.getParentId()).isEmpty()))).sorted(Comparator.comparing((Result r)->!r.favorite).thenComparing(Result::getName)).limit(4).collect(Collectors.toList());
  List<Result> recent=all.stream().filter(r->"file".equals(r.resultType)&&!safe(r.getLastAccessedAt()).isEmpty()).sorted(Comparator.comparing((Result r)->r.getLastAccessedAt()).reversed()).limit(3).collect(Collectors.toList());
  result.put("quickFolders",folders);result.put("recent",recent);return result;
 }
 @Transactional(readOnly=true)
 public Map<String,Object> browse(AuthLoginResponse session,Map<String,String> q){
  String source=q.getOrDefault("source","personal"),parent=q.getOrDefault("parentId","");
  if(!Arrays.asList("personal","workspace","shared").contains(source))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"invalid source");
  Map<String,Result> decorated=visible(session);
  List<Result> list=new ArrayList<>(search.visibleItems(session,source).values());
  list.removeIf(r->"workspace".equals(r.resultType)||!parent.equals(r.locationParentId)
    ||("workspace".equals(source)&&!q.getOrDefault("workspaceId","").equals(r.workspaceId))
    ||("shared".equals(source)&&!q.getOrDefault("shareId","").isEmpty()&&!q.get("shareId").equals(r.shareId)));
  for(Result r:list){Result d=decorated.get(r.getId());if(d!=null){r.favorite=d.favorite;r.shared=d.shared;r.modifiedByName=d.modifiedByName;}}
  String keyword=q.getOrDefault("keyword","").toLowerCase(Locale.ROOT),type=q.getOrDefault("itemType",""),filter=q.getOrDefault("fileType","");
  list.removeIf(r->!Arrays.stream(keyword.trim().split("\\s+")).allMatch(w->r.getName().toLowerCase(Locale.ROOT).contains(w))
   ||(!type.isEmpty()&&!"all".equals(type)&&!type.equals(r.resultType))||!fileFilter(r,filter));
  sort(list,q,"updatedAt");return page(list,q);
 }
 private boolean fileFilter(Result r,String f){
  if(f.isEmpty()||"all".equals(f))return true;
  if("doc".equals(f))return "file".equals(r.resultType)&&(Arrays.asList("word","pdf","text").contains(r.getFileType())||r.getName().matches("(?i).*\\.(md|txt)$"));
  if("other".equals(f))return "file".equals(r.resultType)&&!Arrays.asList("word","pdf","text","excel","image").contains(r.getFileType());
  return "file".equals(r.resultType)&&f.equals(r.getFileType());
 }
 @Transactional
 public void mark(AuthLoginResponse session,String id,boolean favorite,boolean opened){
  Result r=search.visibleItems(session,"all").get(id);if(r==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"item not found");
  em.find(DriveItem.class,id,javax.persistence.LockModeType.PESSIMISTIC_WRITE);
  DriveUserItem state=userItems.findByPersonKeyAndItemId(session.getPersonOaId(),id).orElseGet(()->{DriveUserItem s=new DriveUserItem();s.id=UUID.randomUUID().toString();s.personKey=session.getPersonOaId();s.itemId=id;return s;});
  if(opened)state.openedAt=Instant.now().toString();else state.favorite=favorite;userItems.save(state);
 }
 @Transactional(readOnly=true)
 public Map<String,Object> details(AuthLoginResponse session,String id){
  Result r=visible(session).get(id);
  if(r==null){DriveItem item=access.requireItem(session,id,DriveScopeAction.WRITE);if(!item.isDeleted())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"item not found");
   return map("id",item.getId(),"name",item.getName(),"itemType",item.getItemType().name().toLowerCase(),"deleted",true,"sizeBytes",item.getSizeBytes(),"deletedAt",item.getDeletedAt(),"ownerName",item.getOwnerName(),"displayPath","回收站","modifiedByName","未知");}
  return mapper.convertValue(r,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
 }
 @Transactional(readOnly=true)
 public Map<String,Object> usage(AuthLoginResponse session){
  List<DriveItem> personal=items.findByScopeTypeAndScopeId(DriveScopeType.PERSONAL,session.getPersonOaId());
  Map<String,Long> current=new HashMap<>(),trash=new HashMap<>(),history=new HashMap<>();
  Set<String> ids=new HashSet<>();
  for(DriveItem i:personal)if(i.getItemType()==DriveItemType.FILE){ids.add(i.getId());(i.isDeleted()?trash:current).put(i.getStorageKey(),i.getSizeBytes());}
  if(!ids.isEmpty())for(DriveItemVersion v:versions.findByItemIdIn(ids))if(!current.containsKey(v.storageKey)&&!trash.containsKey(v.storageKey))history.put(v.storageKey,v.sizeBytes);
  long live=sum(current),deleted=sum(trash),old=sum(history);
  Map<String,Map<String,Object>> spaceUsage=new LinkedHashMap<>();
  for(Result r:search.visibleItems(session,"workspace").values()){
   if(!"file".equals(r.resultType))continue;
   Map<String,Object> usage=spaceUsage.computeIfAbsent(r.workspaceId,k->map("fileCount",0L,"activeBytes",0L));
   usage.put("fileCount",((Long)usage.get("fileCount"))+1L);
   usage.put("activeBytes",((Long)usage.get("activeBytes"))+(r.getSizeBytes()==null?0L:r.getSizeBytes()));
  }
  return map("usedBytes",live+deleted+old,"activeBytes",live,"trashBytes",deleted,"versionBytes",old,"referenceBytes",referenceBytes,"enforced",false,"workspaces",spaceUsage);
 }
 private long sum(Map<String,Long> m){return m.values().stream().mapToLong(v->v==null?0:v).sum();}
 @Transactional(readOnly=true)
 public Map<String,Object> workspaceTrash(AuthLoginResponse session,String id,Map<String,String> q){
  Workspace w=workspaces.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"workspace not found"));
  if(!Workspace.STATUS_ACTIVE.equals(w.getStatus())||!Arrays.asList(WorkspaceRole.OWNER,WorkspaceRole.ADMIN,WorkspaceRole.MEMBER).contains(access.resolveWorkspaceRole(session,id)))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"workspace access denied");
  List<Result> list=new ArrayList<>();
  for(DriveItem i:items.findByScopeTypeAndScopeIdAndDeletedTrue(DriveScopeType.WORKSPACE,id)){
   Result r=new Result();r.setId(i.getId());r.setName(i.getName());r.setItemType(i.getItemType().name().toLowerCase(Locale.ROOT));r.resultType=r.getItemType();
   r.setFileType(i.getFileType());r.setSizeBytes(i.getSizeBytes());r.setDeleted(true);r.setDeletedAt(i.getDeletedAt());r.setUpdatedAt(i.getDeletedAt());r.setOwnerName(i.getOwnerName());
   r.workspaceId=id;r.source="workspace";r.scopeType="WORKSPACE";r.scopeId=id;r.displayPath=w.getName()+" / 回收站";list.add(r);
  }
  String keyword=q.getOrDefault("keyword","").toLowerCase(Locale.ROOT);
  list.removeIf(r->!r.getName().toLowerCase(Locale.ROOT).contains(keyword));sort(list,q,"deletedAt");return page(list,q);
 }
 @Transactional(readOnly=true)
 public List<Map<String,Object>> transfers(AuthLoginResponse session){
  return audits.findByPersonOaIdOrderByCreatedAtDesc(session.getPersonOaId(),PageRequest.of(0,500)).getContent().stream()
   .filter(a->a.getAction()==com.x.cloudnext.server.audit.AuditAction.UPLOAD&&"DRIVE_ITEM".equals(a.getTargetType()))
   .limit(50).map(this::event).collect(Collectors.toList());
 }
 @Transactional(readOnly=true)
 public List<Map<String,Object>> activities(AuthLoginResponse session,String workspaceId){
  Workspace w=workspaces.findById(workspaceId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"workspace not found"));
  if(!Workspace.STATUS_ACTIVE.equals(w.getStatus())||access.resolveWorkspaceRole(session,workspaceId)==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"workspace access denied");
  Set<String> allowed=search.visibleItems(session,"workspace").values().stream().filter(r->workspaceId.equals(r.workspaceId)).map(Result::getId).collect(Collectors.toSet());
  return audits.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"createdAt")).stream()
   .filter(a->"DRIVE_ITEM".equals(a.getTargetType())&&allowed.contains(a.getTargetId())).limit(20).map(this::event).collect(Collectors.toList());
 }
 private Map<String,Object> event(AuditLog a){
  Map<String,Object> p=Collections.emptyMap();try{p=mapper.readValue(a.getPayload(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}catch(Exception ignored){}
  return map("id",a.getId(),"itemId",a.getTargetId(),"personKey",a.getPersonOaId(),"name",p.getOrDefault("name","文件"),"sizeBytes",p.getOrDefault("sizeBytes",0),"action",a.getAction().name(),"createdAt",a.getCreatedAt().toString());
 }
}
