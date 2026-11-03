package com.x.cloudnext.server.web;

import javax.validation.Valid;

import java.net.URI;
import com.x.cloudnext.server.form.FormSsoException;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.x.cloudnext.server.auth.AuthLoginRequest;
import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.auth.AuthSessionService;
import com.x.cloudnext.server.auth.AuthStatusResponse;
import com.x.cloudnext.server.auth.AuthenticationProviderRouter;
import com.x.cloudnext.server.auth.AuthenticationResult;
import com.x.cloudnext.server.auth.SsoBootstrapService;
import com.x.cloudnext.server.auth.SsoBootstrapService.BootstrapResult;
import com.x.cloudnext.server.auth.SsoCallbackRequest;
import com.x.cloudnext.server.auth.SsoCallbackResponse;
import com.x.cloudnext.server.config.CloudNextProperties;

@RestController
@RequestMapping("/api/auth")
public class CloudNextAuthController {

    private final CloudNextProperties properties;
    private static final String SSO_COOKIE="CLOUDNEXT_SSO_BOOTSTRAP";
    private final AuthenticationProviderRouter providerRouter;
    private final AuthSessionService authSessionService;
    private final SsoBootstrapService ssoBootstrapService;

    public CloudNextAuthController(
            CloudNextProperties properties,
            AuthenticationProviderRouter providerRouter,
            AuthSessionService authSessionService,
            SsoBootstrapService ssoBootstrapService) {
        this.properties = properties;
        this.providerRouter = providerRouter;
        this.authSessionService = authSessionService;
        this.ssoBootstrapService = ssoBootstrapService;
    }

    @GetMapping("/status")
    public AuthStatusResponse status() {
        AuthStatusResponse response = new AuthStatusResponse();
        response.setMode(properties.getAuth().getMode());
        response.setEmbeddedOrigins(properties.getAuth().getEmbeddedOrigins());
        response.setProvider(properties.getAuth().getEffectiveProvider());
        response.setConfigured(properties.getAuth().isConfigured());
        response.setSsoEnabled(isFormSsoAvailable());
        response.setFormSsoStartUrl(properties.getAuth().getFormSsoStartUrl());
        response.setO2Configured(properties.getAuth().isO2Configured());
        response.setLoginPath(properties.getAuth().getLoginPath());
        response.setRsaPublicKeyPath(properties.getAuth().getRsaPublicKeyPath());
        response.setSessionTtlMinutes(properties.getAuth().getSessionTtlMinutes());
        response.setRememberSessionTtlDays(properties.getAuth().getRememberSessionTtlDays());
        return response;
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthLoginResponse login(@Valid @RequestBody AuthLoginRequest request) {
        AuthenticationResult result = providerRouter.verifyPassword(request.getCredential(), request.getPassword());
        result.setDriveEntryId(request.getEntryId());
        result.setDriveProtocol(1);
        if (result.isMustChangePassword()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "form password must be changed before cloudnext login");
        }
        return authSessionService.createSession(result, request.isRememberMe());
    }

    @GetMapping("/sso/start")
    public ResponseEntity<Void> ssoStart(@RequestParam(name="returnPath",required=false) String returnPath,
            @RequestParam(name="entryId",required=false) String entryId) {
        requireFormSso();
        String safe=ssoBootstrapService.normalizeReturnPath(returnPath);
        String location=org.springframework.web.util.UriComponentsBuilder.fromHttpUrl(properties.getAuth().getFormSsoStartUrl())
                .queryParam("cloudnextSso","1").queryParam("returnPath",safe).queryParam("cloudnextEntryId", entryId == null ? "default" : entryId).build().encode().toUriString();
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

    @GetMapping("/sso/callback")
    public ResponseEntity<Void> ssoCallbackGet(@RequestParam(name="ticket",required=false) String ticket,@RequestParam(name="returnPath",required=false) String returnPath) {
        try {
            requireFormSso();
            if(!org.springframework.util.StringUtils.hasText(ticket)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"missing ticket");
            BootstrapResult result=ssoBootstrapService.begin(ticket,returnPath);
            return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.SET_COOKIE,bootstrapCookie(result).toString()).location(URI.create(loginCompletionLocation(result))).build();
        } catch(ResponseStatusException failure) {
            return ssoErrorRedirect(FormSsoException.classify(failure).getCode(),returnPath);
        } catch(RuntimeException failure) {
            // Browser redirects must not contain upstream bodies, tickets, employee details or stack traces.
            return ssoErrorRedirect("SSO_UNAVAILABLE",returnPath);
        }
    }

    @PostMapping("/sso/callback")
    public ResponseEntity<SsoCallbackResponse> ssoCallbackPost(@Valid @RequestBody SsoCallbackRequest request) {
        try {
        requireFormSso();
        BootstrapResult result=ssoBootstrapService.begin(request.getTicket(),request.getReturnPath());
        SsoCallbackResponse response = new SsoCallbackResponse(result.getReturnPath());
        response.setState(result.getState()); response.setDrive(result.getDrive());
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.SET_COOKIE,bootstrapCookie(result).toString()).body(response);
        } catch(ResponseStatusException failure) { throw FormSsoException.classify(failure); }
    }

    /** An embedded client exchanges the same single-use Form ticket, without a third-party cookie. */
    @PostMapping("/sso/embedded")
    public ResponseEntity<AuthLoginResponse> ssoEmbedded(@Valid @RequestBody SsoCallbackRequest request) {
        requireFormSso();
        try {
            BootstrapResult result = ssoBootstrapService.begin(request.getTicket(), request.getReturnPath());
            return ResponseEntity.ok().header("Cache-Control", "no-store")
                    .header("Referrer-Policy", "no-referrer")
                    .body(ssoBootstrapService.complete(result.getCookieValue()));
        } catch (ResponseStatusException failure) { throw FormSsoException.classify(failure); }
    }

    @PostMapping("/sso/complete")
    public ResponseEntity<AuthLoginResponse> ssoComplete(@RequestParam(name="state",required=false) String state, javax.servlet.http.HttpServletRequest request) {
        requireFormSso();
        String cookieName = state == null ? SSO_COOKIE : SSO_COOKIE + "_" + validState(state);
        String bootstrap = null;
        if (request.getCookies() != null) for (javax.servlet.http.Cookie cookie : request.getCookies()) if (cookieName.equals(cookie.getName())) bootstrap = cookie.getValue();
        AuthLoginResponse response=ssoBootstrapService.complete(bootstrap);
        if (state == null && (response.getDrive() == null || !response.getDrive().isDefault())) {
            authSessionService.logout(response.getToken());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "legacy SSO completion only supports the default drive");
        }
        ResponseCookie clear=ResponseCookie.from(cookieName,"").httpOnly(true).secure(!Boolean.TRUE.equals(properties.getAuth().getAllowInsecureHttp())).sameSite("Lax").path("/api/auth/sso").maxAge(0).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,clear.toString()).body(response);
    }

    private ResponseEntity<Void> ssoErrorRedirect(String code,String returnPath){
        String safe="/";
        try{safe=ssoBootstrapService.normalizeReturnPath(returnPath);}catch(ResponseStatusException ignored){ /* fallback */ }
        var builder=org.springframework.web.util.UriComponentsBuilder.fromPath("/login").queryParam("sso","error")
                .queryParam("code",code).queryParam("returnPath",safe);
        String entry = org.springframework.web.util.UriComponentsBuilder.fromUriString(safe).build().getQueryParams().getFirst("entryId");
        if (entry != null && entry.matches("[a-zA-Z0-9_-]{1,80}")) builder.queryParam("entryId", entry);
        String relative=builder.build().encode().toUriString();
        ResponseCookie clear=ResponseCookie.from(SSO_COOKIE,"").httpOnly(true).secure(!Boolean.TRUE.equals(properties.getAuth().getAllowInsecureHttp())).sameSite("Lax").path("/api/auth/sso").maxAge(0).build();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.SET_COOKIE,clear.toString())
                .header("Cache-Control","no-store").header("Referrer-Policy","no-referrer")
                .location(URI.create(joinBase(properties.getEndpoints().getWebBaseUrl(),relative))).build();
    }

    private String validState(String state) {
        if (!state.matches("[a-f0-9]{32}")) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid SSO state");
        return state;
    }
    private ResponseCookie bootstrapCookie(BootstrapResult result){return ResponseCookie.from(SSO_COOKIE + "_" + result.getState(),result.getCookieValue()).httpOnly(true).secure(!Boolean.TRUE.equals(properties.getAuth().getAllowInsecureHttp())).sameSite("Lax").path("/api/auth/sso").maxAge(properties.getAuth().getSsoBootstrapTtlSeconds()).build();}
    private String joinBase(String base,String path){if(!org.springframework.util.StringUtils.hasText(base))return path;return base.replaceAll("/+$","")+path;}
    private String loginCompletionLocation(BootstrapResult result){
        var builder = org.springframework.web.util.UriComponentsBuilder.fromPath("/login").queryParam("sso","complete").queryParam("state",result.getState()).queryParam("returnPath",result.getReturnPath());
        if (result.getDrive() != null) builder.queryParam("driveId",result.getDrive().getId()).queryParam("entryId",result.getDrive().getEntryId());
        return joinBase(properties.getEndpoints().getWebBaseUrl(),builder.build().encode().toUriString());
    }
    private boolean isFormSsoAvailable(){return "FORM".equalsIgnoreCase(properties.getAuth().getEffectiveProvider())&&properties.getAuth().isConfigured()&&Boolean.TRUE.equals(properties.getAuth().getFormSsoEnabled())&&org.springframework.util.StringUtils.hasText(properties.getAuth().getFormSsoStartUrl());}
    private void requireFormSso(){if(!isFormSsoAvailable())throw new org.springframework.web.server.ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"form sso is not configured for the active authentication provider");}

    @GetMapping("/me")
    public AuthLoginResponse me(@RequestHeader(name = "X-CloudNext-Token", required = false) String token) {
        AuthLoginResponse response = authSessionService.currentSession(token);
        if (response == null) {
            throw new AuthSessionNotFoundException();
        }
        return response;
    }

    @DeleteMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(name = "X-CloudNext-Token", required = false) String token) {
        authSessionService.logout(token);
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    private static class AuthSessionNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
