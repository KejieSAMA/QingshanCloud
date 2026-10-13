package com.x.cloudnext.server.form;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.cloudnext.server.form.entity.DirectorySyncState;
import com.x.cloudnext.server.form.entity.IdentityBinding;
import com.x.cloudnext.server.form.entity.PrincipalBinding;
import com.x.cloudnext.server.form.entity.UnitBinding;
import com.x.cloudnext.server.form.repository.DirectorySyncStateRepository;
import com.x.cloudnext.server.form.repository.IdentityBindingRepository;
import com.x.cloudnext.server.form.repository.PrincipalBindingRepository;
import com.x.cloudnext.server.form.repository.UnitBindingRepository;
import com.x.cloudnext.server.orgsync.entity.OrgIdentityMirror;
import com.x.cloudnext.server.orgsync.entity.OrgPersonMirror;
import com.x.cloudnext.server.orgsync.repository.OrgIdentityMirrorRepository;
import com.x.cloudnext.server.orgsync.repository.OrgPersonMirrorRepository;
import com.x.cloudnext.server.migration.repository.LegacyArchiveMigrationTaskRepository;

@Service
public class FormDirectoryApplyService {
    public static final String SOURCE_KEY="FORM_DIRECTORY";
    private final PrincipalBindingRepository principals; private final UnitBindingRepository units;
    private final IdentityBindingRepository identities; private final DirectorySyncStateRepository states;
    private final OrgPersonMirrorRepository legacyPeople; private final OrgIdentityMirrorRepository legacyIdentities;
    private final ObjectMapper objectMapper;
    private final LegacyArchiveMigrationTaskRepository archiveTasks;
    private final CanonicalBusinessReferenceService references;
    public FormDirectoryApplyService(PrincipalBindingRepository principals,UnitBindingRepository units,IdentityBindingRepository identities,
            DirectorySyncStateRepository states,OrgPersonMirrorRepository legacyPeople,OrgIdentityMirrorRepository legacyIdentities,ObjectMapper objectMapper,
            LegacyArchiveMigrationTaskRepository archiveTasks,CanonicalBusinessReferenceService references){
        this.principals=principals;this.units=units;this.identities=identities;this.states=states;this.legacyPeople=legacyPeople;this.legacyIdentities=legacyIdentities;this.objectMapper=objectMapper;this.archiveTasks=archiveTasks;this.references=references;
    }

    @Transactional
    public DirectorySyncState apply(FormDirectorySnapshot snapshot,String etag){
        validate(snapshot);
        String tenant=snapshot.getTenantId();
        Map<String,String> roleCodeById=snapshot.getRoles().stream().filter(r->!r.isTombstone()&&StringUtils.hasText(r.getCode())).collect(Collectors.toMap(FormDirectorySnapshot.Role::getId,FormDirectorySnapshot.Role::getCode,(a,b)->a));
        Map<String,List<String>> roleCodesByEmployee=new HashMap<>();for(FormDirectorySnapshot.RoleBinding roleBinding:snapshot.getRoleBindings())if(!roleBinding.isTombstone()&&roleCodeById.containsKey(roleBinding.getRoleId()))roleCodesByEmployee.computeIfAbsent(roleBinding.getEmployeeId(),key->new ArrayList<>()).add(roleCodeById.get(roleBinding.getRoleId()));
        Map<String,Long> employeeNoFrequency=snapshot.getEmployees().stream().filter(e->StringUtils.hasText(e.getEmployeeNo()))
                .collect(Collectors.groupingBy(e->e.getEmployeeNo().trim().toLowerCase(),Collectors.counting()));
        Map<String,PrincipalBinding> principalByEmployee=new HashMap<>();
        boolean legacyBaselineComplete=archiveTasks.countByStatus("VERIFIED")>0;
        for(FormDirectorySnapshot.Employee employee:snapshot.getEmployees()){
            PrincipalBinding binding=principals.findByAuthProviderIgnoreCaseAndTenantIdAndFormEmployeeId("FORM",tenant,employee.getEmployeeId())
                    .orElseGet(()->createPrincipal(tenant,employee,employeeNoFrequency,legacyBaselineComplete));
            reconcilePrincipal(binding,employee,employeeNoFrequency,legacyBaselineComplete);
            binding.setEmployeeNoSnapshot(employee.getEmployeeNo());binding.setDisplayName(employee.getName());binding.setDisplayPath(employee.getDisplayPath());
            boolean sourceEnabled=employee.isAccountEnabled()&&!employee.isDeleted();String sourceStatus=sourceEnabled?"ACTIVE":(employee.isDeleted()?"TOMBSTONE":"DISABLED");
            binding.setSourceAccountEnabled(sourceEnabled);binding.setDirectoryStatus(sourceStatus);binding.setAuthVersion(employee.getAuthVersion());
            if(!CanonicalBindingService.isTerminal(binding.getMigrationDecision())){binding.setAccountEnabled(sourceEnabled);binding.setStatus(sourceStatus);}binding.setLastSeenEtag(etag);
            List<String> roleCodes=new ArrayList<>(employee.getRoleCodes());roleCodes.addAll(roleCodesByEmployee.getOrDefault(employee.getEmployeeId(),new ArrayList<>()));roleCodes=roleCodes.stream().distinct().sorted().collect(Collectors.toList());
            try{binding.setRoleListJson(objectMapper.writeValueAsString(roleCodes));}catch(Exception e){binding.setRoleListJson("[]");}
            principals.save(binding);principalByEmployee.put(employee.getEmployeeId(),binding);
        }
        Map<String,UnitBinding> unitByOrg=new HashMap<>();
        Map<String,FormDirectorySnapshot.Organization> orgById=snapshot.getOrganizations().stream().collect(Collectors.toMap(FormDirectorySnapshot.Organization::getOrgId,Function.identity()));
        Map<String,String> orgPaths=new HashMap<>();
        for(FormDirectorySnapshot.Organization org:snapshot.getOrganizations()){
            UnitBinding binding=units.findByTenantIdAndFormOrgId(tenant,org.getOrgId()).orElseGet(()->createUnit(tenant,org));
            binding.setUnitName(org.getName());binding.setDisplayPath(StringUtils.hasText(org.getDisplayPath())?org.getDisplayPath():orgPath(org,orgById,orgPaths,new HashSet<>()));binding.setDirectoryStatus(org.isDeleted()?"TOMBSTONE":"ACTIVE");if(!CanonicalBindingService.isTerminal(binding.getMigrationDecision()))binding.setStatus(binding.getDirectoryStatus());binding.setLastSeenEtag(etag);
            units.save(binding);unitByOrg.put(org.getOrgId(),binding);
        }
        for(FormDirectorySnapshot.Organization org:snapshot.getOrganizations()){
            UnitBinding binding=unitByOrg.get(org.getOrgId());UnitBinding parent=unitByOrg.get(org.getParentOrgId());
            // The binding was loaded/persisted in this transaction and is managed. A second
            // repository save would merge a not-yet-flushed assigned-id entity as detached.
            binding.setParentCanonicalUnitId(parent==null?null:parent.getCanonicalUnitId());
        }
        for(FormDirectorySnapshot.Assignment assignment:snapshot.getAssignments()){
            PrincipalBinding person=principalByEmployee.get(assignment.getEmployeeId());UnitBinding unit=unitByOrg.get(assignment.getOrgId());
            IdentityBinding binding=identities.findByTenantIdAndFormAssignmentId(tenant,assignment.getAssignmentId())
                    .orElseGet(()->createIdentity(tenant,assignment,person,unit));
            reconcileIdentity(binding,person,unit,legacyBaselineComplete);
            binding.setCanonicalPersonId(person.getCanonicalPersonId());binding.setCanonicalUnitId(unit.getCanonicalUnitId());binding.setPositionId(assignment.getPositionId());
            binding.setIdentityName(StringUtils.hasText(assignment.getName())?assignment.getName():person.getDisplayName());binding.setDisplayPath(StringUtils.hasText(assignment.getDisplayPath())?assignment.getDisplayPath():unit.getDisplayPath()+" / "+person.getDisplayName());
            binding.setPrimaryAssignment(assignment.isPrimaryAssignment());binding.setDirectoryStatus(assignment.isDeleted()||!assignment.isActive()?"TOMBSTONE":"ACTIVE");if(!CanonicalBindingService.isTerminal(binding.getMigrationDecision()))binding.setStatus(binding.getDirectoryStatus());binding.setLastSeenEtag(etag);identities.save(binding);
            if(assignment.isPrimaryAssignment()&&"ACTIVE".equals(binding.getStatus()))person.setDisplayPath(binding.getDisplayPath());
        }
        tombstoneMissing(tenant,etag);
        DirectorySyncState state=states.findById(SOURCE_KEY).orElseGet(()->newState());
        state.setEtag(etag);state.setLastOutcome("SUCCESS");state.setLastSuccessAt(Instant.now().toString());state.setFailureMessage("");
        state.setSnapshotTenantId(tenant);state.setFoundationSot(snapshot.authorityRead());state.setFoundationWriteImpl(snapshot.authorityWrite());
        state.setPersonCount(snapshot.getEmployees().size());state.setUnitCount(snapshot.getOrganizations().size());state.setIdentityCount(snapshot.getAssignments().size());
        return states.save(state);
    }

    public void validate(FormDirectorySnapshot snapshot){
        if(snapshot==null||!StringUtils.hasText(snapshot.getTenantId())) throw new IllegalArgumentException("form snapshot tenantId is required");
        // v2 adds supervisor/leader relationships; the directory identity fields keep the v1 contract.
        if(snapshot.getSchemaVersion()!=null&&snapshot.getSchemaVersion()!=1&&snapshot.getSchemaVersion()!=2)throw new IllegalArgumentException("unsupported form snapshot schemaVersion: "+snapshot.getSchemaVersion());
        if(!"sql".equalsIgnoreCase(snapshot.authorityRead())||!"sql".equalsIgnoreCase(snapshot.authorityWrite())) throw new IllegalArgumentException("form snapshot must be sourced from sql+sql authority");
        unique(snapshot.getEmployees(),FormDirectorySnapshot.Employee::getEmployeeId,"employeeId");
        unique(snapshot.getOrganizations(),FormDirectorySnapshot.Organization::getOrgId,"orgId");
        unique(snapshot.getAssignments(),FormDirectorySnapshot.Assignment::getAssignmentId,"assignmentId");
        Set<String> employees=snapshot.getEmployees().stream().map(FormDirectorySnapshot.Employee::getEmployeeId).collect(Collectors.toSet());
        Set<String> orgs=snapshot.getOrganizations().stream().map(FormDirectorySnapshot.Organization::getOrgId).collect(Collectors.toSet());
        for(FormDirectorySnapshot.Organization org:snapshot.getOrganizations()) if(StringUtils.hasText(org.getParentOrgId())&&!orgs.contains(org.getParentOrgId())) throw new IllegalArgumentException("form snapshot has a missing parent org: "+org.getOrgId());
        assertAcyclic(snapshot.getOrganizations());
        Map<String,Integer> primaryCount=new HashMap<>();
        for(FormDirectorySnapshot.Assignment a:snapshot.getAssignments()){
            if(!employees.contains(a.getEmployeeId())||!orgs.contains(a.getOrgId())) throw new IllegalArgumentException("form assignment references a missing employee or org: "+a.getAssignmentId());
            if(a.isPrimaryAssignment()&&a.isActive()&&!a.isDeleted()) primaryCount.merge(a.getEmployeeId(),1,Integer::sum);
        }
        if(primaryCount.values().stream().anyMatch(v->v>1)) throw new IllegalArgumentException("form snapshot contains multiple active primary assignments for one employee");
    }
    private <T> void unique(List<T> values,Function<T,String> key,String label){Set<String> seen=new HashSet<>();for(T value:values){String id=key.apply(value);if(!StringUtils.hasText(id)||!seen.add(id))throw new IllegalArgumentException("form snapshot contains a missing or duplicate "+label);}}
    private void assertAcyclic(List<FormDirectorySnapshot.Organization> orgs){Map<String,String> parents=orgs.stream().collect(Collectors.toMap(FormDirectorySnapshot.Organization::getOrgId,o->o.getParentOrgId()==null?"":o.getParentOrgId()));for(String start:parents.keySet()){Set<String> path=new HashSet<>();String current=start;while(StringUtils.hasText(current)){if(!path.add(current))throw new IllegalArgumentException("form organization hierarchy contains a cycle at "+current);current=parents.get(current);}}}
    private PrincipalBinding createPrincipal(String tenant,FormDirectorySnapshot.Employee employee,Map<String,Long> frequency,boolean legacyBaselineComplete){
        PrincipalBinding p=new PrincipalBinding();p.setId(UUID.randomUUID().toString());p.setAuthProvider("FORM");p.setTenantId(tenant);p.setFormEmployeeId(employee.getEmployeeId());
        p.setCanonicalPersonId(UUID.randomUUID().toString());p.setMigrationDecision("PENDING");
        return p;
    }
    private UnitBinding createUnit(String tenant,FormDirectorySnapshot.Organization org){UnitBinding u=new UnitBinding();u.setId(UUID.randomUUID().toString());u.setTenantId(tenant);u.setFormOrgId(org.getOrgId());u.setCanonicalUnitId(UUID.randomUUID().toString());u.setMigrationDecision("PENDING");return u;}
    private IdentityBinding createIdentity(String tenant,FormDirectorySnapshot.Assignment a,PrincipalBinding person,UnitBinding unit){
        IdentityBinding i=new IdentityBinding();i.setId(UUID.randomUUID().toString());i.setTenantId(tenant);i.setFormAssignmentId(a.getAssignmentId());i.setCanonicalIdentityId(UUID.randomUUID().toString());i.setMigrationDecision("PENDING");
        return i;
    }
    private void reconcilePrincipal(PrincipalBinding p,FormDirectorySnapshot.Employee employee,Map<String,Long> frequency,boolean baselineComplete){
        if(!"PENDING".equals(p.getMigrationDecision()))return;
        List<OrgPersonMirror> candidates=StringUtils.hasText(employee.getEmployeeNo())?legacyPeople.findByEmployeeNoIgnoreCase(employee.getEmployeeNo().trim()):new ArrayList<>();
        boolean uniqueInForm=StringUtils.hasText(employee.getEmployeeNo())&&frequency.getOrDefault(employee.getEmployeeNo().trim().toLowerCase(),0L)==1L;
        if(uniqueInForm&&candidates.size()==1){OrgPersonMirror candidate=candidates.get(0);boolean available=principals.findByCanonicalPersonId(candidate.getOaId()).map(other->other.getId().equals(p.getId())).orElse(true);if(available&&references.principalReferences(p.getCanonicalPersonId())==0){p.setCanonicalPersonId(candidate.getOaId());p.setLegacyOaPersonId(candidate.getOaId());p.setMigrationDecision("AUTO_MATCH");}else{p.setLegacyOaPersonId(candidate.getOaId());p.setMigrationDecision("PENDING");}return;}
        if(baselineComplete&&candidates.isEmpty()&&"PENDING".equals(p.getMigrationDecision()))p.setMigrationDecision("NEW");else if(!candidates.isEmpty())p.setMigrationDecision("PENDING");
    }
    private void reconcileIdentity(IdentityBinding i,PrincipalBinding person,UnitBinding unit,boolean baselineComplete){
        if(!"PENDING".equals(i.getMigrationDecision()))return;
        if(!CanonicalBindingService.isLoginAllowed(person.getMigrationDecision())||!CanonicalBindingService.isLoginAllowed(unit.getMigrationDecision())){i.setMigrationDecision("PENDING");return;}
        if(StringUtils.hasText(person.getLegacyOaPersonId())&&StringUtils.hasText(unit.getLegacyOaUnitId())){List<OrgIdentityMirror> candidates=legacyIdentities.findByPersonOaIdAndUnitOaId(person.getLegacyOaPersonId(),unit.getLegacyOaUnitId());if(candidates.size()==1){OrgIdentityMirror candidate=candidates.get(0);boolean available=identities.findByCanonicalIdentityId(candidate.getOaId()).stream().filter(other->!other.getId().equals(i.getId())).noneMatch(this::activeFormIdentity);if(available&&references.identityReferences(i.getCanonicalIdentityId())==0){i.setCanonicalIdentityId(candidate.getOaId());i.setLegacyOaIdentityId(candidate.getOaId());i.setMigrationDecision("AUTO_MATCH");}else{i.setLegacyOaIdentityId(candidate.getOaId());i.setMigrationDecision("PENDING");}return;}if(candidates.size()>1){i.setMigrationDecision("PENDING");return;}}
        if(baselineComplete&&"PENDING".equals(i.getMigrationDecision()))i.setMigrationDecision("NEW");
    }
    private void tombstoneMissing(String tenant,String etag){
        for(PrincipalBinding p:principals.findAll())if("FORM".equalsIgnoreCase(p.getAuthProvider())&&tenant.equals(p.getTenantId())&&!etag.equals(p.getLastSeenEtag())){p.setSourceAccountEnabled(false);p.setDirectoryStatus("TOMBSTONE");p.setAccountEnabled(false);if(!CanonicalBindingService.isTerminal(p.getMigrationDecision()))p.setStatus("TOMBSTONE");principals.save(p);}
        for(UnitBinding u:units.findAll())if(tenant.equals(u.getTenantId())&&!etag.equals(u.getLastSeenEtag())){u.setDirectoryStatus("TOMBSTONE");if(!CanonicalBindingService.isTerminal(u.getMigrationDecision()))u.setStatus("TOMBSTONE");units.save(u);}
        for(IdentityBinding i:identities.findAll())if(tenant.equals(i.getTenantId())&&!etag.equals(i.getLastSeenEtag())){i.setDirectoryStatus("TOMBSTONE");if(!CanonicalBindingService.isTerminal(i.getMigrationDecision()))i.setStatus("TOMBSTONE");identities.save(i);}
    }
    private DirectorySyncState newState(){DirectorySyncState s=new DirectorySyncState();s.setSourceKey(SOURCE_KEY);return s;}
    private boolean activeFormIdentity(IdentityBinding i){return !i.getFormAssignmentId().startsWith("LEGACY:")&&"ACTIVE".equals(i.getDirectoryStatus())&&CanonicalBindingService.isLoginAllowed(i.getMigrationDecision());}
    private String orgPath(FormDirectorySnapshot.Organization org,Map<String,FormDirectorySnapshot.Organization> byId,Map<String,String> cache,Set<String> visiting){if(cache.containsKey(org.getOrgId()))return cache.get(org.getOrgId());if(!visiting.add(org.getOrgId()))throw new IllegalArgumentException("form organization hierarchy contains a cycle");String path=org.getName();if(StringUtils.hasText(org.getParentOrgId())){FormDirectorySnapshot.Organization parent=byId.get(org.getParentOrgId());path=orgPath(parent,byId,cache,visiting)+" / "+org.getName();}visiting.remove(org.getOrgId());cache.put(org.getOrgId(),path);return path;}
}
