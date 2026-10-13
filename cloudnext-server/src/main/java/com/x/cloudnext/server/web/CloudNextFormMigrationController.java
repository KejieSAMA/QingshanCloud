package com.x.cloudnext.server.web;

import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import com.x.cloudnext.server.auth.AdminAccessService;
import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.form.MappingDecisionRequest;
import com.x.cloudnext.server.form.MigrationManagementService;
import com.x.cloudnext.server.form.MigrationOverviewResponse;
import com.x.cloudnext.server.form.MigrationValidationResponse;
import com.x.cloudnext.server.form.entity.IdentityBinding;
import com.x.cloudnext.server.form.entity.PrincipalBinding;
import com.x.cloudnext.server.form.entity.UnitBinding;
import com.x.cloudnext.server.form.entity.MigrationDecisionAudit;
import com.x.cloudnext.server.orgsync.entity.OrgIdentityMirror;
import com.x.cloudnext.server.orgsync.entity.OrgPersonMirror;
import com.x.cloudnext.server.orgsync.entity.OrgUnitMirror;
import com.x.cloudnext.server.migration.LegacyArchiveImportRequest;
import com.x.cloudnext.server.migration.LegacyArchiveMigrationService;
import com.x.cloudnext.server.migration.CutoverService;
import com.x.cloudnext.server.migration.entity.LegacyArchiveMigrationTask;

@RestController
@RequestMapping("/api/admin/form-migration")
public class CloudNextFormMigrationController {
    private final AdminAccessService admins;private final MigrationManagementService migration;private final LegacyArchiveMigrationService archiveTasks;private final CutoverService cutover;
    public CloudNextFormMigrationController(AdminAccessService admins,MigrationManagementService migration,LegacyArchiveMigrationService archiveTasks,CutoverService cutover){this.admins=admins;this.migration=migration;this.archiveTasks=archiveTasks;this.cutover=cutover;}
    @GetMapping("/overview") public MigrationOverviewResponse overview(@RequestHeader(name="X-CloudNext-Token",required=false)String token){admins.requireAdmin(token);return migration.overview();}
    @GetMapping("/principals") public List<PrincipalBinding> principals(@RequestHeader(name="X-CloudNext-Token",required=false)String token){admins.requireAdmin(token);return migration.principals();}
    @GetMapping("/units") public List<UnitBinding> units(@RequestHeader(name="X-CloudNext-Token",required=false)String token){admins.requireAdmin(token);return migration.units();}
    @GetMapping("/identities") public List<IdentityBinding> identities(@RequestHeader(name="X-CloudNext-Token",required=false)String token){admins.requireAdmin(token);return migration.identities();}
    @GetMapping("/decisions/audit") public List<MigrationDecisionAudit> decisionAudit(@RequestHeader(name="X-CloudNext-Token",required=false)String token){admins.requireAdmin(token);return migration.decisionAudit();}
    @GetMapping("/legacy/principals") public List<OrgPersonMirror> legacyPrincipals(@RequestHeader(name="X-CloudNext-Token",required=false)String token){admins.requireAdmin(token);return migration.legacyPeople();}
    @GetMapping("/legacy/units") public List<OrgUnitMirror> legacyUnits(@RequestHeader(name="X-CloudNext-Token",required=false)String token){admins.requireAdmin(token);return migration.legacyUnits();}
    @GetMapping("/legacy/identities") public List<OrgIdentityMirror> legacyIdentities(@RequestHeader(name="X-CloudNext-Token",required=false)String token){admins.requireAdmin(token);return migration.legacyIdentities();}
    @PutMapping("/principals/{employeeId}/decision") public PrincipalBinding decidePrincipal(@RequestHeader(name="X-CloudNext-Token",required=false)String token,@PathVariable String employeeId,@Valid @RequestBody MappingDecisionRequest request){return migration.decidePrincipal(employeeId,request,actor(admins.requireAdmin(token)));}
    @PutMapping("/units/{orgId}/decision") public UnitBinding decideUnit(@RequestHeader(name="X-CloudNext-Token",required=false)String token,@PathVariable String orgId,@Valid @RequestBody MappingDecisionRequest request){return migration.decideUnit(orgId,request,actor(admins.requireAdmin(token)));}
    @PutMapping("/identities/{assignmentId}/decision") public IdentityBinding decideIdentity(@RequestHeader(name="X-CloudNext-Token",required=false)String token,@PathVariable String assignmentId,@Valid @RequestBody MappingDecisionRequest request){return migration.decideIdentity(assignmentId,request,actor(admins.requireAdmin(token)));}
    @PutMapping("/legacy/units/{oaId}/decision") public UnitBinding decideLegacyUnit(@RequestHeader(name="X-CloudNext-Token",required=false)String token,@PathVariable String oaId,@Valid @RequestBody MappingDecisionRequest request){return migration.decideLegacyUnit(oaId,request,actor(admins.requireAdmin(token)));}
    @PutMapping("/legacy/principals/{oaId}/decision") public PrincipalBinding decideLegacyPrincipal(@RequestHeader(name="X-CloudNext-Token",required=false)String token,@PathVariable String oaId,@Valid @RequestBody MappingDecisionRequest request){return migration.decideLegacyPrincipal(oaId,request,actor(admins.requireAdmin(token)));}
    @PutMapping("/legacy/identities/{oaId}/decision") public IdentityBinding decideLegacyIdentity(@RequestHeader(name="X-CloudNext-Token",required=false)String token,@PathVariable String oaId,@Valid @RequestBody MappingDecisionRequest request){return migration.decideLegacyIdentity(oaId,request,actor(admins.requireAdmin(token)));}
    @GetMapping(value="/units/export.csv",produces="text/csv;charset=UTF-8") public String exportCsv(@RequestHeader(name="X-CloudNext-Token",required=false)String token){admins.requireAdmin(token);return migration.exportUnitCsv();}
    @PostMapping(value="/units/import.csv",consumes={"text/csv",MediaType.TEXT_PLAIN_VALUE}) public Map<String,Integer> importCsv(@RequestHeader(name="X-CloudNext-Token",required=false)String token,@RequestBody String csv){AuthLoginResponse admin=admins.requireAdmin(token);return Map.of("applied",migration.importUnitCsv(csv,actor(admin)));}
    @GetMapping("/validation") public MigrationValidationResponse validation(@RequestHeader(name="X-CloudNext-Token",required=false)String token){admins.requireAdmin(token);return migration.validateMigration();}
    @GetMapping("/tasks") public List<LegacyArchiveMigrationTask> tasks(@RequestHeader(name="X-CloudNext-Token",required=false)String token){admins.requireAdmin(token);return archiveTasks.list();}
    @PostMapping("/tasks/preview") public LegacyArchiveMigrationTask previewTask(@RequestHeader(name="X-CloudNext-Token",required=false)String token,@Valid @RequestBody LegacyArchiveImportRequest request){AuthLoginResponse admin=admins.requireAdmin(token);return archiveTasks.preview(request,actor(admin));}
    @PostMapping("/tasks/{version}/approve") public LegacyArchiveMigrationTask approveTask(@RequestHeader(name="X-CloudNext-Token",required=false)String token,@PathVariable String version){AuthLoginResponse admin=admins.requireAdmin(token);return archiveTasks.approve(version,actor(admin));}
    @PostMapping("/tasks/{version}/apply") public LegacyArchiveMigrationTask applyTask(@RequestHeader(name="X-CloudNext-Token",required=false)String token,@PathVariable String version){AuthLoginResponse admin=admins.requireAdmin(token);return archiveTasks.apply(version,actor(admin));}
    @PostMapping("/tasks/{version}/verify") public LegacyArchiveMigrationTask verifyTask(@RequestHeader(name="X-CloudNext-Token",required=false)String token,@PathVariable String version){AuthLoginResponse admin=admins.requireAdmin(token);return archiveTasks.verify(version,actor(admin));}
    @GetMapping("/cutover/status") public Map<String,Object> cutoverStatus(@RequestHeader(name="X-CloudNext-Token",required=false)String token){admins.requireAdmin(token);return cutover.status();}
    @PostMapping("/cutover/clear-sessions") public Map<String,Long> clearSessions(@RequestHeader(name="X-CloudNext-Token",required=false)String token){admins.requireAdmin(token);return cutover.clearSessions();}
    private String actor(AuthLoginResponse session){return session.getPersonKey()==null?session.getPersonOaId():session.getPersonKey();}
}
