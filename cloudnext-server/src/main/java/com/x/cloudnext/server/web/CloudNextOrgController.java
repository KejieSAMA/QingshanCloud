package com.x.cloudnext.server.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.x.cloudnext.server.drive.DriveAccessService;
import com.x.cloudnext.server.drive.OrgSearchResultResponse;
import com.x.cloudnext.server.drive.OrgSearchService;

@RestController
@RequestMapping("/api/org")
public class CloudNextOrgController {

    private final DriveAccessService driveAccessService;
    private final OrgSearchService orgSearchService;

    public CloudNextOrgController(DriveAccessService driveAccessService, OrgSearchService orgSearchService) {
        this.driveAccessService = driveAccessService;
        this.orgSearchService = orgSearchService;
    }

    @GetMapping("/search")
    public List<OrgSearchResultResponse> search(
            @RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "keyword", required = false) String keyword) {
        driveAccessService.requireSession(token);
        return orgSearchService.search(type, keyword);
    }
}
