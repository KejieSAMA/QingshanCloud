package com.x.cloudnext.server.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.x.cloudnext.server.auth.AdminAccessService;
import com.x.cloudnext.server.migration.LegacyArchiveImportRequest;
import com.x.cloudnext.server.migration.O2LegacyArchiveExportService;
import com.x.cloudnext.server.orgsync.model.OrgMembershipSourceRecord;
import com.x.cloudnext.server.orgsync.model.OrgSourceRecord;

class CloudNextO2ArchiveExportControllerTest {

    @Test
    void exportReturnsExplicitCountsAndNoStoreHeaders() {
        AdminAccessService admins = mock(AdminAccessService.class);
        O2LegacyArchiveExportService exporter = mock(O2LegacyArchiveExportService.class);
        LegacyArchiveImportRequest archive = archive();
        when(exporter.export("oa-final-1")).thenReturn(archive);
        CloudNextO2ArchiveExportController controller = new CloudNextO2ArchiveExportController(admins, exporter);

        ResponseEntity<LegacyArchiveImportRequest> response = controller.export("admin-token", "oa-final-1");

        verify(admins).requireAdmin("admin-token");
        assertEquals("2", response.getHeaders().getFirst("X-CloudNext-Archive-Persons"));
        assertEquals("1", response.getHeaders().getFirst("X-CloudNext-Archive-Memberships"));
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals(archive, response.getBody());
    }

    private LegacyArchiveImportRequest archive() {
        LegacyArchiveImportRequest archive = new LegacyArchiveImportRequest();
        archive.setVersion("oa-final-1");
        archive.setUnits(Arrays.asList(org("unit")));
        archive.setPersons(Arrays.asList(org("person-1"), org("person-2")));
        archive.setIdentities(Arrays.asList(org("identity")));
        archive.setRoles(Arrays.asList(org("role")));
        archive.setGroups(Arrays.asList(org("group")));
        archive.setMemberships(Arrays.asList(new OrgMembershipSourceRecord()));
        return archive;
    }

    private OrgSourceRecord org(String id) {
        OrgSourceRecord record = new OrgSourceRecord();
        record.setId(id);
        return record;
    }
}
