package com.x.cloudnext.server.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import com.x.cloudnext.server.drive.*;

@RestController
@ConditionalOnProperty(prefix = "cloudnext.iteration.search", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CloudNextUnifiedSearchController {
    private final DriveAccessService access;
    private final UnifiedSearchService search;
    public CloudNextUnifiedSearchController(DriveAccessService access, UnifiedSearchService search) {
        this.access = access; this.search = search;
    }
    @GetMapping("/api/search")
    public UnifiedSearchResponse search(@RequestHeader(name = "X-CloudNext-Token", required = false) String token,
            @ModelAttribute UnifiedSearchRequest request) {
        return search.search(access.requireSession(token), request);
    }
}
