package com.x.cloudnext.server.migration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.x.cloudnext.server.orgsync.model.OrgMembershipSourceRecord;
import com.x.cloudnext.server.orgsync.model.OrgSourceRecord;
import com.x.cloudnext.server.orgsync.source.O2OrgReadonlySource;

/** Builds the immutable migration request directly from the read-only O2 source. */
@Service
public class O2LegacyArchiveExportService {

    private static final Comparator<OrgSourceRecord> ORG_ORDER = Comparator.comparing(
            OrgSourceRecord::getId, Comparator.nullsFirst(String::compareTo));
    private static final Comparator<OrgMembershipSourceRecord> MEMBERSHIP_ORDER = Comparator
            .comparing(OrgMembershipSourceRecord::getSourceType, Comparator.nullsFirst(String::compareTo))
            .thenComparing(OrgMembershipSourceRecord::getSourceId, Comparator.nullsFirst(String::compareTo))
            .thenComparing(OrgMembershipSourceRecord::getTargetType, Comparator.nullsFirst(String::compareTo))
            .thenComparing(OrgMembershipSourceRecord::getTargetId, Comparator.nullsFirst(String::compareTo))
            .thenComparing(OrgMembershipSourceRecord::getRelationType, Comparator.nullsFirst(String::compareTo));

    private final O2OrgReadonlySource source;

    public O2LegacyArchiveExportService(O2OrgReadonlySource source) {
        this.source = source;
    }

    public LegacyArchiveImportRequest export(String version) {
        validateVersion(version);
        LegacyArchiveImportRequest request = new LegacyArchiveImportRequest();
        request.setVersion(version);
        request.setUnits(sortedOrg(source.listUnits(), "units"));
        request.setPersons(sortedOrg(source.listPersons(), "persons"));
        request.setIdentities(sortedOrg(source.listIdentities(), "identities"));
        request.setRoles(sortedOrg(source.listRoles(), "roles"));
        request.setGroups(sortedOrg(source.listGroups(), "groups"));
        request.setMemberships(sortedMemberships(source.listMemberships()));
        return request;
    }

    private List<OrgSourceRecord> sortedOrg(List<OrgSourceRecord> records, String label) {
        if (records == null) {
            throw new IllegalStateException("O2 archive source returned null " + label);
        }
        List<OrgSourceRecord> sorted = new ArrayList<>(records);
        sorted.sort(ORG_ORDER);
        return sorted;
    }

    private List<OrgMembershipSourceRecord> sortedMemberships(List<OrgMembershipSourceRecord> records) {
        if (records == null) {
            throw new IllegalStateException("O2 archive source returned null memberships");
        }
        List<OrgMembershipSourceRecord> sorted = new ArrayList<>(records);
        sorted.sort(MEMBERSHIP_ORDER);
        return sorted;
    }

    private void validateVersion(String version) {
        if (!StringUtils.hasText(version) || version.length() > 128 || !version.matches("[A-Za-z0-9._-]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "migration version must contain only letters, digits, dot, underscore or dash");
        }
    }
}
