package com.x.cloudnext.server.preview;

import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.drive.*;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.next.*;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;
import com.x.cloudnext.server.link.DrivePublicLinkService;
import com.x.cloudnext.server.namespace.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DriveFilePreviewService {
    @javax.persistence.PersistenceContext private javax.persistence.EntityManager entityManager;
    @org.springframework.beans.factory.annotation.Autowired private com.x.cloudnext.server.audit.AuditLogService audit;
    private final java.util.Map<String,Long> audited=new java.util.LinkedHashMap<>();
    private final DrivePreviewTicketService tickets;
    private final DriveStorageService storage;
    private final DriveItemRepository items;
    private final DriveVersionService versions;
    private final DriveNamespaceService namespaces;
    private final DriveScopeAccessService access;
    private final DrivePublicLinkService links;
    private final PreviewCache cache;
    private final com.x.cloudnext.server.config.CloudNextProperties properties;
    public DriveFilePreviewService(DrivePreviewTicketService tickets,DriveStorageService storage,DriveItemRepository items,
            DriveVersionService versions,DriveNamespaceService namespaces,DriveScopeAccessService access,
            @Lazy DrivePublicLinkService links,PreviewCache cache,com.x.cloudnext.server.config.CloudNextProperties properties) {
        this.tickets=tickets;this.storage=storage;this.items=items;this.versions=versions;this.namespaces=namespaces;
        this.access=access;this.links=links;this.cache=cache;this.properties=properties;
    }
    /** Caller has proven ordinary preview access. The ticket is a version-bound capability, not a cache key. */
    public DrivePreviewResponse describe(DriveItem item,AccessContext context,DriveItemVersion version,String publicAccessTicket) {
        if(!context.isPreviewAllowed())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"当前权限不允许预览。");
        PreviewSource source=source(item,version);
        DrivePreviewTicketPayload p=new DrivePreviewTicketPayload();
        p.setItemId(item.getId());p.setOwnerPersonOaId(item.getOwnerPersonOaId());p.setVersionId(version==null?null:version.id);
        p.setSourceIdentity(source.identity);p.setAllowEdit(false);p.setAllowDownload(context.isDownloadAllowed());
        p.setEffectivePermission(context.getEffectivePermission());p.setScopeType(item.getScopeType().name());p.setScopeId(item.getScopeId());
        if(context.getKind()==AccessContext.Kind.PUBLIC_LINK) {p.setPublicLinkToken(context.getPublicLinkToken());p.setPublicAccessTicket(publicAccessTicket);}
        else {
            AuthLoginResponse viewer=context.getSession();p.setViewerPersonOaIdRaw(viewer.getPersonOaId());p.setViewerName(viewer.getPersonName());
            if(viewer.getIdentityList()!=null)for(var identity:viewer.getIdentityList())if(identity!=null){
                p.getViewerIdentityIds().add(identity.getId()==null?"":identity.getId());p.getViewerUnitIds().add(identity.getUnit()==null?"":identity.getUnit());}
        }
        p.setExpiresAt(Instant.now().plusSeconds(properties.getDrive().getPreviewTicketTtlSeconds()).toEpochMilli());
        String ticket=tickets.create(p);DrivePreviewResponse r=response(source,p,ticket);
        r.setPreviewPageUrl("/preview/file?ticket="+URLEncoder.encode(ticket,StandardCharsets.UTF_8));return r;
    }
    @Transactional(readOnly=true)
    public DrivePreviewResponse inspect(String ticket,boolean generate,boolean retry) {
        Authorized a=authorize(ticket);DrivePreviewResponse r=response(a.source,a.payload,ticket);
        if(a.source.kind.equals("office")) {
            try {apply(r,generate?cache.ensure(a.source,retry):cache.status(a.source));}
            catch(PreviewFailure f) {r.setStatus("FAILED");r.setUnavailableCode(f.code);r.setUnavailableMessage(f.getMessage());r.setRetryable(f.retryable);}
        }
        return r;
    }
    @Transactional(readOnly=true)
    public DriveService.DriveDownloadPayload content(String ticket) {
        Authorized a=authorize(ticket);PreviewSource source=a.source;
        if(source.kind.equals("unsupported"))throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"此格式暂不支持预览。");
        try {
            Path path=source.kind.equals("office")?cache.content(source):source.path;
            String type=source.kind.equals("office")||source.kind.equals("pdf")?"application/pdf":source.kind.equals("text")?"text/plain; charset=utf-8":imageType(source.name);
            String name=source.kind.equals("office")?source.name.substring(0,source.name.lastIndexOf('.'))+".pdf":source.name;
            auditRead(ticket,a);
            return new DriveService.DriveDownloadPayload(name,type,new FileSystemResource(path));
        } catch(PreviewFailure f) {throw new ResponseStatusException(HttpStatus.CONFLICT,f.getMessage());}
    }
    private void auditRead(String ticket,Authorized a) {
        // The public-link descriptor already records its access. Range chunks must not multiply user audit rows.
        if(audit==null || StringUtils.hasText(a.payload.getPublicLinkToken()))return;
        String key=PreviewSource.hash(a.source.driveId+"\n"+ticket);
        synchronized(audited) {
            audited.values().removeIf(expires->expires<=System.currentTimeMillis());
            if(audited.containsKey(key))return;
            if(audited.size()>=1024)audited.remove(audited.keySet().iterator().next());
            audited.put(key,a.payload.getExpiresAt());
        }
        audit.recordAsync(a.payload.getViewerPersonOaIdRaw(),com.x.cloudnext.server.audit.AuditAction.DOWNLOAD,"DRIVE_ITEM",a.source.itemId,
            "mode","preview","name",a.source.name,"contentType",a.source.contentType);
    }
    private Authorized authorize(String ticket) {
        // parse() establishes namespace and refreshes account eligibility/authorization version.
        DrivePreviewTicketPayload p=tickets.parse(ticket);
        if(entityManager!=null)DriveContext.apply(entityManager);
        if(p.isAllowEdit())throw new ResponseStatusException(HttpStatus.GONE,"在线编辑已停用，请重新打开文件预览。");
        DriveItem item=items.findById(p.getItemId()).orElseThrow(()->missing());
        DriveItemVersion version=null;
        if(StringUtils.hasText(p.getPublicLinkToken())) {
            DriveItem permitted=links.requirePreviewItem(p.getPublicLinkToken(),p.getPublicAccessTicket());
            if(!permitted.getId().equals(item.getId()))throw missing();
            if(StringUtils.hasText(p.getVersionId()))throw missing();
        } else {
            if(!StringUtils.hasText(p.getViewerPersonOaIdRaw()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"预览已失效，请重新打开文件。");
            AuthLoginResponse viewer=namespaces.ticketViewer(p);access.requireItem(viewer,item.getId(),DriveScopeAction.READ);
            if(StringUtils.hasText(p.getVersionId()))version=versions.requireVersion(viewer,item.getId(),p.getVersionId());
        }
        PreviewSource source=source(item,version);
        if(StringUtils.hasText(p.getSourceIdentity()) && !p.getSourceIdentity().equals(source.identity))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"文件已更新，请重新打开预览。");
        return new Authorized(source,p);
    }
    private PreviewSource source(DriveItem item,DriveItemVersion version) {
        DriveContext.assertSame(item.getDriveId());
        if(item.isDeleted()||item.getItemType()!=DriveItemType.FILE)throw missing();
        // All ancestors must remain accessible/live, including ticket and public-link reads.
        DriveItem current=item;java.util.HashSet<String> seen=new java.util.HashSet<>();
        while(StringUtils.hasText(current.getParentId())) {
            if(!seen.add(current.getId()))throw missing();current=items.findById(current.getParentId()).orElseThrow(DriveFilePreviewService::missing);
            if(current.isDeleted())throw missing();
        }
        String key=version==null?item.getStorageKey():version.storageKey;
        if(!DriveContext.containsStorageKey(key))throw missing();
        Resource resource=storage.load(key);
        try {
            Path path=resource.getFile().toPath();if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw missing();
            return new PreviewSource(DriveContext.requireId(),item.getId(),version==null?"current":version.id,key,
                version==null?item.getName():version.fileName,version==null?item.getContentType():version.contentType,path,Files.size(path));
        } catch(IOException e) {throw missing();}
    }
    private DrivePreviewResponse response(PreviewSource s,DrivePreviewTicketPayload p,String ticket) {
        DrivePreviewResponse r=new DrivePreviewResponse();r.setItemId(s.itemId);r.setFileName(s.name);r.setKind(s.kind);
        r.setContentType(s.contentType);r.setAllowDownload(p.isAllowDownload());r.setMode("view");r.setSupported(!s.kind.equals("unsupported"));
        r.setPreviewTicket(ticket);r.setExpiresAt(p.getExpiresAt());r.setSourceIdentity(s.identity);
        r.setPreviewUrl("/api/previews/content");r.setStatus(s.kind.equals("office")?"MISSING":"READY");
        if(s.kind.equals("unsupported")){r.setStatus("UNSUPPORTED");r.setUnavailableMessage("此格式暂不支持预览。");}
        if(s.kind.equals("text") && s.size>1024*1024) {r.setSupported(false);r.setStatus("UNSUPPORTED");r.setUnavailableCode("TEXT_TOO_LARGE");r.setUnavailableMessage("文本超过 1 MB，请下载后查看。");}
        return r;
    }
    private static void apply(DrivePreviewResponse r,PreviewCache.State s) {
        r.setStatus(s.status);r.setPhase(s.phase);r.setUnavailableCode(s.code);r.setUnavailableMessage(s.message);r.setRetryable(s.retryable);r.setPageCount(s.pages);
    }
    private static String imageType(String name) {switch(PreviewSource.extension(name)){case "jpg":case "jpeg":return "image/jpeg";case "svg":return "text/plain";default:return "image/"+PreviewSource.extension(name);}}
    private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"文件不存在或已被删除。");}
    private static class Authorized {final PreviewSource source;final DrivePreviewTicketPayload payload;Authorized(PreviewSource s,DrivePreviewTicketPayload p){source=s;payload=p;}}
}
