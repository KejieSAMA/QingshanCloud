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
        if (result.isMustChangePassword()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "form password must be changed before cloudnext login");
        }
        return authSessionService.createSession(result, request.isRememberMe());
    }

    @GetMapping("/sso/start")
    public ResponseEntity<Void> ssoStart(@RequestParam(name="returnPath",required=false) String returnPath) {
        requireFormSso();
        String safe=ssoBootstrapService.normalizeReturnPath(returnPath);
        String location=org.springframework.web.util.UriComponentsBuilder.fromHttpUrl(properties.getAuth().getFormSsoStartUrl())
                .queryParam("cloudnextSso","1").queryParam("returnPath",safe).build().encode().toUriString();
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

    @GetMapping("/sso/callback")
    public ResponseEntity<Void> ssoCallbackGet(@RequestParam(name="ticket",required=false) String ticket,@RequestParam(name="returnPath",required=false) String returnPath) {
        try {
            requireFormSso();
            if(!org.springframework.util.StringUtils.hasText(ticket)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"missing ticket");
            BootstrapResult result=ssoBootstrapService.begin(ticket,returnPath);
            return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.SET_COOKIE,bootstrapCookie(result.getCookieValue()).toString()).location(URI.create(loginCompletionLocation(result.getReturnPath()))).build();
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
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.SET_COOKIE,bootstrapCookie(result.getCookieValue()).toString()).body(new SsoCallbackResponse(result.getReturnPath()));
        } catch(ResponseStatusException failure) { throw FormSsoException.classify(failure); }
    }

    @PostMapping("/sso/complete")
    public ResponseEntity<AuthLoginResponse> ssoComplete(@CookieValue(name=SSO_COOKIE,required=false) String bootstrap) {
        requireFormSso();
        AuthLoginResponse response=ssoBootstrapService.complete(bootstrap);
        ResponseCookie clear=ResponseCookie.from(SSO_COOKIE,"").httpOnly(true).secure(!Boolean.TRUE.equals(properties.getAuth().getAllowInsecureHttp())).sameSite("Lax").path("/api/auth/sso").maxAge(0).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,clear.toString()).body(response);
    }

    private ResponseEntity<Void> ssoErrorRedirect(String code,String returnPath){
        String safe="/";
        try{safe=ssoBootstrapService.normalizeReturnPath(returnPath);}catch(ResponseStatusException ignored){ /* fallback */ }
        String relative=org.springframework.web.util.UriComponentsBuilder.fromPath("/login").queryParam("sso","error")
                .queryParam("code",code).queryParam("returnPath",safe).build().encode().toUriString();
        ResponseCookie clear=ResponseCookie.from(SSO_COOKIE,"").httpOnly(true).secure(!Boolean.TRUE.equals(properties.getAuth().getAllowInsecureHttp())).sameSite("Lax").path("/api/auth/sso").maxAge(0).build();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.SET_COOKIE,clear.toString())
                .header("Cache-Control","no-store").header("Referrer-Policy","no-referrer")
                .location(URI.create(joinBase(properties.getEndpoints().getWebBaseUrl(),relative))).build();
    }

    private ResponseCookie bootstrapCookie(String value){return ResponseCookie.from(SSO_COOKIE,value).httpOnly(true).secure(!Boolean.TRUE.equals(properties.getAuth().getAllowInsecureHttp())).sameSite("Lax").path("/api/auth/sso").maxAge(properties.getAuth().getSsoBootstrapTtlSeconds()).build();}
    private String joinBase(String base,String path){if(!org.springframework.util.StringUtils.hasText(base))return path;return base.replaceAll("/+$","")+path;}
    private String loginCompletionLocation(String returnPath){String relative=org.springframework.web.util.UriComponentsBuilder.fromPath("/login").queryParam("sso","complete").queryParam("returnPath",returnPath).build().encode().toUriString();return joinBase(properties.getEndpoints().getWebBaseUrl(),relative);}
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
