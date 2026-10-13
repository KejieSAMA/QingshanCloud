package com.x.cloudnext.server.drive;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.entity.DriveShare;
import com.x.cloudnext.server.drive.repository.DriveShareRepository;
import com.x.cloudnext.server.workspace.entity.Workspace;
import com.x.cloudnext.server.workspace.entity.WorkspaceMember;
import com.x.cloudnext.server.workspace.repository.WorkspaceMemberRepository;
import com.x.cloudnext.server.workspace.repository.WorkspaceRepository;

@TestPropertySource(properties = {
        "cloudnext.iteration.search.enabled=true",
        "cloudnext.iteration.workspaces.enabled=true",
        "cloudnext.iteration.public-links.enabled=false",
        "cloudnext.iteration.governance.enabled=false",
        "cloudnext.iteration.preview-health.enabled=false"
})
class CloudNextSearchFlowTest extends AbstractDriveIntegrationTest {

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    private DriveShareRepository driveShareRepository;

    @Test
    void searchFiles_supportsPersonalWorkspaceAndSharedScopes() throws Exception {
        DriveItem personalMatch = seedFile(
                DEFAULT_OWNER_ID,
                "",
                "Alpha-plan.docx",
                "word",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "alpha".getBytes());
        personalMatch.setUpdatedAt("2026-05-10T10:00:00Z");
        syncItem(personalMatch);

        DriveItem personalOther = seedFile(
                DEFAULT_OWNER_ID,
                "",
                "Beta.txt",
                "txt",
                "text/plain",
                "beta".getBytes());
        personalOther.setUpdatedAt("2026-05-09T10:00:00Z");
        syncItem(personalOther);

        Workspace workspace = new Workspace();
        workspace.setId("ws-1");
        workspace.setName("Team One");
        workspace.setCreatorPersonOaId(DEFAULT_OWNER_ID);
        workspace.setCreatorName(DEFAULT_OWNER_NAME);
        workspace.setStatus("ACTIVE");
        workspaceRepository.saveAndFlush(workspace);

        WorkspaceMember owner = new WorkspaceMember();
        owner.setId("wsm-1");
        owner.setWorkspaceId(workspace.getId());
        owner.setPersonOaId(DEFAULT_OWNER_ID);
        owner.setPersonName(DEFAULT_OWNER_NAME);
        owner.setRole(WorkspaceRole.OWNER);
        workspaceMemberRepository.saveAndFlush(owner);

        DriveItem workspaceItem = new DriveItem();
        workspaceItem.setId("ws-item-1");
        workspaceItem.setParentId("");
        workspaceItem.setOwnerPersonOaId(DEFAULT_OWNER_ID);
        workspaceItem.setOwnerName(DEFAULT_OWNER_NAME);
        workspaceItem.setName("Workspace Notes.txt");
        workspaceItem.setItemType(DriveItemType.FILE);
        workspaceItem.setFileType("txt");
        workspaceItem.setContentType("text/plain");
        workspaceItem.setSizeBytes(12L);
        workspaceItem.setStorageKey("ws-storage");
        workspaceItem.setScopeType(DriveScopeType.WORKSPACE);
        workspaceItem.setScopeId(workspace.getId());
        workspaceItem.setStorageBackend("local");
        workspaceItem.setUpdatedAt("2026-05-11T10:00:00Z");
        driveItemRepository.saveAndFlush(workspaceItem);

        DriveItem sharedItem = seedFile(
                "person-b",
                "",
                "Shared-brief.pdf",
                "pdf",
                "application/pdf",
                "shared".getBytes());
        sharedItem.setUpdatedAt("2026-05-12T10:00:00Z");
        syncItem(sharedItem);

        DriveShare share = new DriveShare();
        share.setId("share-1");
        share.setItemId(sharedItem.getId());
        share.setOwnerPersonOaId("person-b");
        share.setOwnerName("User-person-b");
        share.setTargetType(DriveShareTargetType.PERSON);
        share.setTargetId(DEFAULT_OWNER_ID);
        share.setTargetName(DEFAULT_OWNER_NAME);
        share.setPermission(DriveSharePermission.READ);
        share.setExpiresAt("");
        share.setRevoked(false);
        driveShareRepository.saveAndFlush(share);

        mockMvc.perform(get("/api/search/files")
                .header(TOKEN_HEADER, token)
                .param("scope", "personal")
                .param("keyword", "plan")
                .param("itemType", "file")
                .param("page", "1")
                .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(personalMatch.getId()));

        mockMvc.perform(get("/api/search/files")
                .header(TOKEN_HEADER, token)
                .param("scope", "workspace")
                .param("keyword", "workspace")
                .param("itemType", "file"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(workspaceItem.getId()));

        mockMvc.perform(get("/api/search/files")
                .header(TOKEN_HEADER, token)
                .param("scope", "shared")
                .param("keyword", "shared"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(sharedItem.getId()));
    }

    @Test
    void searchFiles_rejectsInvalidInputAndRespectsGate() throws Exception {
        mockMvc.perform(get("/api/search/files")
                .header(TOKEN_HEADER, token)
                .param("scope", "personal")
                .param("keyword", ""))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/search/files")
                .header(TOKEN_HEADER, token)
                .param("scope", "personal")
                .param("keyword", "x".repeat(101)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/search/files")
                .header(TOKEN_HEADER, token)
                .param("scope", "personal")
                .param("keyword", "plan")
                .param("updatedFrom", "invalid"))
                .andExpect(status().isBadRequest());
    }
}
