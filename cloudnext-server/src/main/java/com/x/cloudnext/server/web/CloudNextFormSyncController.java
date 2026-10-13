package com.x.cloudnext.server.web;

import org.springframework.web.bind.annotation.*;
import com.x.cloudnext.server.auth.AdminAccessService;
import com.x.cloudnext.server.form.FormDirectorySyncService;
import com.x.cloudnext.server.form.entity.DirectorySyncState;

@RestController
@RequestMapping("/api/admin/form-sync")
public class CloudNextFormSyncController {
    private final AdminAccessService admins;private final FormDirectorySyncService sync;
    public CloudNextFormSyncController(AdminAccessService admins,FormDirectorySyncService sync){this.admins=admins;this.sync=sync;}
    @GetMapping("/status") public DirectorySyncState status(@RequestHeader(name="X-CloudNext-Token",required=false)String token){admins.requireAdmin(token);return sync.current();}
    @PostMapping("/trigger") public DirectorySyncState trigger(@RequestHeader(name="X-CloudNext-Token",required=false)String token,@RequestParam(name="force",defaultValue="true")boolean force){admins.requireAdmin(token);return force?sync.syncForce():sync.sync();}
}
