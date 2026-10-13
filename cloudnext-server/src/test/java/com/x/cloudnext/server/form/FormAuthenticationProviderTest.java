package com.x.cloudnext.server.form;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import com.x.cloudnext.server.auth.AuthenticationResult;
import com.x.cloudnext.server.config.CloudNextProperties;

class FormAuthenticationProviderTest {
    private final FormIntegrationClient client=mock(FormIntegrationClient.class);
    private final CanonicalBindingService bindings=mock(CanonicalBindingService.class);
    private final FormLoginDirectorySync sync=mock(FormLoginDirectorySync.class);
    private final FormAuthenticationProvider provider=new FormAuthenticationProvider(client,bindings,sync,new CloudNextProperties());
    private AuthenticationResult principal(String decision) {
        AuthenticationResult principal=new AuthenticationResult();
        principal.setTenantId("tenant");principal.setFormEmployeeId("employee");
        principal.setAccountEnabled(true);principal.setAudience("cloudnext");principal.setMappingDecision(decision);
        when(client.exchangeTicket("one-time-ticket")).thenReturn(principal);
        when(bindings.bindAuthentication(principal)).thenReturn(principal);return principal;
    }
    @Test void exchangeOnceThenSynchronizeExactIdentityAndBind() {
        AuthenticationResult principal=principal("NEW");
        assertSame(principal,provider.exchangeTicket("one-time-ticket"));
        var order=inOrder(client,sync,bindings);
        order.verify(client).exchangeTicket("one-time-ticket");
        order.verify(sync).ensureBinding("tenant","employee");
        order.verify(bindings).bindAuthentication(principal);
        verify(client,times(1)).exchangeTicket(anyString());
    }
    @Test void invalidAudienceCannotStartSynchronization() {
        principal("NEW").setAudience("another-app");
        assertEquals("TICKET_INVALID",assertThrows(FormSsoException.class,()->provider.exchangeTicket("one-time-ticket")).getCode());
        verifyNoInteractions(sync);verify(bindings,never()).bindAuthentication(any());
    }
    @Test void disabledAccountCannotStartSynchronization() {
        principal("NEW").setAccountEnabled(false);
        assertEquals("ACCOUNT_DISABLED",assertThrows(FormSsoException.class,()->provider.exchangeTicket("one-time-ticket")).getCode());
        verifyNoInteractions(sync);
    }
    @Test void pendingAndArchivedIdentitiesRemainBlocked() {
        for(String decision:new String[]{"PENDING","ARCHIVE","REVOKED","TRANSFERRED","TOMBSTONE"}) {
            principal(decision);
            assertEquals("IDENTITY_MAPPING_PENDING",assertThrows(FormSsoException.class,()->provider.exchangeTicket("one-time-ticket")).getCode());
        }
    }
}
