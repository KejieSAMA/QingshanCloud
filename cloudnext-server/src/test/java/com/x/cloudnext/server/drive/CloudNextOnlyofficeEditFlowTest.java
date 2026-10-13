package com.x.cloudnext.server.drive;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.workspace.entity.Workspace;
import com.x.cloudnext.server.workspace.entity.WorkspaceMember;
import com.x.cloudnext.server.workspace.repository.WorkspaceMemberRepository;
import com.x.cloudnext.server.workspace.repository.WorkspaceRepository;

@TestPropertySource(properties = {
        "cloudnext.iteration.onlyoffice-edit.enabled=true"
})
class CloudNextOnlyofficeEditFlowTest extends AbstractDriveIntegrationTest {

    @Autowired
    private DriveService driveService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Test
    void editConfigAllowsOwnerAndKeepsOnlyofficeEditShape() throws Exception {
        DriveItem office = seedFile(
                DEFAULT_OWNER_ID,
                "",
                "plan.docx",
                "word",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "office-binary".getBytes(StandardCharsets.UTF_8));

        String editJson = mockMvc.perform(get("/api/drive/items/{id}/edit", office.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supported").value(true))
                .andExpect(jsonPath("$.editTicket").isNotEmpty())
                .andExpect(jsonPath("$.editPageUrl", containsString("/edit/office?ticket=")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        DriveOnlyofficeEditTicketResponse edit = objectMapper.readValue(editJson, DriveOnlyofficeEditTicketResponse.class);

        mockMvc.perform(get("/api/onlyoffice/edit-config")
                .param("editTicket", edit.getEditTicket()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("edit"))
                .andExpect(jsonPath("$.docEditorConfig.editorConfig.mode").value("edit"))
                .andExpect(jsonPath("$.docEditorConfig.editorConfig.callbackUrl", containsString("/api/onlyoffice/callback?editTicket=")))
                .andExpect(jsonPath("$.docEditorConfig.document.permissions.edit").value(true))
                .andExpect(jsonPath("$.docEditorConfig.document.key").isNotEmpty());
    }

    @Test
    void nonOfficeAndReadOnlyShareCannotOpenEditor() throws Exception {
        DriveItem pdf = seedFile(DEFAULT_OWNER_ID, "", "guide.pdf", "pdf", MediaType.APPLICATION_PDF_VALUE, "pdf".getBytes());
        mockMvc.perform(get("/api/drive/items/{id}/edit", pdf.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supported").value(false))
                .andExpect(jsonPath("$.unavailableCode").value("onlyoffice_edit_unsupported"));

        DriveItem office = seedFile(
                DEFAULT_OWNER_ID,
                "",
                "shared.docx",
                "word",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "office-binary".getBytes(StandardCharsets.UTF_8));
        String viewerToken = createSession("person-b", "User B");
        mockMvc.perform(post("/api/drive/shares")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(createPersonShareRequest(office.getId(), "person-b", "User B", "read"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/drive/items/{id}/edit", office.getId())
                .header(TOKEN_HEADER, viewerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void editShareCanOpenEditor() throws Exception {
        DriveItem office = seedFile(
                DEFAULT_OWNER_ID,
                "",
                "shared-edit.docx",
                "word",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "office-binary".getBytes(StandardCharsets.UTF_8));
        String viewerToken = createSession("person-b", "User B");
        mockMvc.perform(post("/api/drive/shares")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(createPersonShareRequest(office.getId(), "person-b", "User B", "edit"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/drive/items/{id}/edit", office.getId())
                .header(TOKEN_HEADER, viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supported").value(true));
    }

    @Test
    void workspaceViewerCannotEditButMemberCan() throws Exception {
        Workspace workspace = new Workspace();
        workspace.setId("ws-edit");
        workspace.setName("Edit Workspace");
        workspace.setCreatorPersonOaId(DEFAULT_OWNER_ID);
        workspace.setCreatorName(DEFAULT_OWNER_NAME);
        workspace.setStatus("ACTIVE");

        // Mirror WorkspaceService's real tree: visibility and recent-opened
        // tracking require every workspace item to descend from its root.
        DriveItem root = seedFolder(DEFAULT_OWNER_ID, "", "");
        root.setScopeType(DriveScopeType.WORKSPACE);
        root.setScopeId(workspace.getId());
        syncItem(root);
        workspace.setRootItemId(root.getId());
        workspaceRepository.saveAndFlush(workspace);

        WorkspaceMember viewer = new WorkspaceMember();
        viewer.setId("ws-viewer");
        viewer.setWorkspaceId(workspace.getId());
        viewer.setPersonOaId(DEFAULT_OWNER_ID);
        viewer.setPersonName(DEFAULT_OWNER_NAME);
        viewer.setRole(WorkspaceRole.VIEWER);
        workspaceMemberRepository.saveAndFlush(viewer);

        String memberToken = createSession("person-b", "User B");
        WorkspaceMember member = new WorkspaceMember();
        member.setId("ws-member");
        member.setWorkspaceId(workspace.getId());
        member.setPersonOaId("person-b");
        member.setPersonName("User B");
        member.setRole(WorkspaceRole.MEMBER);
        workspaceMemberRepository.saveAndFlush(member);

        DriveItem office = seedFile(
                DEFAULT_OWNER_ID,
                root.getId(),
                "workspace.docx",
                "word",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "workspace".getBytes(StandardCharsets.UTF_8));
        office.setScopeType(DriveScopeType.WORKSPACE);
        office.setScopeId(workspace.getId());
        syncItem(office);

        mockMvc.perform(get("/api/drive/items/{id}/edit", office.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/drive/items/{id}/edit", office.getId())
                .header(TOKEN_HEADER, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supported").value(true));
    }

    @Test
    void callbackSavesContentAndAcceptsDuplicateNotification() throws Exception {
        DriveItem office = seedFile(
                DEFAULT_OWNER_ID,
                "",
                "plan.docx",
                "word",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "old".getBytes(StandardCharsets.UTF_8));

        String editJson = mockMvc.perform(get("/api/drive/items/{id}/edit", office.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        DriveOnlyofficeEditTicketResponse edit = objectMapper.readValue(editJson, DriveOnlyofficeEditTicketResponse.class);
        JsonNode config = objectMapper.readTree(mockMvc.perform(get("/api/onlyoffice/edit-config")
                .param("editTicket", edit.getEditTicket()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String documentKey = config.at("/docEditorConfig/document/key").asText();
        String savedUrl = "http://onlyoffice.test/saved.docx";

        RestTemplate restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(driveService, "restTemplate", restTemplate);
        when(restTemplate.exchange(eq(savedUrl), eq(HttpMethod.GET), eq(HttpEntity.EMPTY), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok("new-content".getBytes(StandardCharsets.UTF_8)));

        String callback = signedCallback(2, documentKey, savedUrl);
        mockMvc.perform(post("/api/onlyoffice/callback")
                .param("editTicket", edit.getEditTicket())
                .contentType(MediaType.APPLICATION_JSON)
                .content(callback))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(0));

        DriveItem saved = reloadItem(office.getId());
        org.assertj.core.api.Assertions.assertThat(saved.getSizeBytes()).isEqualTo(11L);
        org.assertj.core.api.Assertions.assertThat(storage.get(saved.getStorageKey()))
                .isEqualTo("new-content".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(post("/api/onlyoffice/callback")
                .param("editTicket", edit.getEditTicket())
                .contentType(MediaType.APPLICATION_JSON)
                .content(callback))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(0));
    }

    @Test
    void callbackRejectsStaleDocumentKeyWithoutOverwriting() throws Exception {
        DriveItem office = seedFile(
                DEFAULT_OWNER_ID,
                "",
                "plan.docx",
                "word",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "old".getBytes(StandardCharsets.UTF_8));

        String editJson = mockMvc.perform(get("/api/drive/items/{id}/edit", office.getId())
                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        DriveOnlyofficeEditTicketResponse edit = objectMapper.readValue(editJson, DriveOnlyofficeEditTicketResponse.class);
        String configJson = mockMvc.perform(get("/api/onlyoffice/edit-config")
                .param("editTicket", edit.getEditTicket()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode config = objectMapper.readTree(configJson);
        String staleKey = config.at("/docEditorConfig/document/key").asText();

        office = reloadItem(office.getId());
        office.setSizeBytes(99L);
        syncItem(office);

        String callback = signedCallback(2, staleKey, "http://onlyoffice.invalid/saved.docx");
        mockMvc.perform(post("/api/onlyoffice/callback")
                .param("editTicket", edit.getEditTicket())
                .contentType(MediaType.APPLICATION_JSON)
                .content(callback))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(1));

        DriveItem reloaded = reloadItem(office.getId());
        org.assertj.core.api.Assertions.assertThat(reloaded.getSizeBytes()).isEqualTo(99L);
        org.assertj.core.api.Assertions.assertThat(storage.get(reloaded.getStorageKey())).isEqualTo("old".getBytes(StandardCharsets.UTF_8));
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

    private String signedCallback(int status, String key, String url) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("key", key);
        payload.put("url", url);
        payload.put("token", sign(payload));
        return objectMapper.writeValueAsString(payload);
    }

    private String sign(Map<String, Object> claims) throws Exception {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");
        String headerPart = base64Url(objectMapper.writeValueAsBytes(header));
        String payloadPart = base64Url(objectMapper.writeValueAsBytes(claims));
        String signingInput = headerPart + "." + payloadPart;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = base64Url(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + signature;
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
