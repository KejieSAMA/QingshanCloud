package com.x.cloudnext.server.drive;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.x.cloudnext.server.drive.entity.DriveItem;

@TestPropertySource(properties = {
        "cloudnext.drive.onlyoffice.document-server-url=http://test-onlyoffice",
        "cloudnext.drive.onlyoffice.docservice-api=http://test-onlyoffice/web-apps/apps/api/documents/api.js",
        "cloudnext.drive.onlyoffice.jwt-enabled=true",
        "cloudnext.drive.onlyoffice.jwt-secret="
})
class CloudNextDriveOnlyofficeJwtRequirementTest extends AbstractDriveIntegrationTest {

    @Test
    void officePreviewReturnsUnavailablePayloadWhenJwtIsEnabledButSecretMissing() throws Exception {
        DriveItem office = seedFile(
                DEFAULT_OWNER_ID,
                "",
                "missing-jwt-secret.xlsx",
                "excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "office".getBytes());

        mockMvc.perform(get("/api/drive/items/{id}/preview", office.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("office"))
                .andExpect(jsonPath("$.supported").value(false))
                .andExpect(jsonPath("$.unavailableCode").value("onlyoffice_not_configured"))
                .andExpect(jsonPath("$.unavailableMessage", containsString("cloudnext.drive.onlyoffice.jwt-secret")));
    }

    @Test
    void configAndStatusExposeMissingJwtSecretWhenJwtIsEnabled() throws Exception {
        mockMvc.perform(get("/api/admin/config")
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onlyofficePreviewConfigured").value(false))
                .andExpect(jsonPath("$.onlyofficeJwtEnabled").value(true))
                .andExpect(jsonPath("$.onlyofficeMissingConfigKeys[0]").value("cloudnext.drive.onlyoffice.jwt-secret"));

        mockMvc.perform(get("/api/status")
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onlyofficePreviewReady").value(false))
                .andExpect(jsonPath("$.warnings[0]", containsString("cloudnext.drive.onlyoffice.jwt-secret")));
    }

    @Test
    void onlyofficeViewConfigReturns503WithReadableMessageWhenJwtSecretIsMissing() throws Exception {
        mockMvc.perform(get("/api/onlyoffice/view-config")
                .param("previewTicket", "dummy-ticket"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", containsString("cloudnext.drive.onlyoffice.jwt-secret")));
    }
}
