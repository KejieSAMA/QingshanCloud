package com.x.cloudnext.server.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.cloudnext.server.migration.entity.LegacyArchiveMigrationTask;
import com.x.cloudnext.server.migration.repository.LegacyArchiveMigrationTaskRepository;
import com.x.cloudnext.server.orgsync.entity.*;
import com.x.cloudnext.server.orgsync.model.*;
import com.x.cloudnext.server.orgsync.repository.*;
import com.x.cloudnext.server.form.FormDirectoryApplyService;
import com.x.cloudnext.server.form.entity.DirectorySyncState;
import com.x.cloudnext.server.form.repository.DirectorySyncStateRepository;
import com.x.cloudnext.server.config.CloudNextProperties;

@Service
public class LegacyArchiveMigrationService {
    private final LegacyArchiveMigrationTaskRepository tasks;
    private final ObjectMapper mapper;
    private final OrgUnitMirrorRepository units;
    private final OrgPersonMirrorRepository persons;
    private final OrgIdentityMirrorRepository identities;
    private final OrgRoleMirrorRepository roles;
    private final OrgGroupMirrorRepository groups;
    private final OrgMembershipMirrorRepository memberships;
    private final MigrationInventoryService inventory;
    private final DirectorySyncStateRepository directoryStates;
    private final CloudNextProperties properties;

    public LegacyArchiveMigrationService(LegacyArchiveMigrationTaskRepository tasks,ObjectMapper mapper,
            OrgUnitMirrorRepository units,OrgPersonMirrorRepository persons,OrgIdentityMirrorRepository identities,
            OrgRoleMirrorRepository roles,OrgGroupMirrorRepository groups,OrgMembershipMirrorRepository memberships,
            MigrationInventoryService inventory,DirectorySyncStateRepository directoryStates,CloudNextProperties properties){
        this.tasks=tasks;this.mapper=mapper;this.units=units;this.persons=persons;this.identities=identities;
        this.roles=roles;this.groups=groups;this.memberships=memberships;this.inventory=inventory;this.directoryStates=directoryStates;this.properties=properties;
    }

    @Transactional
    public LegacyArchiveMigrationTask preview(LegacyArchiveImportRequest request,String actor){
        assertConfiguredCutoverVersion(request==null?null:request.getVersion());
        validate(request);
        try{
            String json=mapper.writeValueAsString(request);String checksum=sha256(json);
            LegacyArchiveMigrationTask existing=tasks.findById(request.getVersion()).orElse(null);
            if(existing!=null){if(!checksum.equals(existing.getChecksum()))throw conflict("migration version already exists with a different snapshot");return existing;}
            DirectorySyncState directory=directoryStates.findForUpdate(FormDirectoryApplyService.SOURCE_KEY).orElseThrow(()->conflict("a successful Form directory snapshot is required before preview"));
            assertSuccessfulDirectory(directory);
            if(properties.getCutover().isMaintenanceMode()&&!request.getVersion().equals(directory.getLastSuccessCutoverVersion()))throw conflict("the configured final migration requires a successful forced Form synchronization in this maintenance version");
            LegacyArchiveMigrationTask task=new LegacyArchiveMigrationTask();task.setVersion(request.getVersion());task.setStatus("PREVIEWED");task.setChecksum(checksum);task.setSnapshotJson(json);task.setInventoryJson(inventory.json(inventory.capture()));task.setFormSnapshotEtag(directory.getEtag());task.setFormSnapshotGeneration(directory.getLastSuccessGeneration());task.setFormSnapshotSuccessAt(directory.getLastSuccessAt());task.setCreatedBy(actor);
            task.setUnitCount(request.getUnits().size());task.setPersonCount(request.getPersons().size());task.setIdentityCount(request.getIdentities().size());task.setRoleCount(request.getRoles().size());task.setGroupCount(request.getGroups().size());task.setMembershipCount(request.getMemberships().size());return tasks.save(task);
        }catch(ResponseStatusException e){throw e;}catch(Exception e){throw new IllegalArgumentException("legacy snapshot cannot be serialized",e);}
    }

    @Transactional
    public LegacyArchiveMigrationTask approve(String version,String actor){assertConfiguredCutoverVersion(version);LegacyArchiveMigrationTask task=require(version);if(isAtLeast(task,"APPROVED"))return task;if(!"PREVIEWED".equals(task.getStatus()))throw conflict("task must be PREVIEWED before approval");task.setStatus("APPROVED");task.setApprovedBy(actor);task.setApprovedAt(Instant.now().toString());return tasks.save(task);}

    @Transactional
    public LegacyArchiveMigrationTask apply(String version,String actor){
        assertConfiguredCutoverVersion(version);
        LegacyArchiveMigrationTask task=require(version);if(isAtLeast(task,"APPLIED"))return task;if(!"APPROVED".equals(task.getStatus()))throw conflict("task must be APPROVED before apply");LegacyArchiveImportRequest request=read(task);
        units.saveAll(request.getUnits().stream().map(this::unit).collect(Collectors.toList()));persons.saveAll(request.getPersons().stream().map(this::person).collect(Collectors.toList()));identities.saveAll(request.getIdentities().stream().map(this::identity).collect(Collectors.toList()));roles.saveAll(request.getRoles().stream().map(this::role).collect(Collectors.toList()));groups.saveAll(request.getGroups().stream().map(this::group).collect(Collectors.toList()));memberships.saveAll(request.getMemberships().stream().map(this::membership).collect(Collectors.toList()));
        task.setStatus("APPLIED");task.setAppliedBy(actor);task.setAppliedAt(Instant.now().toString());return tasks.save(task);
    }

    @Transactional
    public LegacyArchiveMigrationTask verify(String version,String actor){
        assertConfiguredCutoverVersion(version);
        LegacyArchiveMigrationTask task=require(version);if("VERIFIED".equals(task.getStatus()))return task;if(!"APPLIED".equals(task.getStatus()))throw conflict("task must be APPLIED before verification");LegacyArchiveImportRequest request=read(task);
        boolean countsMatch=task.getUnitCount()==request.getUnits().size()&&task.getPersonCount()==request.getPersons().size()&&task.getIdentityCount()==request.getIdentities().size()&&task.getRoleCount()==request.getRoles().size()&&task.getGroupCount()==request.getGroups().size()&&task.getMembershipCount()==request.getMemberships().size();
        boolean recordsExist=request.getUnits().stream().allMatch(v->units.findById(v.getId()).map(e->sameUnit(e,v)).orElse(false))
                &&request.getPersons().stream().allMatch(v->persons.findById(v.getId()).map(e->samePerson(e,v)).orElse(false))
                &&request.getIdentities().stream().allMatch(v->identities.findById(v.getId()).map(e->sameIdentity(e,v)).orElse(false))
                &&request.getRoles().stream().allMatch(v->roles.findById(v.getId()).map(e->sameNamed(e,v)).orElse(false))
                &&request.getGroups().stream().allMatch(v->groups.findById(v.getId()).map(e->sameNamed(e,v)).orElse(false))
                &&request.getMemberships().stream().allMatch(v->memberships.findById(membershipId(v)).map(e->sameMembership(e,v)).orElse(false));
        if(!countsMatch||!recordsExist)throw conflict("legacy archive verification failed");try{inventory.assertMatches(task.getInventoryJson());}catch(IllegalStateException e){throw conflict(e.getMessage());}if(properties.getCutover().isMaintenanceMode()){DirectorySyncState directory=directoryStates.findForUpdate(FormDirectoryApplyService.SOURCE_KEY).orElseThrow(()->conflict("Form directory state is missing"));assertTaskDirectoryFence(task,directory);}task.setStatus("VERIFIED");task.setVerifiedBy(actor);task.setVerifiedAt(Instant.now().toString());return tasks.save(task);
    }

    public List<LegacyArchiveMigrationTask> list(){return tasks.findAll();}
    public LegacyArchiveMigrationTask get(String version){return tasks.findById(version).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"migration task not found"));}

    private void validate(LegacyArchiveImportRequest request){
        if(request==null||!StringUtils.hasText(request.getVersion())||request.getVersion().length()>128||!request.getVersion().matches("[A-Za-z0-9._-]+"))throw new IllegalArgumentException("migration version must contain only letters, digits, dot, underscore or dash");
        unique(request.getUnits(),"unit");unique(request.getPersons(),"person");unique(request.getIdentities(),"identity");unique(request.getRoles(),"role");unique(request.getGroups(),"group");
        Comparator<OrgSourceRecord> byId=Comparator.comparing(OrgSourceRecord::getId);request.getUnits().sort(byId);request.getPersons().sort(byId);request.getIdentities().sort(byId);request.getRoles().sort(byId);request.getGroups().sort(byId);
        Set<String> personIds=ids(request.getPersons()),unitIds=ids(request.getUnits()),identityIds=ids(request.getIdentities()),roleIds=ids(request.getRoles()),groupIds=ids(request.getGroups());
        for(OrgSourceRecord identity:request.getIdentities())if(!personIds.contains(identity.getPersonId())||!unitIds.contains(identity.getUnitId()))throw new IllegalArgumentException("legacy identity references missing person or unit: "+identity.getId());
        Set<String> membershipIds=new HashSet<>();for(OrgMembershipSourceRecord membership:request.getMemberships()){String id=membershipId(membership);if(!membershipIds.add(id))throw new IllegalArgumentException("legacy snapshot contains duplicate membership: "+id);if(!contains(membership.getSourceType(),membership.getSourceId(),personIds,unitIds,identityIds,roleIds,groupIds)||!contains(membership.getTargetType(),membership.getTargetId(),personIds,unitIds,identityIds,roleIds,groupIds))throw new IllegalArgumentException("legacy membership references a missing subject: "+id);}request.getMemberships().sort(Comparator.comparing(this::membershipId));
    }
    private boolean contains(String type,String id,Set<String> personIds,Set<String> unitIds,Set<String> identityIds,Set<String> roleIds,Set<String> groupIds){if(!StringUtils.hasText(type)||!StringUtils.hasText(id))return false;switch(type.toLowerCase()){case "person":return personIds.contains(id);case "unit":return unitIds.contains(id);case "identity":return identityIds.contains(id);case "role":return roleIds.contains(id);case "group":return groupIds.contains(id);default:return false;}}
    private Set<String> ids(List<OrgSourceRecord> values){return values.stream().map(OrgSourceRecord::getId).collect(Collectors.toSet());}
    private void unique(List<OrgSourceRecord> rows,String type){Set<String> ids=new HashSet<>();for(OrgSourceRecord row:rows)if(row==null||!StringUtils.hasText(row.getId())||!ids.add(row.getId()))throw new IllegalArgumentException("legacy snapshot contains missing or duplicate "+type+" id");}
    private LegacyArchiveMigrationTask require(String version){return tasks.findForUpdate(version).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"migration task not found"));}
    private ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private LegacyArchiveImportRequest read(LegacyArchiveMigrationTask task){try{if(!task.getChecksum().equals(sha256(task.getSnapshotJson())))throw conflict("stored legacy snapshot checksum does not match the approved task");return mapper.readValue(task.getSnapshotJson(),LegacyArchiveImportRequest.class);}catch(ResponseStatusException e){throw e;}catch(Exception e){throw new IllegalStateException("stored legacy snapshot is invalid",e);}}
    private boolean isAtLeast(LegacyArchiveMigrationTask task,String phase){if("APPROVED".equals(phase))return "APPROVED".equals(task.getStatus())||"APPLIED".equals(task.getStatus())||"VERIFIED".equals(task.getStatus());return "APPLIED".equals(task.getStatus())||"VERIFIED".equals(task.getStatus());}
    private void base(OrgSourceRecord source,BaseMirrorEntity target){target.setId(source.getId());target.setName(source.getName());target.setStatus(source.getStatus());target.setUpdatedAt(source.getUpdatedAt());target.setSyncVersion(source.getSyncVersion());}
    private OrgUnitMirror unit(OrgSourceRecord s){OrgUnitMirror e=new OrgUnitMirror();base(s,e);e.setOaId(s.getId());e.setUniqueName(s.getUniqueName());e.setDistinguishedName(s.getDistinguishedName());e.setParentOaId(s.getParentId());e.setLevelValue(s.getLevelValue());e.setLevelName(s.getLevelName());return e;}
    private OrgPersonMirror person(OrgSourceRecord s){OrgPersonMirror e=new OrgPersonMirror();base(s,e);e.setOaId(s.getId());e.setUniqueName(s.getUniqueName());e.setEmployeeNo(s.getEmployeeNo());e.setDistinguishedName(s.getDistinguishedName());e.setMobile(s.getMobile());e.setMail(s.getMail());return e;}
    private OrgIdentityMirror identity(OrgSourceRecord s){OrgIdentityMirror e=new OrgIdentityMirror();base(s,e);e.setOaId(s.getId());e.setUniqueName(s.getUniqueName());e.setDistinguishedName(s.getDistinguishedName());e.setPersonOaId(s.getPersonId());e.setUnitOaId(s.getUnitId());e.setUnitName(s.getUnitName());e.setUnitLevel(s.getUnitLevel());e.setUnitLevelName(s.getUnitLevelName());e.setMajorFlag(s.getMajorFlag());return e;}
    private OrgRoleMirror role(OrgSourceRecord s){OrgRoleMirror e=new OrgRoleMirror();base(s,e);e.setOaId(s.getId());e.setUniqueName(s.getUniqueName());e.setDistinguishedName(s.getDistinguishedName());return e;}
    private OrgGroupMirror group(OrgSourceRecord s){OrgGroupMirror e=new OrgGroupMirror();base(s,e);e.setOaId(s.getId());e.setUniqueName(s.getUniqueName());e.setDistinguishedName(s.getDistinguishedName());return e;}
    private OrgMembershipMirror membership(OrgMembershipSourceRecord s){OrgMembershipMirror e=new OrgMembershipMirror();e.setId(membershipId(s));e.setSourceType(s.getSourceType());e.setSourceOaId(s.getSourceId());e.setTargetType(s.getTargetType());e.setTargetOaId(s.getTargetId());e.setRelationType(s.getRelationType());e.setSyncVersion(s.getSyncVersion());return e;}
    private String membershipId(OrgMembershipSourceRecord s){return s.getSourceType()+":"+s.getSourceId()+":"+s.getTargetType()+":"+s.getTargetId()+":"+s.getRelationType();}
    private String sha256(String value)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte v:digest)b.append(String.format("%02x",v));return b.toString();}
    private boolean same(String left,String right){return left==null?right==null:left.equals(right);}
    private boolean sameBase(BaseMirrorEntity e,OrgSourceRecord v){return same(e.getId(),v.getId())&&same(e.getName(),v.getName())&&same(e.getStatus(),v.getStatus())&&same(e.getUpdatedAt(),v.getUpdatedAt())&&same(e.getSyncVersion(),v.getSyncVersion());}
    private boolean sameUnit(OrgUnitMirror e,OrgSourceRecord v){return sameBase(e,v)&&same(e.getOaId(),v.getId())&&same(e.getUniqueName(),v.getUniqueName())&&same(e.getDistinguishedName(),v.getDistinguishedName())&&same(e.getParentOaId(),v.getParentId())&&same(e.getLevelValue(),v.getLevelValue())&&same(e.getLevelName(),v.getLevelName());}
    private boolean samePerson(OrgPersonMirror e,OrgSourceRecord v){return sameBase(e,v)&&same(e.getOaId(),v.getId())&&same(e.getUniqueName(),v.getUniqueName())&&same(e.getEmployeeNo(),v.getEmployeeNo())&&same(e.getDistinguishedName(),v.getDistinguishedName())&&same(e.getMobile(),v.getMobile())&&same(e.getMail(),v.getMail());}
    private boolean sameIdentity(OrgIdentityMirror e,OrgSourceRecord v){return sameBase(e,v)&&same(e.getOaId(),v.getId())&&same(e.getUniqueName(),v.getUniqueName())&&same(e.getDistinguishedName(),v.getDistinguishedName())&&same(e.getPersonOaId(),v.getPersonId())&&same(e.getUnitOaId(),v.getUnitId())&&same(e.getUnitName(),v.getUnitName())&&same(e.getUnitLevel(),v.getUnitLevel())&&same(e.getUnitLevelName(),v.getUnitLevelName())&&same(e.getMajorFlag(),v.getMajorFlag());}
    private boolean sameNamed(BaseMirrorEntity e,OrgSourceRecord v){if(!sameBase(e,v))return false;if(e instanceof OrgRoleMirror){OrgRoleMirror n=(OrgRoleMirror)e;return same(n.getOaId(),v.getId())&&same(n.getUniqueName(),v.getUniqueName())&&same(n.getDistinguishedName(),v.getDistinguishedName());}OrgGroupMirror n=(OrgGroupMirror)e;return same(n.getOaId(),v.getId())&&same(n.getUniqueName(),v.getUniqueName())&&same(n.getDistinguishedName(),v.getDistinguishedName());}
    private boolean sameMembership(OrgMembershipMirror e,OrgMembershipSourceRecord v){return same(e.getId(),membershipId(v))&&same(e.getSourceType(),v.getSourceType())&&same(e.getSourceOaId(),v.getSourceId())&&same(e.getTargetType(),v.getTargetType())&&same(e.getTargetOaId(),v.getTargetId())&&same(e.getRelationType(),v.getRelationType())&&same(e.getSyncVersion(),v.getSyncVersion());}
    private boolean same(Object left,Object right){return left==null?right==null:left.equals(right);}
    private void assertConfiguredCutoverVersion(String version){if(properties.getCutover().isMaintenanceMode()&&!same(properties.getCutover().getMigrationVersion(),version))throw conflict("maintenance mode only permits the configured final migration version");}
    private void assertSuccessfulDirectory(DirectorySyncState directory){if(!StringUtils.hasText(directory.getEtag())||directory.getLastSuccessGeneration()<=0L||!("SUCCESS".equals(directory.getLastOutcome())||"NOT_MODIFIED".equals(directory.getLastOutcome())))throw conflict("the latest Form directory synchronization must be successful before preview");}
    private void assertTaskDirectoryFence(LegacyArchiveMigrationTask task,DirectorySyncState directory){assertSuccessfulDirectory(directory);if(task.getFormSnapshotGeneration()!=directory.getLastSuccessGeneration()||!same(task.getFormSnapshotEtag(),directory.getEtag())||!same(task.getFormSnapshotSuccessAt(),directory.getLastSuccessAt())||!same(task.getVersion(),directory.getLastSuccessCutoverVersion()))throw conflict("Form directory snapshot changed after migration preview");}
}
