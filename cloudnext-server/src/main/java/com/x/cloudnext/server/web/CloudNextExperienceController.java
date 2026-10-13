package com.x.cloudnext.server.web;
import java.util.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import org.springframework.web.server.ResponseStatusException;
import com.x.cloudnext.server.drive.*;
import com.x.cloudnext.server.drive.next.*;
import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.link.*;

@RestController
@RequestMapping("/api")
public class CloudNextExperienceController {
 private final DriveExperienceService experience;
 private final DriveVersionService versions;
 private final DriveAccessService auth;
 private final DrivePublicLinkService links;
 private final DriveService drive;
 private final DrivePreviewTicketService tickets;
 public CloudNextExperienceController(DriveExperienceService experience,DriveVersionService versions,DriveAccessService auth,DrivePublicLinkService links,DriveService drive,DrivePreviewTicketService tickets){
  this.experience=experience;this.versions=versions;this.auth=auth;this.links=links;this.drive=drive;this.tickets=tickets;
 }
 private AuthLoginResponse session(String token){return auth.requireSession(token);}
 @GetMapping("/workspaces/{id}/trash/items/query")
 public Object trash(@RequestHeader(name="X-CloudNext-Token",required=false)String t,@PathVariable String id,@RequestParam Map<String,String> q){return experience.workspaceTrash(session(t),id,q);}
 @GetMapping("/drive/me/browse")
 public Object browse(@RequestHeader(name="X-CloudNext-Token",required=false)String t,@RequestParam Map<String,String> q){return experience.browse(session(t),q);}
 @GetMapping("/drive/preferences/ui")
 public Object prefs(@RequestHeader(name="X-CloudNext-Token",required=false)String t){return experience.prefs(session(t));}
 @PutMapping("/drive/preferences/ui")
 public Object prefs(@RequestHeader(name="X-CloudNext-Token",required=false)String t,@RequestBody Map<String,Object> p){return experience.savePrefs(session(t),p);}
 @GetMapping("/drive/me/home")
 public Object home(@RequestHeader(name="X-CloudNext-Token",required=false)String t,@RequestParam Map<String,String> q){return experience.home(session(t),q);}
 @GetMapping("/drive/me/recent-opened/query")
 public Object recent(@RequestHeader(name="X-CloudNext-Token",required=false)String t,@RequestParam Map<String,String> q){return experience.query(session(t),"recent",q);}
 @PostMapping("/drive/me/recent-opened/{id}")
 public void opened(@RequestHeader(name="X-CloudNext-Token",required=false)String t,@PathVariable String id){experience.mark(session(t),id,false,true);}
 @GetMapping("/drive/me/favorites")
 public Object favorites(@RequestHeader(name="X-CloudNext-Token",required=false)String t,@RequestParam Map<String,String> q){return experience.query(session(t),"favorites",q);}
 @PutMapping("/drive/me/favorites/{id}")
 public void favorite(@RequestHeader(name="X-CloudNext-Token",required=false)String t,@PathVariable String id){experience.mark(session(t),id,true,false);}
 @DeleteMapping("/drive/me/favorites/{id}")
 public void unfavorite(@RequestHeader(name="X-CloudNext-Token",required=false)String t,@PathVariable String id){experience.mark(session(t),id,false,false);}
 @GetMapping("/drive/me/storage-usage")
 public Object usage(@RequestHeader(name="X-CloudNext-Token",required=false)String t){return experience.usage(session(t));}
 @PostMapping("/drive/items/presentation")
 public Object presentation(@RequestHeader(name="X-CloudNext-Token",required=false)String t,@RequestBody List<String> ids){return experience.presentation(session(t),ids);}
 @GetMapping("/drive/items/{id}/details")
 public Object details(@RequestHeader(name="X-CloudNext-Token",required=false)String t,@PathVariable String id){return experience.details(session(t),id);}
 @GetMapping("/workspaces/{id}/activities")
 public Object activities(@RequestHeader(name="X-CloudNext-Token",required=false)String t,@PathVariable String id){return experience.activities(session(t),id);}
 @GetMapping("/drive/me/transfers")
 public Object transfers(@RequestHeader(name="X-CloudNext-Token",required=false)String t){return experience.transfers(session(t));}
 @GetMapping("/drive/me/links")
 public Object links(@RequestHeader(name="X-CloudNext-Token",required=false)String t){return links.listMineIncludingInactive(session(t));}
 @DeleteMapping("/links/{id}/record")
 public void hide(@RequestHeader(name="X-CloudNext-Token",required=false)String t,@PathVariable String id){links.hideInactiveRecord(session(t),id);}
 @GetMapping("/drive/items/{id}/versions")
 public Object versions(@RequestHeader(name="X-CloudNext-Token",required=false)String t,@PathVariable String id){return versions.list(session(t),id);}
 @GetMapping("/drive/items/{id}/versions/{versionId}/preview")
 public Object versionPreview(@RequestHeader(name="X-CloudNext-Token",required=false)String t,@PathVariable String id,@PathVariable String versionId){
  DriveItemVersion version=versions.requireVersion(session(t),id,versionId);
  DrivePreviewResponse response=drive.getPreview(t,id);
  response.setFileName(version.fileName);response.setContentType(version.contentType);response.setAllowDownload(true);
  if("office".equals(response.getKind())&&Boolean.TRUE.equals(response.getSupported())){
   if("current".equals(version.id))return response;
   DrivePreviewTicketPayload p=tickets.parse(response.getPreviewTicket());p.setVersionId(version.id);p.setAllowEdit(false);p.setAllowDownload(true);
   String ticket=tickets.create(p);response.setPreviewTicket(ticket);
  }else response.setPreviewUrl("/api/drive/items/"+id+"/versions/"+versionId+"/content");
  return response;
 }
 @GetMapping("/drive/items/{id}/versions/{versionId}/{operation:content|download}")
 public ResponseEntity<Resource> versionContent(@RequestHeader(name="X-CloudNext-Token",required=false)String t,@PathVariable String id,@PathVariable String versionId,@PathVariable String operation){
  DriveItemVersion v=versions.requireVersion(session(t),id,versionId);
  String disposition="download".equals(operation)?"attachment":"inline";
  MediaType type=MediaType.APPLICATION_OCTET_STREAM;try{type=MediaType.parseMediaType(v.contentType);}catch(Exception ignored){}
  return ResponseEntity.ok().contentType(type).header("X-Content-Type-Options","nosniff")
    .header("Content-Security-Policy", "sandbox; default-src 'none'; img-src data:; style-src 'unsafe-inline'")
    .header("Content-Disposition",disposition+"; filename*=UTF-8''"+URLEncoder.encode(v.fileName,StandardCharsets.UTF_8).replace("+","%20")).body(versions.content(v));
 }
}
