package com.x.cloudnext.server.web;

import javax.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.drive.DriveAccessService;
import com.x.cloudnext.server.drive.DrivePageResponse;
import com.x.cloudnext.server.drive.DriveSearchRequest;
import com.x.cloudnext.server.drive.DriveSearchService;

@RestController
@RequestMapping("/api/search")
@ConditionalOnProperty(
        prefix = "cloudnext.iteration.search",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class CloudNextSearchController {

    private final DriveAccessService driveAccessService;
    private final DriveSearchService driveSearchService;

    public CloudNextSearchController(
            DriveAccessService driveAccessService,
            DriveSearchService driveSearchService) {
        this.driveAccessService = driveAccessService;
        this.driveSearchService = driveSearchService;
    }

    @GetMapping("/files")
    public DrivePageResponse searchFiles(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @Valid @ModelAttribute DriveSearchRequest request) {
        AuthLoginResponse session = driveAccessService.requireSession(token);
        return driveSearchService.searchFiles(session, request);
    }
}
