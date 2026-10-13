package com.x.cloudnext.server.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.x.cloudnext.server.orgsync.model.OrgMembershipSourceRecord;
import com.x.cloudnext.server.orgsync.model.OrgSourceRecord;
import com.x.cloudnext.server.orgsync.source.O2OrgReadonlySource;

class O2LegacyArchiveExportServiceTest {

    @Test
    void exportSortsEveryArchiveSectionDeterministically() {
        O2LegacyArchiveExportService service = new O2LegacyArchiveExportService(new StubSource());

        LegacyArchiveImportRequest archive = service.export("oa-final-20260720");

        assertEquals("oa-final-20260720", archive.getVersion());
        assertEquals(Arrays.asList("a", "b"), ids(archive.getUnits()));
        assertEquals(Arrays.asList("a", "b"), ids(archive.getPersons()));
        assertEquals(Arrays.asList("a", "b"), ids(archive.getIdentities()));
        assertEquals(Arrays.asList("a", "b"), ids(archive.getRoles()));
        assertEquals(Arrays.asList("a", "b"), ids(archive.getGroups()));
        assertEquals("a", archive.getMemberships().get(0).getSourceId());
        assertEquals("b", archive.getMemberships().get(1).getSourceId());
    }

    @Test
    void exportRejectsAReusableUnsafeVersionShapeBeforeReadingO2() {
        O2LegacyArchiveExportService service = new O2LegacyArchiveExportService(new StubSource());

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.export("bad version/with spaces"));

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatus());
    }

    private List<String> ids(List<OrgSourceRecord> records) {
        List<String> ids = new ArrayList<>();
        for (OrgSourceRecord record : records) {
            ids.add(record.getId());
        }
        return ids;
    }

    private static class StubSource implements O2OrgReadonlySource {
        @Override public List<OrgSourceRecord> listUnits() { return records(); }
        @Override public List<OrgSourceRecord> listPersons() { return records(); }
        @Override public List<OrgSourceRecord> listIdentities() { return records(); }
        @Override public List<OrgSourceRecord> listRoles() { return records(); }
        @Override public List<OrgSourceRecord> listGroups() { return records(); }
        @Override public List<OrgMembershipSourceRecord> listMemberships() {
            return new ArrayList<>(Arrays.asList(membership("b"), membership("a")));
        }
        private List<OrgSourceRecord> records() {
            return new ArrayList<>(Arrays.asList(record("b"), record("a")));
        }
        private OrgSourceRecord record(String id) {
            OrgSourceRecord record = new OrgSourceRecord();record.setId(id);return record;
        }
        private OrgMembershipSourceRecord membership(String id) {
            OrgMembershipSourceRecord record = new OrgMembershipSourceRecord();
            record.setSourceType("role");record.setSourceId(id);record.setTargetType("person");
            record.setTargetId(id);record.setRelationType("member");return record;
        }
    }
}
