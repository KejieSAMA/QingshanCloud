package com.x.cloudnext.server.orgsync.source;

import java.util.List;

import com.x.cloudnext.server.orgsync.model.OrgMembershipSourceRecord;
import com.x.cloudnext.server.orgsync.model.OrgSourceRecord;

public interface O2OrgReadonlySource {

    List<OrgSourceRecord> listUnits();

    List<OrgSourceRecord> listPersons();

    List<OrgSourceRecord> listIdentities();

    List<OrgSourceRecord> listRoles();

    List<OrgSourceRecord> listGroups();

    List<OrgMembershipSourceRecord> listMemberships();
}
