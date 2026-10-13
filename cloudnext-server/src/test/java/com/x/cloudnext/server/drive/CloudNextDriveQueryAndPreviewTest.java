package com.x.cloudnext.server.drive;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.x.cloudnext.server.drive.entity.DriveItem;

class CloudNextDriveQueryAndPreviewTest extends AbstractDriveIntegrationTest {

    @Test
    void nameSortUsesNaturalOrderBeforePagingAndPreferencePersists() throws Exception {
        for (int index = 12; index >= 1; index--) {
            seedFile(DEFAULT_OWNER_ID, "", String.valueOf(index), "file", MediaType.TEXT_PLAIN_VALUE, new byte[] { 1 });
        }

        mockMvc.perform(get("/api/drive/me/items/query")
                .header(TOKEN_HEADER, token)
                .param("page", "1")
                .param("size", "20")
                .param("sortBy", "name")
                .param("sortOrder", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", contains(
                        "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")));

        mockMvc.perform(put("/api/drive/preferences/sort")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"context\":\"my-files\",\"sortBy\":\"name\",\"sortOrder\":\"asc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortBy").value("name"))
                .andExpect(jsonPath("$.sortOrder").value("asc"));

        mockMvc.perform(get("/api/drive/preferences/sort")
                .header(TOKEN_HEADER, token)
                .param("context", "my-files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortBy").value("name"))
                .andExpect(jsonPath("$.sortOrder").value("asc"));
    }

    @Test
    void queryMyItems_returnsFolderFirstAndSupportsKeywordTypeAndTimeFilters() throws Exception {
        DriveItem folder = seedFolder(DEFAULT_OWNER_ID, "", "Projects");
        folder.setUpdatedAt("2026-05-09T09:00:00Z");
        syncItem(folder);

        DriveItem alpha = seedFile(
                DEFAULT_OWNER_ID,
                "",
                "Alpha-plan.docx",
                "word",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "alpha".getBytes());
        alpha.setUpdatedAt("2026-05-10T12:00:00Z");
        syncItem(alpha);

        DriveItem beta = seedFile(DEFAULT_OWNER_ID, "", "Beta.pdf", "pdf", "application/pdf", "beta".getBytes());
        beta.setUpdatedAt("2026-05-08T08:00:00Z");
        syncItem(beta);

        mockMvc.perform(get("/api/drive/me/items/query")
                .header(TOKEN_HEADER, token)
                .param("parentId", "")
                .param("page", "1")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[0].id").value(folder.getId()))
                .andExpect(jsonPath("$.items[0].itemType").value("folder"));

        mockMvc.perform(get("/api/drive/me/items/query")
                .header(TOKEN_HEADER, token)
                .param("parentId", "")
                .param("keyword", "plan")
                .param("itemType", "file")
                .param("timeFrom", "2026-05-10T00:00:00Z")
                .param("timeTo", "2026-05-10T23:59:59Z")
                .param("sortBy", "updatedAt")
                .param("sortOrder", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(alpha.getId()))
                .andExpect(jsonPath("$.items[0].name").value("Alpha-plan.docx"));
    }

    @Test
    void queryMyItems_supportsPagination() throws Exception {
        for (int i = 1; i <= 21; i++) {
            seedFile(
                    DEFAULT_OWNER_ID,
                    "",
                    String.format("Document-%02d.txt", i),
                    "file",
                    MediaType.TEXT_PLAIN_VALUE,
                    ("row-" + i).getBytes());
        }

        mockMvc.perform(get("/api/drive/me/items/query")
                .header(TOKEN_HEADER, token)
                .param("parentId", "")
                .param("page", "2")
                .param("size", "20")
                .param("sortBy", "name")
                .param("sortOrder", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(21))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void queryMyItems_returnsParentNameForPagedChildren() throws Exception {
        DriveItem parent = seedFolder(DEFAULT_OWNER_ID, "", "Invoices");
        DriveItem child = seedFile(
                DEFAULT_OWNER_ID,
                parent.getId(),
                "2026-05.pdf",
                "pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "invoice".getBytes());
        child.setUpdatedAt("2026-05-11T10:00:00Z");
        syncItem(child);

        mockMvc.perform(get("/api/drive/me/items/query")
                .header(TOKEN_HEADER, token)
                .param("parentId", parent.getId())
                .param("page", "1")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(child.getId()))
                .andExpect(jsonPath("$.items[0].parentId").value(parent.getId()))
                .andExpect(jsonPath("$.items[0].parentName").value("Invoices"));
    }

    @Test
    void getPath_supportsRootHierarchyAndAccessControl() throws Exception {
        DriveItem level1 = seedFolder(DEFAULT_OWNER_ID, "", "Root A");
        DriveItem level2 = seedFolder(DEFAULT_OWNER_ID, level1.getId(), "Root B");
        DriveItem outsider = seedFolder("person-b", "", "Other");

        mockMvc.perform(get("/api/drive/me/path")
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        mockMvc.perform(get("/api/drive/me/path")
                .header(TOKEN_HEADER, token)
                .param("itemId", level2.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(level1.getId()))
                .andExpect(jsonPath("$[1].id").value(level2.getId()));

        mockMvc.perform(get("/api/drive/me/path")
                .header(TOKEN_HEADER, token)
                .param("itemId", outsider.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void recentOpened_updatesAfterPreviewAndDownload() throws Exception {
        DriveItem image = seedFile(DEFAULT_OWNER_ID, "", "photo.png", "file", "image/png", "image".getBytes());
        DriveItem pdf = seedFile(DEFAULT_OWNER_ID, "", "manual.pdf", "pdf", "application/pdf", "pdf".getBytes());

        mockMvc.perform(get("/api/drive/me/recent-opened")
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        mockMvc.perform(get("/api/drive/items/{id}/preview", image.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("image"));

        mockMvc.perform(get("/api/drive/items/{id}/download", pdf.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(content().bytes("pdf".getBytes()));

        mockMvc.perform(get("/api/drive/me/recent-opened")
                .header(TOKEN_HEADER, token)
                .param("limit", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(pdf.getId()))
                .andExpect(jsonPath("$[1].id").value(image.getId()));
    }

    @Test
    void previewAndOnlyofficeEndpoints_coverKindsAndTicketValidation() throws Exception {
        DriveItem office = seedFile(
                DEFAULT_OWNER_ID,
                "",
                "slides.pptx",
                "ppt",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "office-binary".getBytes());
        DriveItem unsupported = seedFile(
                DEFAULT_OWNER_ID,
                "",
                "archive.zip",
                "file",
                "application/zip",
                "zip".getBytes());
        DriveItem pdf = seedFile(DEFAULT_OWNER_ID, "", "guide.pdf", "pdf", "application/pdf", "guide".getBytes());

        String previewJson = mockMvc.perform(get("/api/drive/items/{id}/preview", office.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("office"))
                .andExpect(jsonPath("$.supported").value(true))
                .andExpect(jsonPath("$.previewTicket").isNotEmpty())
                .andExpect(jsonPath("$.previewPageUrl", containsString("/preview/office?ticket=")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        DrivePreviewResponse preview = objectMapper.readValue(previewJson, DrivePreviewResponse.class);

        mockMvc.perform(get("/api/drive/items/{id}/preview", unsupported.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("unsupported"))
                .andExpect(jsonPath("$.supported").value(false));

        mockMvc.perform(get("/api/drive/items/{id}/preview", pdf.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("pdf"))
                .andExpect(jsonPath("$.supported").value(true));

        mockMvc.perform(get("/api/onlyoffice/view-config")
                .param("previewTicket", preview.getPreviewTicket()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("slides.pptx"))
                .andExpect(jsonPath("$.docserviceApi").value("http://test-onlyoffice/web-apps/apps/api/documents/api.js"))
                .andExpect(jsonPath("$.docEditorConfig.document.fileType").value("pptx"))
                .andExpect(jsonPath("$.docEditorConfig.document.url", containsString("/api/onlyoffice/content?previewTicket=")))
                .andExpect(jsonPath("$.docEditorConfig.editorConfig.mode").value("view"))
                .andExpect(jsonPath("$.docEditorConfig.token").isNotEmpty());

        mockMvc.perform(get("/api/onlyoffice/view-config")
                .param("previewTicket", preview.getPreviewTicket() + "tampered"))
                .andExpect(status().isUnauthorized());

        DrivePreviewTicketPayload expired = new DrivePreviewTicketPayload();
        expired.setItemId(office.getId());
        expired.setOwnerPersonOaId(DEFAULT_OWNER_ID);
        expired.setViewerPersonOaId("uid=" + DEFAULT_OWNER_ID + ",ou=people,dc=cloudnext");
        expired.setViewerName(DEFAULT_OWNER_NAME);
        expired.setExpiresAt(Instant.now().minusSeconds(5).toEpochMilli());
        String expiredTicket = drivePreviewTicketService.create(expired);

        mockMvc.perform(get("/api/onlyoffice/view-config")
                .param("previewTicket", expiredTicket))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/onlyoffice/content")
                .param("previewTicket", preview.getPreviewTicket()))
                .andExpect(status().isOk())
                .andExpect(content().bytes("office-binary".getBytes()));

        mockMvc.perform(get("/api/drive/me/recent-opened")
                .header(TOKEN_HEADER, token)
                .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(office.getId()));

        mockMvc.perform(get("/api/onlyoffice/content")
                .param("previewTicket", preview.getPreviewTicket() + "tampered"))
                .andExpect(status().isUnauthorized());
    }
}
