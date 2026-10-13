package com.x.cloudnext.server.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.x.cloudnext.server.auth.AdminAccessService;
import com.x.cloudnext.server.migration.LegacyArchiveImportRequest;
import com.x.cloudnext.server.migration.O2LegacyArchiveExportService;

@RestController
@RequestMapping("/api/admin/form-migration/legacy")
public class CloudNextO2ArchiveExportController {

    private final AdminAccessService admins;
    private final O2LegacyArchiveExportService exporter;

    public CloudNextO2ArchiveExportController(AdminAccessService admins, O2LegacyArchiveExportService exporter) {
        this.admins = admins;
        this.exporter = exporter;
    }

    @GetMapping("/archive-export")
    public ResponseEntity<LegacyArchiveImportRequest> export(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @RequestParam("version") String version) {
        admins.requireAdmin(token);
        LegacyArchiveImportRequest archive = exporter.export(version);
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore());
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("o2-legacy-archive-" + version + ".json")
                .build());
        headers.add("X-CloudNext-Archive-Units", String.valueOf(archive.getUnits().size()));
        headers.add("X-CloudNext-Archive-Persons", String.valueOf(archive.getPersons().size()));
        headers.add("X-CloudNext-Archive-Identities", String.valueOf(archive.getIdentities().size()));
        headers.add("X-CloudNext-Archive-Roles", String.valueOf(archive.getRoles().size()));
        headers.add("X-CloudNext-Archive-Groups", String.valueOf(archive.getGroups().size()));
        headers.add("X-CloudNext-Archive-Memberships", String.valueOf(archive.getMemberships().size()));
        return ResponseEntity.ok().headers(headers).body(archive);
    }
}
