package com.x.cloudnext.server.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriUtils;
import com.x.cloudnext.server.drive.*;
import com.x.cloudnext.server.drive.next.DriveVersionService;
import com.x.cloudnext.server.auth.AuthSessionService;

@RestController
@RequestMapping("/api/drive/native-files")
public class CloudNextNativeFilesController {
    private final NativeFileTickets tickets;
    private final DriveService drive;
    private final DriveVersionService versions;
    private final DriveZipPackagingService zip;
    private final AuthSessionService sessions;
    public CloudNextNativeFilesController(NativeFileTickets tickets, DriveService drive, DriveVersionService versions, DriveZipPackagingService zip, AuthSessionService sessions) {
        this.tickets=tickets;this.drive=drive;this.versions=versions;this.zip=zip;this.sessions=sessions;
    }
    @PostMapping("/ticket")
    public NativeFileTickets.Grant issue(@RequestHeader("X-CloudNext-Token") String token,@RequestBody NativeFileTickets.Request request,HttpServletResponse response) {
        privateResponse(response);return tickets.issue(token,request);
    }
    @GetMapping("/metadata")
    public NativeFileTickets.Grant metadata(@RequestHeader("X-CloudNext-File-Ticket") String ticket,HttpServletResponse response) {
        privateResponse(response);return tickets.require(ticket);
    }
    @RequestMapping(value="/content",method={RequestMethod.GET,RequestMethod.POST})
    public void content(@RequestHeader(name="X-CloudNext-File-Ticket",required=false) String header,
            @RequestParam(name="ticket",required=false) String formTicket,HttpServletResponse response) throws IOException {
        privateResponse(response);
        NativeFileTickets.Grant grant=tickets.require(header==null?formTicket:header);
        var request=grant.request();
        if(request.archive){DriveBatchDownloadRequest batch=new DriveBatchDownloadRequest();batch.setItemIds(request.itemIds);batch.setZipName(grant.fileName);zip.streamZip(grant.sessionToken(),batch,response);return;}
        org.springframework.core.io.Resource resource;
        if(request.versionId!=null){var v=versions.requireVersion(sessions.currentSession(grant.sessionToken()),request.itemIds.get(0),request.versionId);resource=versions.content(v);}
        else resource=drive.downloadItem(grant.sessionToken(),request.itemIds.get(0)).getResource();
        org.springframework.http.MediaType type=org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
        try { type=org.springframework.http.MediaType.parseMediaType(grant.mimeType); } catch (RuntimeException ignored) { }
        response.setContentType(type.toString());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+UriUtils.encode(grant.fileName,StandardCharsets.UTF_8));
        if(grant.sizeBytes>0)response.setContentLengthLong(grant.sizeBytes);
        try(var stream=resource.getInputStream()){stream.transferTo(response.getOutputStream());}
    }
    private void privateResponse(HttpServletResponse response){response.setHeader("Cache-Control","no-store");response.setHeader("Referrer-Policy","no-referrer");response.setHeader("X-Content-Type-Options","nosniff");}
}
