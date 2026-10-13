package com.x.cloudnext.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.net.URLDecoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import com.x.cloudnext.server.auth.entity.SsoBootstrap;
import com.x.cloudnext.server.auth.repository.SsoBootstrapRepository;
import com.x.cloudnext.server.config.CloudNextProperties;
import com.x.cloudnext.server.form.FormAuthenticationProvider;

@Service
public class SsoBootstrapService {
    private final FormAuthenticationProvider formProvider;private final AuthSessionService sessions;private final SsoBootstrapRepository bootstraps;private final CloudNextProperties properties;
    private final SsoSessionIssuer issuer;
    public SsoBootstrapService(FormAuthenticationProvider formProvider,AuthSessionService sessions,SsoBootstrapRepository bootstraps,CloudNextProperties properties,SsoSessionIssuer issuer){this.formProvider=formProvider;this.sessions=sessions;this.bootstraps=bootstraps;this.properties=properties;this.issuer=issuer;}
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public BootstrapResult begin(String ticket,String returnPath){
        AuthenticationResult principal=formProvider.exchangeTicket(ticket);
        if(!properties.getAuth().getFormSsoAudience().equals(principal.getAudience()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"form sso ticket audience is invalid");
        String authoritativeReturnPath=normalizeReturnPath(principal.getReturnTo());
        if(StringUtils.hasText(returnPath)&&!authoritativeReturnPath.equals(normalizeReturnPath(returnPath)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"form sso ticket return path does not match callback");
        if(principal.isMustChangePassword()) throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,"form password must be changed before cloudnext login");
        return issuer.issue(principal,authoritativeReturnPath);
    }
    @Transactional
    public AuthLoginResponse complete(String raw){
        if(!StringUtils.hasText(raw))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"cloudnext sso bootstrap cookie is missing");
        SsoBootstrap b=bootstraps.findById(sha256(raw)).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"cloudnext sso bootstrap is invalid"));bootstraps.delete(b);
        if(!Instant.parse(b.getExpiresAt()).isAfter(Instant.now())){sessions.logout(b.getSessionToken());throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"cloudnext sso bootstrap has expired");}
        AuthLoginResponse session=sessions.currentSession(b.getSessionToken());if(session==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"cloudnext sso session is invalid");return session;
    }
    public String normalizeReturnPath(String value){
        String original=StringUtils.hasText(value)?value.trim():"/";if(original.length()>1024||!isRelativePath(original))throw invalidReturnPath();String decoded=original;boolean stable=false;
        for(int pass=0;pass<8;pass++){try{String next=URLDecoder.decode(decoded.replace("+","%2B"),StandardCharsets.UTF_8.name());if(!isRelativePath(next))throw invalidReturnPath();if(next.equals(decoded)){stable=true;break;}decoded=next;}catch(ResponseStatusException e){throw e;}catch(Exception e){throw invalidReturnPath();}}
        if(!stable)throw invalidReturnPath();return original;
    }
    private boolean isRelativePath(String value){if(!value.startsWith("/")||value.startsWith("//")||value.indexOf('\\')>=0)return false;for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c<=0x1f||c==0x7f)return false;}return true;}
    private ResponseStatusException invalidReturnPath(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"returnPath must be a valid relative application path");}
    private String sha256(String value){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte item:digest)b.append(String.format("%02x",item));return b.toString();}catch(Exception e){throw new IllegalStateException("SHA-256 is unavailable",e);}}
    public static class BootstrapResult{private final String cookieValue;private final String returnPath;public BootstrapResult(String cookieValue,String returnPath){this.cookieValue=cookieValue;this.returnPath=returnPath;}public String getCookieValue(){return cookieValue;}public String getReturnPath(){return returnPath;}}
}
