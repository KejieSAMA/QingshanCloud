package com.x.cloudnext.server.drive;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.x.cloudnext.server.auth.*;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.next.DriveVersionService;

/** Short-lived capabilities. No sessions or file credentials are exposed to the OA web document. */
@Service
public class NativeFileTickets {
    private final AuthSessionService sessions;
    private final DriveService drive;
    private final DriveVersionService versions;
    private final DriveZipPackagingService zip;
    private final Map<String, Grant> grants = new LinkedHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public NativeFileTickets(AuthSessionService sessions, DriveService drive, DriveVersionService versions, DriveZipPackagingService zip) {
        this(sessions, drive, versions, zip, Clock.systemUTC());
    }
    NativeFileTickets(AuthSessionService sessions, DriveService drive, DriveVersionService versions, DriveZipPackagingService zip, Clock clock) {
        this.sessions=sessions;this.drive=drive;this.versions=versions;this.zip=zip;this.clock=clock;
    }
    public static class Request {
        public List<String> itemIds;
        public String versionId;
        public String action;
        public String zipName;
        public boolean archive;
    }
    public static final class Grant {
        public final String ticket, fileName, mimeType, action, driveId, fileKey;
        public final long expiresAt, sizeBytes;
        private final String token, fingerprint;
        private final Request request;
        Grant(String id, String token, Request request, String driveId, String name, String type, long size, String fingerprint, long expiry) {
            this.ticket=id;this.token=token;this.request=request;this.driveId=driveId;this.fileName=name;
            this.mimeType=type;this.sizeBytes=size;this.action=request.action;this.fingerprint=fingerprint;this.expiresAt=expiry;
            this.fileKey=id; // Separate cache entry per frozen transfer; never mix namespaces or versions.
        }
        public String sessionToken() { return token; }
        public Request request() { return request; }
    }
    public Grant issue(String token, Request input) {
        if(input==null || input.itemIds==null || input.itemIds.isEmpty() || input.itemIds.size()>1000
            || !Arrays.asList("OPEN","SAVE").contains(input.action)
            || input.itemIds.stream().anyMatch(id->id==null || !id.matches("[A-Za-z0-9._:-]{1,160}"))
            || new HashSet<>(input.itemIds).size()!=input.itemIds.size()
            || (input.versionId!=null && (!input.versionId.matches("[A-Za-z0-9._:-]{1,160}") || input.itemIds.size()!=1 || input.archive)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"文件操作参数不正确");
        AuthLoginResponse session=requiredSession(token);
        Request request=new Request();request.itemIds=List.copyOf(input.itemIds);request.versionId=input.versionId;
        request.action=input.action;request.archive=input.archive || input.itemIds.size()>1;
        request.zipName=safeName(input.zipName==null?"云盘文件.zip":input.zipName);
        String fingerprint=fingerprint(token,request);
        String name,type;long size=0;
        if(request.archive){name=request.zipName.toLowerCase(Locale.ROOT).endsWith(".zip")?request.zipName:request.zipName+".zip";type="application/zip";}
        else if(request.versionId!=null){var v=versions.requireVersion(session,request.itemIds.get(0),request.versionId);name=v.fileName;type=v.contentType;size=v.sizeBytes;}
        else {DriveItem item=drive.requireDownloadItem(token,request.itemIds.get(0));name=item.getName();type=item.getContentType();size=item.getSizeBytes()==null?0:item.getSizeBytes();}
        byte[] bytes=new byte[32];random.nextBytes(bytes);StringBuilder id=new StringBuilder();for(byte b:bytes)id.append(String.format("%02x",b));
        Grant grant=new Grant(id.toString(),token,request,session.getDrive()==null?"default":session.getDrive().getId(),safeName(name),type,size,fingerprint,clock.millis()+300_000);
        synchronized(grants) {
            grants.values().removeIf(g->g.expiresAt<=clock.millis());
            if(grants.size()>=256 || grants.values().stream().filter(g->g.token.equals(token)).count()>=32)
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"文件任务较多，请稍后重试");
            grants.put(grant.ticket,grant);
        }
        return grant;
    }
    public Grant require(String id) {
        Grant grant;
        synchronized(grants) {
            grant=id!=null && id.matches("[a-f0-9]{64}")?grants.get(id):null;
            if(grant==null || grant.expiresAt<=clock.millis()){if(id!=null)grants.remove(id);throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"文件操作已过期，请重新选择");}
        }
        AuthLoginResponse session=requiredSession(grant.token);
        String driveId=session.getDrive()==null?"default":session.getDrive().getId();
        if(!grant.driveId.equals(driveId) || !grant.fingerprint.equals(fingerprint(grant.token,grant.request)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"文件已变化，请重新选择");
        return grant;
    }
    private AuthLoginResponse requiredSession(String token) {
        AuthLoginResponse session=sessions.currentSession(token);
        if(session==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"登录已失效，请返回 OA 重试");
        return session;
    }
    private String fingerprint(String token,Request request) {
        for(String id:request.itemIds)drive.requireDownloadItem(token,id);
        if(request.archive){DriveBatchDownloadRequest batch=new DriveBatchDownloadRequest();batch.setItemIds(request.itemIds);batch.setZipName(request.zipName);return zip.fingerprint(token,batch);}
        if(request.versionId!=null){var v=versions.requireVersion(requiredSession(token),request.itemIds.get(0),request.versionId);return v.id+":"+v.storageKey+":"+v.sha256;}
        DriveItem item=drive.requireDownloadItem(token,request.itemIds.get(0));
        if(item.getItemType()!=DriveItemType.FILE)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择文件");
        return item.getId()+":"+item.getStorageKey()+":"+item.getName()+":"+item.getSizeBytes();
    }
    private static String safeName(String value) { String name=value==null?"云盘文件":value.replaceAll("[\\\\/\\p{Cntrl}]","_");return name.isBlank()?"云盘文件":name.substring(0,Math.min(240,name.length())); }
}
