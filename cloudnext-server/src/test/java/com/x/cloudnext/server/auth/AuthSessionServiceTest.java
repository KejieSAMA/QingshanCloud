package com.x.cloudnext.server.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.cloudnext.server.auth.repository.AuthSessionRepository;
import com.x.cloudnext.server.config.CloudNextProperties;
import com.x.cloudnext.server.form.CanonicalBindingService;

class AuthSessionServiceTest {

    @Test
    void usesStandardTtlWhenRememberLoginIsDisabled() {
        CloudNextProperties properties = new CloudNextProperties();
        properties.getAuth().setSessionTtlMinutes(480);

        AuthSessionService service = service(properties);

        assertEquals(480L, service.resolveSessionTtlMinutes(false));
    }

    @Test
    void usesRememberTtlWhenRememberLoginIsEnabled() {
        CloudNextProperties properties = new CloudNextProperties();
        properties.getAuth().setRememberSessionTtlDays(30);

        AuthSessionService service = service(properties);

        assertEquals(43_200L, service.resolveSessionTtlMinutes(true));
    }

    @Test
    void clampsRememberTtlToNinetyDays() {
        CloudNextProperties properties = new CloudNextProperties();
        properties.getAuth().setRememberSessionTtlDays(365);

        assertEquals(90, properties.getAuth().getRememberSessionTtlDays());
    }

    private AuthSessionService service(CloudNextProperties properties) {
        return new AuthSessionService(
                properties,
                mock(AuthSessionRepository.class),
                new ObjectMapper(),
                mock(CanonicalBindingService.class));
    }
}
