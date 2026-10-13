package com.x.cloudnext.server.drive;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.x.cloudnext.server.drive.entity.DriveItem;

@TestPropertySource(properties = {
        "cloudnext.iteration.onlyoffice-edit.enabled=false"
})
class CloudNextOnlyofficeEditDisabledTest extends AbstractDriveIntegrationTest {

    @Test
    void editEndpointsReturn404WhenFlagIsDisabled() throws Exception {
        DriveItem office = seedFile(
                DEFAULT_OWNER_ID,
                "",
                "plan.docx",
                "word",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "office-binary".getBytes());

        mockMvc.perform(get("/api/drive/items/{id}/edit", office.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/onlyoffice/edit-config")
                .param("editTicket", "dummy"))
                .andExpect(status().isNotFound());
    }
}
