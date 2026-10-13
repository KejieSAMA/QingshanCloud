package com.x.cloudnext.server.orgsync;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.x.cloudnext.server.orgsync.entity.OrgGroupMirror;
import com.x.cloudnext.server.orgsync.entity.OrgIdentityMirror;
import com.x.cloudnext.server.orgsync.entity.OrgMembershipMirror;
import com.x.cloudnext.server.orgsync.entity.OrgPersonMirror;
import com.x.cloudnext.server.orgsync.entity.OrgRoleMirror;
import com.x.cloudnext.server.orgsync.entity.OrgUnitMirror;
import com.x.cloudnext.server.orgsync.model.OrgMembershipSourceRecord;
import com.x.cloudnext.server.orgsync.model.OrgSourceRecord;
import com.x.cloudnext.server.orgsync.repository.OrgGroupMirrorRepository;
import com.x.cloudnext.server.orgsync.repository.OrgIdentityMirrorRepository;
import com.x.cloudnext.server.orgsync.repository.OrgMembershipMirrorRepository;
import com.x.cloudnext.server.orgsync.repository.OrgPersonMirrorRepository;
import com.x.cloudnext.server.orgsync.repository.OrgRoleMirrorRepository;
import com.x.cloudnext.server.orgsync.repository.OrgUnitMirrorRepository;
import com.x.cloudnext.server.orgsync.source.O2OrgReadonlySource;

@Service
public class OrgMirrorSyncService {

    private final O2OrgReadonlySource readonlySource;
    private final OrgUnitMirrorRepository unitRepository;
    private final OrgPersonMirrorRepository personRepository;
    private final OrgIdentityMirrorRepository identityRepository;
    private final OrgRoleMirrorRepository roleRepository;
    private final OrgGroupMirrorRepository groupRepository;
    private final OrgMembershipMirrorRepository membershipRepository;

    public OrgMirrorSyncService(
            O2OrgReadonlySource readonlySource,
            OrgUnitMirrorRepository unitRepository,
            OrgPersonMirrorRepository personRepository,
            OrgIdentityMirrorRepository identityRepository,
            OrgRoleMirrorRepository roleRepository,
            OrgGroupMirrorRepository groupRepository,
            OrgMembershipMirrorRepository membershipRepository) {
        this.readonlySource = readonlySource;
        this.unitRepository = unitRepository;
        this.personRepository = personRepository;
        this.identityRepository = identityRepository;
        this.roleRepository = roleRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional
    public OrgMirrorStats syncAll() {
        List<OrgSourceRecord> units = readonlySource.listUnits();
        List<OrgSourceRecord> persons = readonlySource.listPersons();
        List<OrgSourceRecord> identities = readonlySource.listIdentities();
        List<OrgSourceRecord> roles = readonlySource.listRoles();
        List<OrgSourceRecord> groups = readonlySource.listGroups();
        List<OrgMembershipSourceRecord> memberships = readonlySource.listMemberships();

        unitRepository.deleteAllInBatch();
        personRepository.deleteAllInBatch();
        identityRepository.deleteAllInBatch();
        roleRepository.deleteAllInBatch();
        groupRepository.deleteAllInBatch();
        membershipRepository.deleteAllInBatch();

        unitRepository.saveAll(units.stream().map(this::toUnitMirror).collect(Collectors.toList()));
        personRepository.saveAll(persons.stream().map(this::toPersonMirror).collect(Collectors.toList()));
        identityRepository.saveAll(identities.stream().map(this::toIdentityMirror).collect(Collectors.toList()));
        roleRepository.saveAll(roles.stream().map(this::toRoleMirror).collect(Collectors.toList()));
        groupRepository.saveAll(groups.stream().map(this::toGroupMirror).collect(Collectors.toList()));
        membershipRepository.saveAll(memberships.stream().map(this::toMembershipMirror).collect(Collectors.toList()));

        return stats();
    }

    public OrgMirrorStats stats() {
        OrgMirrorStats stats = new OrgMirrorStats();
        stats.setUnits(unitRepository.count());
        stats.setPersons(personRepository.count());
        stats.setIdentities(identityRepository.count());
        stats.setRoles(roleRepository.count());
        stats.setGroups(groupRepository.count());
        stats.setMemberships(membershipRepository.count());
        stats.setCapturedAt(Instant.now().toString());
        return stats;
    }

    private OrgUnitMirror toUnitMirror(OrgSourceRecord source) {
        OrgUnitMirror entity = new OrgUnitMirror();
        fillBase(source, entity);
        entity.setOaId(source.getId());
        entity.setUniqueName(source.getUniqueName());
        entity.setDistinguishedName(source.getDistinguishedName());
        entity.setParentOaId(source.getParentId());
        entity.setLevelValue(source.getLevelValue());
        entity.setLevelName(source.getLevelName());
        return entity;
    }

    private OrgPersonMirror toPersonMirror(OrgSourceRecord source) {
        OrgPersonMirror entity = new OrgPersonMirror();
        fillBase(source, entity);
        entity.setOaId(source.getId());
        entity.setUniqueName(source.getUniqueName());
        entity.setDistinguishedName(source.getDistinguishedName());
        entity.setEmployeeNo(source.getEmployeeNo());
        entity.setMobile(source.getMobile());
        entity.setMail(source.getMail());
        return entity;
    }

    private OrgIdentityMirror toIdentityMirror(OrgSourceRecord source) {
        OrgIdentityMirror entity = new OrgIdentityMirror();
        fillBase(source, entity);
        entity.setOaId(source.getId());
        entity.setUniqueName(source.getUniqueName());
        entity.setDistinguishedName(source.getDistinguishedName());
        entity.setPersonOaId(source.getPersonId());
        entity.setUnitOaId(source.getUnitId());
        entity.setUnitName(source.getUnitName());
        entity.setUnitLevel(source.getUnitLevel());
        entity.setUnitLevelName(source.getUnitLevelName());
        entity.setMajorFlag(source.getMajorFlag());
        return entity;
    }

    private OrgRoleMirror toRoleMirror(OrgSourceRecord source) {
        OrgRoleMirror entity = new OrgRoleMirror();
        fillBase(source, entity);
        entity.setOaId(source.getId());
        entity.setUniqueName(source.getUniqueName());
        entity.setDistinguishedName(source.getDistinguishedName());
        return entity;
    }

    private OrgGroupMirror toGroupMirror(OrgSourceRecord source) {
        OrgGroupMirror entity = new OrgGroupMirror();
        fillBase(source, entity);
        entity.setOaId(source.getId());
        entity.setUniqueName(source.getUniqueName());
        entity.setDistinguishedName(source.getDistinguishedName());
        return entity;
    }

    private OrgMembershipMirror toMembershipMirror(OrgMembershipSourceRecord source) {
        OrgMembershipMirror entity = new OrgMembershipMirror();
        entity.setId(source.getSourceType() + ":" + source.getSourceId() + ":" + source.getTargetType() + ":"
                + source.getTargetId() + ":" + source.getRelationType());
        entity.setSourceType(source.getSourceType());
        entity.setSourceOaId(source.getSourceId());
        entity.setTargetType(source.getTargetType());
        entity.setTargetOaId(source.getTargetId());
        entity.setRelationType(source.getRelationType());
        entity.setSyncVersion(source.getSyncVersion());
        return entity;
    }

    private void fillBase(OrgSourceRecord source, com.x.cloudnext.server.orgsync.entity.BaseMirrorEntity target) {
        target.setId(source.getId());
        target.setName(source.getName());
        target.setStatus(source.getStatus());
        target.setUpdatedAt(source.getUpdatedAt());
        target.setSyncVersion(source.getSyncVersion());
    }
}
