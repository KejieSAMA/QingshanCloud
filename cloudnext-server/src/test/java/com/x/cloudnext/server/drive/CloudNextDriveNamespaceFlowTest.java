package com.x.cloudnext.server.drive;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;
import com.x.cloudnext.server.auth.*;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.form.FormIntegrationClient;
import com.x.cloudnext.server.namespace.DriveContext;

/** Runs only via the inherited, guarded qingshancloud_test initializer after explicit Java-test authorization. */
@TestPropertySource(properties = "cloudnext.iteration.public-links.enabled=true")
class CloudNextDriveNamespaceFlowTest extends AbstractDriveIntegrationTest {
    @MockBean private FormIntegrationClient form;
    private AuthLoginResponse login(String entry) {
        ensureActivePrincipal("namespace-person", "Namespace User");
        var binding = principalBindingRepository.findByCanonicalPersonId("namespace-person").orElseThrow();
        binding.setAuthVersion(1L); principalBindingRepository.saveAndFlush(binding);
        when(form.resolveDrive("test-tenant", "namespace-person", entry, 1L)).thenReturn(entry);
        AuthenticationResult principal = new AuthenticationResult(); principal.setAuthProvider("FORM");
        principal.setTenantId("test-tenant"); principal.setFormEmployeeId("namespace-person"); principal.setPersonKey("namespace-person");
        principal.setName("Namespace User"); principal.setAccountEnabled(true); principal.setAuthVersion(1L);
        principal.setDriveEntryId(entry); principal.setDriveProtocol(1);
        return authSessionService.createSession(principal);
    }
    private DriveItem file(AuthLoginResponse session) {
        try {
            authSessionService.currentSession(session.getToken());
            return seedFile("namespace-person", "", "same-name.txt", "text", "text/plain", "isolated".getBytes(StandardCharsets.UTF_8));
        } finally { DriveContext.clear(); }
    }
    @Test void defaultAAndBQueryIdenticalOwnersAndNamesWithoutMixing() throws Exception {
        var defaultSession = login("default"); var a = login("cloud_a"); var b = login("cloud_b");
        var defaultFile = file(defaultSession); var aFile = file(a); var bFile = file(b);
        for (var pair : new Object[][] {{defaultSession, defaultFile}, {a, aFile}, {b, bFile}}) {
            var session = (AuthLoginResponse) pair[0]; var item = (DriveItem) pair[1];
            mockMvc.perform(get("/api/drive/me/items/query").header(TOKEN_HEADER, session.getToken()).header("X-CloudNext-Drive", session.getDrive().getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].id", is(item.getId())));
        }
        mockMvc.perform(get("/api/drive/items/" + aFile.getId() + "/download").header(TOKEN_HEADER, b.getToken()).header("X-CloudNext-Drive", b.getDrive().getId()))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/drive/me/items/query").header(TOKEN_HEADER, a.getToken())).andExpect(status().isForbidden());
    }
    @Test void admissionWithdrawalInvalidatesTheExistingSession() throws Exception {
        var a = login("cloud_a"); file(a);
        when(form.resolveDrive("test-tenant", "namespace-person", "cloud_a", 1L)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "DRIVE_ACCESS_DENIED"));
        mockMvc.perform(get("/api/drive/me/items/query").header(TOKEN_HEADER, a.getToken()).header("X-CloudNext-Drive", a.getDrive().getId()))
            .andExpect(status().isForbidden());
    }
    @Test void exclusivePublicLinkRequiresLoginIntoItsOwnDrive() throws Exception {
        var a = login("cloud_a"); var b = login("cloud_b"); var aFile = file(a);
        String response = mockMvc.perform(post("/api/links").header(TOKEN_HEADER, a.getToken()).header("X-CloudNext-Drive", a.getDrive().getId())
            .contentType(MediaType.APPLICATION_JSON).content("{\"itemId\":\"" + aFile.getId() + "\",\"permission\":\"preview\"}"))
            .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        String publicToken = objectMapper.readTree(response).path("token").asText();
        mockMvc.perform(get("/public/links/" + publicToken + "/meta")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/public/links/" + publicToken + "/meta").header(TOKEN_HEADER, b.getToken()).header("X-CloudNext-Drive", b.getDrive().getId()))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/public/links/" + publicToken + "/meta").header(TOKEN_HEADER, a.getToken()).header("X-CloudNext-Drive", a.getDrive().getId()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.fileName", is("same-name.txt")));
    }
}
