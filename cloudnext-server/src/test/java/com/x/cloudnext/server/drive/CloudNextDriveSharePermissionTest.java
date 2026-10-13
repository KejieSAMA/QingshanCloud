package com.x.cloudnext.server.drive;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.x.cloudnext.server.drive.entity.DriveItem;

class CloudNextDriveSharePermissionTest extends AbstractDriveIntegrationTest {

    @Test
    void readShareAllowsPreviewAndContentButBlocksDownload() throws Exception {
        DriveItem file = seedFile(DEFAULT_OWNER_ID, "", "guide.pdf", "pdf", "application/pdf", "guide".getBytes());
        String viewerToken = createSession("person-b", "User B");

        mockMvc.perform(post("/api/drive/shares")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(createPersonShareRequest(file.getId(), "person-b", "User B", "read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSucceeded").value(1));

        mockMvc.perform(get("/api/drive/items/{id}/preview", file.getId())
                .header(TOKEN_HEADER, viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("pdf"))
                .andExpect(jsonPath("$.allowDownload").value(false))
                .andExpect(jsonPath("$.previewUrl", containsString("/api/drive/items/" + file.getId() + "/content")));

        mockMvc.perform(get("/api/drive/items/{id}/content", file.getId())
                .header(TOKEN_HEADER, viewerToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes("guide".getBytes()));

        mockMvc.perform(get("/api/drive/items/{id}/download", file.getId())
                .header(TOKEN_HEADER, viewerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void downloadShareAllowsAttachmentDownloadAndOnlyofficeDownload() throws Exception {
        DriveItem office = seedFile(
                DEFAULT_OWNER_ID,
                "",
                "slides.pptx",
                "ppt",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "office-binary".getBytes());
        String viewerToken = createSession("person-b", "User B");

        mockMvc.perform(post("/api/drive/shares")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(createPersonShareRequest(office.getId(), "person-b", "User B", "download"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSucceeded").value(1));

        String previewJson = mockMvc.perform(get("/api/drive/items/{id}/preview", office.getId())
                .header(TOKEN_HEADER, viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("office"))
                .andExpect(jsonPath("$.allowDownload").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        DrivePreviewResponse preview = objectMapper.readValue(previewJson, DrivePreviewResponse.class);

        mockMvc.perform(get("/api/onlyoffice/view-config")
                .param("previewTicket", preview.getPreviewTicket()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docEditorConfig.document.permissions.download").value(true))
                .andExpect(jsonPath("$.docEditorConfig.document.permissions.print").value(true));

        mockMvc.perform(get("/api/drive/items/{id}/download", office.getId())
                .header(TOKEN_HEADER, viewerToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes("office-binary".getBytes()));
    }

    @Test
    void incomingSharedChildrenUseHighestEffectivePermission() throws Exception {
        DriveItem folder = seedFolder(DEFAULT_OWNER_ID, "", "Projects");
        DriveItem child = seedFile(
                DEFAULT_OWNER_ID,
                folder.getId(),
                "Budget.xlsx",
                "excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "budget".getBytes());
        String viewerToken = createSession("person-b", "User B");

        mockMvc.perform(post("/api/drive/shares")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(createPersonShareRequest(folder.getId(), "person-b", "User B", "read"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/drive/shares")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(createPersonShareRequest(child.getId(), "person-b", "User B", "download"))))
                .andExpect(status().isOk());

        String incomingJson = mockMvc.perform(get("/api/drive/shares/incoming")
                .header(TOKEN_HEADER, viewerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        DriveSharePageResponse page = objectMapper.readValue(incomingJson, DriveSharePageResponse.class);
        String shareId = page.getItems().stream()
                .filter(item -> item.getItem() != null && folder.getId().equals(item.getItem().getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected shared folder root item"))
                .getShareId();

        mockMvc.perform(get("/api/drive/shares/incoming")
                .header(TOKEN_HEADER, viewerToken)
                .param("shareId", shareId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].item.id").value(child.getId()))
                .andExpect(jsonPath("$.items[0].permission").value("read"))
                .andExpect(jsonPath("$.items[0].effectivePermission").value("download"))
                .andExpect(jsonPath("$.items[0].allowPreview").value(true))
                .andExpect(jsonPath("$.items[0].allowDownload").value(true));
    }

    private DriveCreateShareRequest createPersonShareRequest(String itemId, String targetId, String targetName, String permission) {
        DriveCreateShareRequest request = new DriveCreateShareRequest();
        request.setItemId(itemId);
        request.setPermission(permission);

        DriveShareTargetRequest target = new DriveShareTargetRequest();
        target.setType("person");
        target.setId(targetId);
        target.setName(targetName);
        request.setTargets(java.util.Collections.singletonList(target));
        return request;
    }
}
