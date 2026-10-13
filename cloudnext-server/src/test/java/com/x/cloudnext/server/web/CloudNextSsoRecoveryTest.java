package com.x.cloudnext.server.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.x.cloudnext.server.auth.*;
import com.x.cloudnext.server.config.CloudNextProperties;
import com.x.cloudnext.server.form.FormSsoException;

class CloudNextSsoRecoveryTest {
    private final SsoBootstrapService bootstrap=mock(SsoBootstrapService.class);
    private CloudNextAuthController controller() {
        CloudNextProperties props=new CloudNextProperties();props.getAuth().setProvider("FORM");
        props.getAuth().setFormBaseUrl("https://oa.test");props.getAuth().setFormServiceToken("test-only");
        props.getAuth().setFormSsoStartUrl("https://oa.test");props.getEndpoints().setWebBaseUrl("https://drive.test");
        return new CloudNextAuthController(props,mock(AuthenticationProviderRouter.class),mock(AuthSessionService.class),bootstrap);
    }
    @Test void browserFailureRedirectsOnlyCodeAndSafePath() {
        when(bootstrap.begin("secret-ticket","/my-files")).thenThrow(FormSsoException.pending());
        when(bootstrap.normalizeReturnPath("/my-files")).thenReturn("/my-files");
        var response=controller().ssoCallbackGet("secret-ticket","/my-files");
        assertEquals(HttpStatus.FOUND,response.getStatusCode());String target=response.getHeaders().getLocation().toString();
        assertTrue(target.startsWith("https://drive.test/login?sso=error&code=DIRECTORY_SYNC_PENDING"));
        assertFalse(target.contains("secret-ticket"));assertTrue(response.getHeaders().getFirst("Set-Cookie").contains("Max-Age=0"));
    }
    @Test void invalidReturnUrlFallsBackWithoutReflectingUpstreamErrors() {
        when(bootstrap.begin(anyString(),anyString())).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED,"secret ticket/phone/stack"));
        when(bootstrap.normalizeReturnPath(anyString())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST));
        String target=controller().ssoCallbackGet("ticket","//evil.test").getHeaders().getLocation().toString();
        assertTrue(target.contains("TICKET_INVALID"));assertFalse(target.contains("evil.test"));assertFalse(target.contains("secret"));
    }
    @Test void postPreservesStatusAndAddsStableErrorCode() {
        when(bootstrap.begin("ticket","/")).thenThrow(FormSsoException.pending());
        SsoCallbackRequest request=new SsoCallbackRequest();request.setTicket("ticket");request.setReturnPath("/");
        FormSsoException failure=assertThrows(FormSsoException.class,()->controller().ssoCallbackPost(request));
        var response=new CloudNextExceptionHandler("100MB").handleResponseStatus(failure);
        assertEquals(HttpStatus.CONFLICT,response.getStatusCode());assertEquals("DIRECTORY_SYNC_PENDING",response.getBody().get("code"));
    }
    @Test void successKeepsBootstrapCookieAndCompletionRedirect() {
        when(bootstrap.begin("ticket","/")).thenReturn(new SsoBootstrapService.BootstrapResult("bootstrap-only","/"));
        var response=controller().ssoCallbackGet("ticket","/");
        assertEquals(HttpStatus.FOUND,response.getStatusCode());assertTrue(response.getHeaders().getLocation().toString().contains("sso=complete"));
        assertTrue(response.getHeaders().getFirst("Set-Cookie").contains("HttpOnly"));
    }
}
