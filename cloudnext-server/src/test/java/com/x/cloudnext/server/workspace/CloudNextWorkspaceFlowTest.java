package com.x.cloudnext.server.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.cloudnext.server.CloudNextServerApplication;
import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.auth.AuthSessionService;
import com.x.cloudnext.server.auth.O2AuthenticationResult;
import com.x.cloudnext.server.auth.repository.AuthSessionRepository;
import com.x.cloudnext.server.drive.DriveScopeType;
import com.x.cloudnext.server.drive.DriveStorageService;
import com.x.cloudnext.server.drive.DriveStoredFile;
import com.x.cloudnext.server.drive.WorkspaceRole;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;
import com.x.cloudnext.server.form.entity.PrincipalBinding;
import com.x.cloudnext.server.form.repository.PrincipalBindingRepository;
import com.x.cloudnext.server.workspace.entity.Workspace;
import com.x.cloudnext.server.workspace.entity.WorkspaceMember;
import com.x.cloudnext.server.workspace.repository.WorkspaceMemberRepository;
import com.x.cloudnext.server.workspace.repository.WorkspaceRepository;

/**
 * Integration coverage for the workspace HTTP surface (task 1.14).
 *
 * <p>Stands alone rather than extending {@code AbstractDriveIntegrationTest}
 * because it lives in the {@code workspace} test subpackage and needs direct
 * access to {@link WorkspaceRepository} / {@link WorkspaceMemberRepository}.
 * The {@code DriveStorageService} is replaced with an in-memory map, mirroring
 * the pattern in {@code AbstractDriveIntegrationTest.configureStorageMock}.
 */
@SpringBootTest(
        classes = CloudNextServerApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:cloudnext-workspace-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.task.scheduling.enabled=false"
        })
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "cloudnext.iteration.workspaces.enabled=true",
        "cloudnext.iteration.public-links.enabled=true",
        "cloudnext.iteration.search.enabled=false",
        "cloudnext.iteration.governance.enabled=false",
        "cloudnext.iteration.preview-health.enabled=false"
})
class CloudNextWorkspaceFlowTest {

    private static final String TOKEN_HEADER = "X-CloudNext-Token";
    private static final String OWNER_ID = "person-a";
    private static final String OWNER_NAME = "User A";
    private static final String OUTSIDER_ID = "person-b";
    private static final String OUTSIDER_NAME = "User B";
    private static final String SECOND_OWNER_ID = "person-c";
    private static final String SECOND_OWNER_NAME = "User C";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthSessionService authSessionService;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private PrincipalBindingRepository principalBindingRepository;

    @Autowired
    private DriveItemRepository driveItemRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    @MockBean
    private DriveStorageService driveStorageService;

    @PersistenceContext
    private EntityManager entityManager;

    private final Map<String, byte[]> storage = new ConcurrentHashMap<>();

    private String ownerToken;

    @BeforeEach
    void resetState() throws Exception {
        driveItemRepository.deleteAll();
        workspaceMemberRepository.deleteAll();
        workspaceRepository.deleteAll();
        authSessionRepository.deleteAll();
        principalBindingRepository.deleteAll();
        entityManager.clear();
        storage.clear();
        configureStorageMock();
        ownerToken = createSession(OWNER_ID, OWNER_NAME);
        ensureActivePrincipal(OUTSIDER_ID, OUTSIDER_NAME);
        ensureActivePrincipal(SECOND_OWNER_ID, SECOND_OWNER_NAME);
    }

    @Test
    void workspaceCrudHappyPath() throws Exception {
        // POST /api/workspaces
        String createJson = mockMvc.perform(post("/api/workspaces")
                .header(TOKEN_HEADER, ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Team Alpha\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.rootItemId").exists())
                .andExpect(jsonPath("$.memberCount").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createNode = objectMapper.readTree(createJson);
        String workspaceId = createNode.get("id").asText();
        String rootItemId = createNode.get("rootItemId").asText();
        assertTrue(StringUtils.hasText(rootItemId), "rootItemId must be populated");

        mockMvc.perform(get("/api/drive/me/recent")
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + rootItemId + "')]").isEmpty());

        mockMvc.perform(get("/api/drive/me/items")
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + rootItemId + "')]").isEmpty());

        mockMvc.perform(get("/api/drive/me/items/query")
                .header(TOKEN_HEADER, ownerToken)
                .param("parentId", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='" + rootItemId + "')]").isEmpty());

        mockMvc.perform(get("/api/drive/trash/items")
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + rootItemId + "')]").isEmpty());

        mockMvc.perform(delete("/api/drive/items/{id}", rootItemId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/drive/shares")
                .header(TOKEN_HEADER, ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemIds\":[\"" + rootItemId
                        + "\"],\"targets\":[{\"type\":\"person\",\"id\":\"" + SECOND_OWNER_ID
                        + "\",\"name\":\"" + SECOND_OWNER_NAME + "\"}],\"permission\":\"read\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFailed").value(1));

        mockMvc.perform(post("/api/links")
                .header(TOKEN_HEADER, ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":\"" + rootItemId + "\",\"permission\":\"preview\"}"))
                .andExpect(status().isBadRequest());

        entityManager.clear();
        DriveItem rootAfterDeleteAttempt = driveItemRepository.findById(rootItemId).orElseThrow();
        assertTrue(!rootAfterDeleteAttempt.isDeleted(), "workspace root must not be soft deleted");

        // GET /api/workspaces — list contains the new workspace
        mockMvc.perform(get("/api/workspaces")
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + workspaceId + "')].name").value("Team Alpha"));

        // GET /api/workspaces/{id} — myRole=OWNER
        mockMvc.perform(get("/api/workspaces/{id}", workspaceId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.rootItemId").value(rootItemId));

        // POST /api/workspaces/{id}/folders — child of rootItemId
        String folderJson = mockMvc.perform(post("/api/workspaces/{id}/folders", workspaceId)
                .header(TOKEN_HEADER, ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"docs\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(rootItemId))
                .andExpect(jsonPath("$.name").value("docs"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String folderId = objectMapper.readTree(folderJson).get("id").asText();
        assertNotNull(folderId);

        // POST /api/workspaces/{id}/files — multipart upload to root
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "notes.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "hello workspace".getBytes(StandardCharsets.UTF_8));
        String fileJson = mockMvc.perform(multipart("/api/workspaces/{id}/files", workspaceId)
                .file(upload)
                .header(TOKEN_HEADER, ownerToken)
                .param("parentId", rootItemId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("notes.txt"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String fileId = objectMapper.readTree(fileJson).get("id").asText();
        assertNotNull(fileId);

        mockMvc.perform(get("/api/drive/items/{id}/download", fileId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes("hello workspace".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/drive/me/recent-opened")
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + fileId + "')]").isEmpty());

        // GET /api/workspaces/{id}/items — folder + file present
        mockMvc.perform(get("/api/workspaces/{id}/items", workspaceId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + folderId + "')].name").value("docs"))
                .andExpect(jsonPath("$[?(@.id=='" + fileId + "')].name").value("notes.txt"));

        // DELETE /api/drive/items/{fileId} — scope-aware delete (task 0.5)
        mockMvc.perform(delete("/api/drive/items/{id}", fileId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        // GET /api/workspaces/{id}/items — file no longer present
        mockMvc.perform(get("/api/workspaces/{id}/items", workspaceId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + fileId + "')]").isEmpty());

        // Direct repository assertion: deleted DriveItem retains workspace scope.
        entityManager.clear();
        DriveItem reloadedFile = driveItemRepository.findById(fileId).orElseThrow();
        assertTrue(reloadedFile.isDeleted(), "deleted flag must be set");
        assertEquals(DriveScopeType.WORKSPACE, reloadedFile.getScopeType(), "scopeType stays WORKSPACE after delete");
        assertEquals(workspaceId, reloadedFile.getScopeId(), "scopeId stays the workspace id after delete");

        // Property 3 — workspace trash isolation:
        // workspace-trash query returns the deleted file…
        List<DriveItem> workspaceTrash = driveItemRepository
                .findByScopeTypeAndScopeIdAndDeletedTrue(DriveScopeType.WORKSPACE, workspaceId);
        assertTrue(
                workspaceTrash.stream().anyMatch(item -> fileId.equals(item.getId())),
                "workspace trash query must include the deleted workspace file");

        // …while the personal-trash query (filtered to PERSONAL scope) does not.
        List<DriveItem> personalTrashScoped = driveItemRepository
                .findByScopeTypeAndScopeIdAndDeletedTrue(DriveScopeType.PERSONAL, OWNER_ID);
        assertTrue(
                personalTrashScoped.stream().noneMatch(item -> fileId.equals(item.getId())),
                "personal-scope trash must not include workspace items");
    }

    @Test
    void workspacesDefaultToCreatedTimeDescAndRememberSortPreferencePerUser() throws Exception {
        String memberToken = createSession(SECOND_OWNER_ID, SECOND_OWNER_NAME);
        String zetaId = createWorkspace(ownerToken, "Zeta Space");
        String alphaId = createWorkspace(ownerToken, "Alpha Space");
        String betaId = createWorkspace(ownerToken, "Beta Space");
        addMember(ownerToken, zetaId, SECOND_OWNER_ID, SECOND_OWNER_NAME, "MEMBER");
        addMember(ownerToken, alphaId, SECOND_OWNER_ID, SECOND_OWNER_NAME, "MEMBER");
        addMember(ownerToken, betaId, SECOND_OWNER_ID, SECOND_OWNER_NAME, "MEMBER");
        setWorkspaceCreatedAt(zetaId, "2026-01-01T00:00:00Z");
        setWorkspaceCreatedAt(alphaId, "2026-01-02T00:00:00Z");
        setWorkspaceCreatedAt(betaId, "2026-01-03T00:00:00Z");
        workspaceRepository.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/workspaces")
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(betaId))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[1].id").value(alphaId))
                .andExpect(jsonPath("$[2].id").value(zetaId));

        mockMvc.perform(get("/api/workspaces/sort-preference")
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortMode").value("TIME"));

        mockMvc.perform(put("/api/workspaces/sort-preference")
                .header(TOKEN_HEADER, ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sortMode\":\"NAME\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortMode").value("NAME"));

        mockMvc.perform(get("/api/workspaces/sort-preference")
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortMode").value("NAME"));

        mockMvc.perform(get("/api/workspaces")
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(alphaId))
                .andExpect(jsonPath("$[1].id").value(betaId))
                .andExpect(jsonPath("$[2].id").value(zetaId));

        mockMvc.perform(get("/api/workspaces/sort-preference")
                .header(TOKEN_HEADER, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortMode").value("TIME"));

        mockMvc.perform(put("/api/workspaces/sort-preference")
                .header(TOKEN_HEADER, ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sortMode\":\"UPDATED\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/workspaces/sort-preference")
                .header(TOKEN_HEADER, ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/workspaces/{id}", betaId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/workspaces")
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + betaId + "')]").isEmpty());
    }

    private void setWorkspaceCreatedAt(String workspaceId, String createdAt) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow();
        workspace.setCreatedAt(createdAt);
        workspaceRepository.save(workspace);
    }

    @Test
    void nonMemberAccessDenied() throws Exception {
        String workspaceId = createWorkspace(ownerToken, "Closed Space");
        String outsiderToken = createSession(OUTSIDER_ID, OUTSIDER_NAME);

        mockMvc.perform(get("/api/workspaces/{id}", workspaceId)
                .header(TOKEN_HEADER, outsiderToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/workspaces/{id}/items", workspaceId)
                .header(TOKEN_HEADER, outsiderToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/workspaces/{id}/folders", workspaceId)
                .header(TOKEN_HEADER, outsiderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"intrusion\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/workspaces/{id}/members", workspaceId)
                .header(TOKEN_HEADER, outsiderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void workspaceRenameAndArchivePermissions() throws Exception {
        String workspaceId = createWorkspace(ownerToken, "Rename Me");
        String adminToken = createSession(OUTSIDER_ID, OUTSIDER_NAME);
        String memberToken = createSession(SECOND_OWNER_ID, SECOND_OWNER_NAME);
        String viewerId = "person-d";
        String viewerToken = createSession(viewerId, "User D");
        String outsiderToken = createSession("person-e", "User E");
        addMember(ownerToken, workspaceId, OUTSIDER_ID, OUTSIDER_NAME, "ADMIN");
        addMember(ownerToken, workspaceId, SECOND_OWNER_ID, SECOND_OWNER_NAME, "MEMBER");
        addMember(ownerToken, workspaceId, viewerId, "User D", "VIEWER");

        mockMvc.perform(patch("/api/workspaces/{id}", workspaceId)
                .header(TOKEN_HEADER, ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Owner Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Owner Renamed"));

        mockMvc.perform(patch("/api/workspaces/{id}", workspaceId)
                .header(TOKEN_HEADER, adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Admin Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Admin Renamed"));

        mockMvc.perform(patch("/api/workspaces/{id}", workspaceId)
                .header(TOKEN_HEADER, memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Member Renamed\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/workspaces/{id}", workspaceId)
                .header(TOKEN_HEADER, viewerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Viewer Renamed\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/workspaces/{id}", workspaceId)
                .header(TOKEN_HEADER, outsiderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Outsider Renamed\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/workspaces/{id}", workspaceId)
                .header(TOKEN_HEADER, adminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/workspaces/{id}", workspaceId)
                .header(TOKEN_HEADER, memberToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/workspaces/{id}", workspaceId)
                .header(TOKEN_HEADER, viewerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/workspaces/{id}", workspaceId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isNoContent());

        entityManager.clear();
        assertEquals("ARCHIVED", workspaceRepository.findById(workspaceId).orElseThrow().getStatus());

        mockMvc.perform(get("/api/workspaces")
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + workspaceId + "')]").isEmpty());

        mockMvc.perform(get("/api/workspaces/{id}", workspaceId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/workspaces/{id}/items", workspaceId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void archivedWorkspaceItemsAreDeniedThroughDriveEndpoints() throws Exception {
        String workspaceId = createWorkspace(ownerToken, "Archive Gate");
        String rootItemId = workspaceRepository.findById(workspaceId).orElseThrow().getRootItemId();
        String fileId = uploadWorkspaceFile(ownerToken, workspaceId, rootItemId, "archived.txt", "archived content");

        mockMvc.perform(delete("/api/workspaces/{id}", workspaceId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/drive/items/{id}/preview", fileId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/drive/items/{id}/download", fileId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/drive/items/{id}/name", fileId)
                .header(TOKEN_HEADER, ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"still-denied.txt\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/drive/items/{id}", fileId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotModifyOwner() throws Exception {
        String workspaceId = createWorkspace(ownerToken, "Admin Bound");
        String adminToken = createSession(OUTSIDER_ID, OUTSIDER_NAME);
        addMember(ownerToken, workspaceId, OUTSIDER_ID, OUTSIDER_NAME, "ADMIN");
        String ownerMemberId = findOwnerMemberId(workspaceId);

        // Admin cannot grant OWNER role to anyone else.
        mockMvc.perform(post("/api/workspaces/{id}/members", workspaceId)
                .header(TOKEN_HEADER, adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"personOaId\":\"" + SECOND_OWNER_ID
                        + "\",\"personName\":\"" + SECOND_OWNER_NAME
                        + "\",\"role\":\"OWNER\"}"))
                .andExpect(status().isForbidden());

        // Admin cannot demote/modify the existing OWNER.
        mockMvc.perform(patch("/api/workspaces/{id}/members/{memberId}", workspaceId, ownerMemberId)
                .header(TOKEN_HEADER, adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        // Admin cannot remove the existing OWNER.
        mockMvc.perform(delete("/api/workspaces/{id}/members/{memberId}", workspaceId, ownerMemberId)
                .header(TOKEN_HEADER, adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void lastOwnerProtection() throws Exception {
        String workspaceId = createWorkspace(ownerToken, "Solo Owner");
        String ownerMemberId = findOwnerMemberId(workspaceId);

        // Owner cannot demote herself when she is the only OWNER.
        mockMvc.perform(patch("/api/workspaces/{id}/members/{memberId}", workspaceId, ownerMemberId)
                .header(TOKEN_HEADER, ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isBadRequest());

        // Owner cannot remove herself when she is the only OWNER either.
        mockMvc.perform(delete("/api/workspaces/{id}/members/{memberId}", workspaceId, ownerMemberId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isBadRequest());

        // Owner adds a second OWNER → guard relaxes (count stays ≥ 1).
        String secondOwnerMemberId = addMember(
                ownerToken, workspaceId, SECOND_OWNER_ID, SECOND_OWNER_NAME, "OWNER");

        // Demote the second owner to ADMIN — succeeds because original owner remains.
        mockMvc.perform(patch("/api/workspaces/{id}/members/{memberId}", workspaceId, secondOwnerMemberId)
                .header(TOKEN_HEADER, ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // Now promote them back to OWNER and remove the second owner — also fine.
        mockMvc.perform(patch("/api/workspaces/{id}/members/{memberId}", workspaceId, secondOwnerMemberId)
                .header(TOKEN_HEADER, ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"));

        mockMvc.perform(delete("/api/workspaces/{id}/members/{memberId}", workspaceId, secondOwnerMemberId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isNoContent());

        // After the dust settles, exactly one OWNER remains.
        long ownerCount = workspaceMemberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER);
        assertEquals(1L, ownerCount, "exactly one OWNER must remain after the round-trip");
    }

    @Test
    void workspaceMoveRenameAndPurgeStayInsideScope() throws Exception {
        String workspaceId = createWorkspace(ownerToken, "Scoped Moves");
        String rootItemId = workspaceRepository.findById(workspaceId).orElseThrow().getRootItemId();
        String memberToken = createSession(OUTSIDER_ID, OUTSIDER_NAME);
        addMember(ownerToken, workspaceId, OUTSIDER_ID, OUTSIDER_NAME, "MEMBER");

        String workspaceFolderId = createWorkspaceFolder(ownerToken, workspaceId, rootItemId, "team-folder");
        String personalFolderJson = mockMvc.perform(post("/api/drive/me/folders")
                .header(TOKEN_HEADER, ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"personal-target\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String personalFolderId = objectMapper.readTree(personalFolderJson).get("id").asText();

        String ownerFileId = uploadWorkspaceFile(ownerToken, workspaceId, rootItemId, "notes.txt", "owner file");

        mockMvc.perform(post("/api/drive/items/move")
                .header(TOKEN_HEADER, ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemIds\":[\"" + ownerFileId + "\"],\"targetParentId\":\"" + personalFolderId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFailed").value(1));

        entityManager.clear();
        DriveItem afterBadMove = driveItemRepository.findById(ownerFileId).orElseThrow();
        assertEquals(rootItemId, afterBadMove.getParentId(), "workspace item must not move into personal folder");
        assertEquals(DriveScopeType.WORKSPACE, afterBadMove.getScopeType());
        assertEquals(workspaceId, afterBadMove.getScopeId());

        mockMvc.perform(post("/api/drive/items/move")
                .header(TOKEN_HEADER, ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemIds\":[\"" + ownerFileId + "\"],\"targetParentId\":\"" + workspaceFolderId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSucceeded").value(1));

        String memberFileId = uploadWorkspaceFile(memberToken, workspaceId, workspaceFolderId, "other.txt", "member file");
        mockMvc.perform(patch("/api/drive/items/{id}/name", memberFileId)
                .header(TOKEN_HEADER, memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"notes.txt\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/drive/items/{id}", workspaceFolderId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/drive/trash/items/purge")
                .header(TOKEN_HEADER, ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemIds\":[\"" + workspaceFolderId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSucceeded").value(1));

        entityManager.clear();
        assertTrue(driveItemRepository.findById(workspaceFolderId).isEmpty(), "folder should be purged");
        assertTrue(driveItemRepository.findById(ownerFileId).isEmpty(), "owner child should be purged");
        assertTrue(driveItemRepository.findById(memberFileId).isEmpty(), "member child should be purged");
    }

    @Test
    void workspaceShareAndPublicLinkAreGovernedByOwnersAndAdmins() throws Exception {
        String workspaceId = createWorkspace(ownerToken, "Governed Space");
        String rootItemId = workspaceRepository.findById(workspaceId).orElseThrow().getRootItemId();
        String memberToken = createSession(OUTSIDER_ID, OUTSIDER_NAME);
        addMember(ownerToken, workspaceId, OUTSIDER_ID, OUTSIDER_NAME, "MEMBER");
        String memberId = workspaceMemberRepository
                .findByWorkspaceIdAndPersonOaId(workspaceId, OUTSIDER_ID)
                .orElseThrow()
                .getId();
        String fileId = uploadWorkspaceFile(memberToken, workspaceId, rootItemId, "governed.txt", "governed");

        mockMvc.perform(post("/api/drive/shares")
                .header(TOKEN_HEADER, memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemIds\":[\"" + fileId
                        + "\"],\"targets\":[{\"type\":\"person\",\"id\":\"" + SECOND_OWNER_ID
                        + "\",\"name\":\"" + SECOND_OWNER_NAME + "\"}],\"permission\":\"read\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSucceeded").value(1));

        String summaryJson = mockMvc.perform(get("/api/drive/shares/summary")
                .header(TOKEN_HEADER, ownerToken)
                .param("itemId", fileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeTargetCount").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String shareId = objectMapper.readTree(summaryJson).get("collaborators").get(0).get("shareId").asText();
        mockMvc.perform(post("/api/drive/shares/{id}/revoke", shareId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(true));

        String linkJson = mockMvc.perform(post("/api/links")
                .header(TOKEN_HEADER, memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":\"" + fileId + "\",\"permission\":\"preview\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String linkId = objectMapper.readTree(linkJson).get("id").asText();

        mockMvc.perform(get("/api/links/items/{itemId}", fileId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + linkId + "')]").isNotEmpty());

        mockMvc.perform(post("/api/links/{id}/revoke", linkId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(true));

        mockMvc.perform(post("/api/drive/shares")
                .header(TOKEN_HEADER, memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemIds\":[\"" + fileId
                        + "\"],\"targets\":[{\"type\":\"person\",\"id\":\"" + SECOND_OWNER_ID
                        + "\",\"name\":\"" + SECOND_OWNER_NAME + "\"}],\"permission\":\"read\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSucceeded").value(1));
        String linkToCleanupJson = mockMvc.perform(post("/api/links")
                .header(TOKEN_HEADER, memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":\"" + fileId + "\",\"permission\":\"preview\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String linkToCleanupId = objectMapper.readTree(linkToCleanupJson).get("id").asText();

        mockMvc.perform(patch("/api/workspaces/{id}/members/{memberId}", workspaceId, memberId)
                .header(TOKEN_HEADER, ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"VIEWER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEWER"));

        mockMvc.perform(get("/api/drive/shares/summary")
                .header(TOKEN_HEADER, ownerToken)
                .param("itemId", fileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeTargetCount").value(0));
        mockMvc.perform(get("/api/links/items/{itemId}", fileId)
                .header(TOKEN_HEADER, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + linkToCleanupId + "' && @.revoked==true)]").isNotEmpty());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private String createSession(String personOaId, String personName) {
        ensureActivePrincipal(personOaId, personName);
        O2AuthenticationResult result = new O2AuthenticationResult();
        result.setPersonOaId(personOaId);
        result.setName(personName);
        result.setDistinguishedName("uid=" + personOaId + ",ou=people,dc=cloudnext");
        result.setToken("o2-" + personOaId);
        result.setTokenType("user");
        AuthLoginResponse session = authSessionService.createSession(result);
        return session.getToken();
    }

    private String createWorkspace(String token, String name) throws Exception {
        String payload = "{\"name\":\"" + name + "\"}";
        String body = mockMvc.perform(post("/api/workspaces")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String addMember(
            String token, String workspaceId, String personOaId, String personName, String role) throws Exception {
        ensureActivePrincipal(personOaId, personName);
        String payload = "{\"personOaId\":\"" + personOaId
                + "\",\"personName\":\"" + personName
                + "\",\"role\":\"" + role + "\"}";
        String body = mockMvc.perform(post("/api/workspaces/{id}/members", workspaceId)
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private void ensureActivePrincipal(String personKey, String displayName) {
        if (principalBindingRepository.findByCanonicalPersonId(personKey).isPresent()) {
            return;
        }
        PrincipalBinding binding = new PrincipalBinding();
        binding.setId(UUID.randomUUID().toString());
        binding.setAuthProvider("FORM");
        binding.setTenantId("test-tenant");
        binding.setFormEmployeeId(personKey);
        binding.setCanonicalPersonId(personKey);
        binding.setEmployeeNoSnapshot(personKey);
        binding.setDisplayName(displayName);
        binding.setDisplayPath("uid=" + personKey + ",ou=people,dc=cloudnext");
        binding.setSourceAccountEnabled(true);
        binding.setAccountEnabled(true);
        binding.setDirectoryStatus("ACTIVE");
        binding.setStatus("ACTIVE");
        binding.setMigrationDecision("NEW");
        principalBindingRepository.saveAndFlush(binding);
    }

    private String createWorkspaceFolder(String token, String workspaceId, String parentId, String name) throws Exception {
        String body = mockMvc.perform(post("/api/workspaces/{id}/folders", workspaceId)
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":\"" + parentId + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String uploadWorkspaceFile(
            String token, String workspaceId, String parentId, String fileName, String content) throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                fileName,
                MediaType.TEXT_PLAIN_VALUE,
                content.getBytes(StandardCharsets.UTF_8));
        String body = mockMvc.perform(multipart("/api/workspaces/{id}/files", workspaceId)
                .file(upload)
                .header(TOKEN_HEADER, token)
                .param("parentId", parentId))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private String findOwnerMemberId(String workspaceId) {
        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspaceId);
        return members.stream()
                .filter(m -> m.getRole() == WorkspaceRole.OWNER)
                .findFirst()
                .map(WorkspaceMember::getId)
                .orElseThrow(() -> new AssertionError("no OWNER member in workspace " + workspaceId));
    }

    private void configureStorageMock() throws IOException {
        Mockito.reset(driveStorageService);
        Mockito.lenient().when(driveStorageService.store(anyString(), any(MultipartFile.class)))
                .thenAnswer(invocation -> {
                    String itemId = invocation.getArgument(0);
                    MultipartFile file = invocation.getArgument(1);
                    String storageKey = "mock-" + itemId + "-" + UUID.randomUUID();
                    byte[] bytes = file.getBytes();
                    storage.put(storageKey, bytes);
                    String contentType = StringUtils.hasText(file.getContentType())
                            ? file.getContentType()
                            : MediaType.APPLICATION_OCTET_STREAM_VALUE;
                    return new DriveStoredFile(storageKey, file.getSize(), contentType);
                });
        Mockito.lenient().when(driveStorageService.load(anyString())).thenAnswer(invocation -> {
            String storageKey = invocation.getArgument(0);
            byte[] bytes = storage.get(storageKey);
            if (bytes == null) {
                return new ByteArrayResource(new byte[0]) {
                    @Override
                    public boolean exists() {
                        return false;
                    }
                };
            }
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return storageKey;
                }
            };
        });
        Mockito.doAnswer(invocation -> {
            storage.remove(invocation.getArgument(0));
            return null;
        }).when(driveStorageService).delete(anyString());
    }
}
