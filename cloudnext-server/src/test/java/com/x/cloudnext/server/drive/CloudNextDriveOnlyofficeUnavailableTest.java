package com.x.cloudnext.server.drive;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.x.cloudnext.server.drive.entity.DriveItem;

@TestPropertySource(properties = {
        "cloudnext.drive.onlyoffice.document-server-url=",
        "cloudnext.drive.onlyoffice.docservice-api=",
        "cloudnext.drive.onlyoffice.jwt-enabled=false",
        "cloudnext.drive.onlyoffice.jwt-secret="
})
class CloudNextDriveOnlyofficeUnavailableTest extends AbstractDriveIntegrationTest {

    @Test
    void officePreviewReturnsFriendlyUnavailablePayloadWhenDocServerConfigIsMissing() throws Exception {
        DriveItem office = seedFile(
                DEFAULT_OWNER_ID,
                "",
                "missing-config.docx",
                "word",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "office".getBytes());

        mockMvc.perform(get("/api/drive/items/{id}/preview", office.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("office"))
                .andExpect(jsonPath("$.supported").value(false))
                .andExpect(jsonPath("$.unavailableCode").value("onlyoffice_not_configured"))
                .andExpect(jsonPath("$.unavailableMessage", containsString("cloudnext.drive.onlyoffice.document-server-url")))
                .andExpect(jsonPath("$.unavailableMessage", containsString("cloudnext.drive.onlyoffice.docservice-api")));
    }

    @Test
    void configAndStatusExposeOnlyofficeReadinessAndMissingKeys() throws Exception {
        mockMvc.perform(get("/api/admin/config")
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onlyofficePreviewConfigured").value(false))
                .andExpect(jsonPath("$.onlyofficeJwtEnabled").value(false))
                .andExpect(jsonPath("$.onlyofficeMissingConfigKeys[0]").value("cloudnext.drive.onlyoffice.document-server-url"))
                .andExpect(jsonPath("$.onlyofficeMissingConfigKeys[1]").value("cloudnext.drive.onlyoffice.docservice-api"));

        mockMvc.perform(get("/api/status")
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onlyofficePreviewReady").value(false))
                .andExpect(jsonPath("$.warnings[0]", containsString("cloudnext.drive.onlyoffice.document-server-url")));
    }

    @Test
    void onlyofficeViewConfigReturns503WithReadableMessageWhenMissingDocServerConfig() throws Exception {
        mockMvc.perform(get("/api/onlyoffice/view-config")
                .param("previewTicket", "dummy-ticket"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", containsString("cloudnext.drive.onlyoffice.document-server-url")))
                .andExpect(jsonPath("$.message", containsString("cloudnext.drive.onlyoffice.docservice-api")));
    }
}
