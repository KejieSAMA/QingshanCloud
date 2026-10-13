package com.x.cloudnext.server.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import com.x.cloudnext.server.drive.DriveItemType;
import com.x.cloudnext.server.drive.DriveScopeType;
import com.x.cloudnext.server.drive.DriveStorageService;
import com.x.cloudnext.server.drive.DriveStoredFile;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.entity.DrivePublicLink;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;
import com.x.cloudnext.server.drive.repository.DrivePublicLinkRepository;

/**
 * Integration coverage for the iteration-two public-link surface (task 2.13).
 *
 * <p>Mirrors {@code CloudNextWorkspaceFlowTest}: a self-contained
 * {@code @SpringBootTest} that wires the real Spring context against an
 * isolated MySQL database, mocks {@link DriveStorageService} with an
 * in-memory map (so file streams round-trip without touching the disk),
 * and drives both surfaces of the feature through {@link MockMvc}:
 *
 * <ul>
 *   <li>The authenticated management surface ({@code /api/links*},
 *       gated by {@code cloudnext.iteration.public-links.enabled=true}
 *       on this test class — production default is {@code false}).</li>
 *   <li>The anonymous public surface ({@code /public/links/{token}/...}),
 *       which {@code WebMvcConfig} explicitly excludes from
 *       {@code AuthSessionInterceptor}, so requests carry no
 *       {@code X-CloudNext-Token} header.</li>
 * </ul>
 *
 * <p>Each {@code @Test} uses a fresh public-link token so the
 * {@code (token, ip)} fixed-window rate limiter on
 * {@code POST /public/links/{token}/verify} never trips during the
 * happy / sad-path mix below.
 *
 * <p>Expiry fast-forward: scenario 7 mutates {@code expiresAt} directly
 * through {@link DrivePublicLinkRepository#save(Object)} (bypassing the
 * service-layer "must lie in the future" validation), then calls
 * {@link EntityManager#clear()} so subsequent reads pull the freshly
 * persisted past timestamp. This avoids any {@code Thread.sleep}-driven
 * timing flakiness.
 */
@SpringBootTest(classes = CloudNextServerApplication.class)
@org.springframework.test.context.ContextConfiguration(initializers = com.x.cloudnext.server.testing.MysqlTestDatabaseInitializer.class)
@org.junit.jupiter.api.extension.ExtendWith(com.x.cloudnext.server.testing.MysqlDatabaseExtension.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // Iteration gate: this is the whole point of the test — flip
        // public-links on so CloudNextLinkController and
        // CloudNextPublicLinkController are registered.
        "cloudnext.iteration.public-links.enabled=true",
        "cloudnext.iteration.workspaces.enabled=true",
        "cloudnext.iteration.search.enabled=false",
        "cloudnext.iteration.governance.enabled=false",
        "cloudnext.iteration.preview-health.enabled=false",
        // Public-link runtime config. Pin the secret so the test does not
        // rely on the startup-generated ephemeral key.
        "cloudnext.public-link.base-url=http://test-cloudnext-web",
        "cloudnext.public-link.session-secret=public-link-flow-test-secret",
        "cloudnext.public-link.session-ttl-seconds=600",
        // Drive / OnlyOffice config required by DriveService.buildPreviewForItem.
        "cloudnext.endpoints.callback-base-url=http://test-cloudnext-api",
        "cloudnext.endpoints.web-base-url=http://test-cloudnext-web",
        "cloudnext.drive.preview-ticket-ttl-seconds=600",
        "cloudnext.drive.trash-retention-days=30",
        "cloudnext.drive.onlyoffice.document-server-url=http://test-onlyoffice",
        "cloudnext.drive.onlyoffice.docservice-api=http://test-onlyoffice/web-apps/apps/api/documents/api.js",
        "cloudnext.drive.onlyoffice.jwt-enabled=true",
        "cloudnext.drive.onlyoffice.jwt-secret=test-secret",
        "cloudnext.drive.onlyoffice.jwt-header=Authorization"
})
class CloudNextPublicLinkFlowTest {

    private static final String TOKEN_HEADER = "X-CloudNext-Token";

    private static final String ALICE_ID = "person-alice";
    private static final String ALICE_NAME = "Alice";
    private static final String BOB_ID = "person-bob";
    private static final String BOB_NAME = "Bob";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthSessionService authSessionService;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private DriveItemRepository driveItemRepository;

    @Autowired
    private DrivePublicLinkRepository drivePublicLinkRepository;

    @MockBean
    private DriveStorageService driveStorageService;

    @PersistenceContext
    private EntityManager entityManager;

    private final Map<String, byte[]> storage = new ConcurrentHashMap<>();

    private String aliceToken;
    private String bobToken;

    @BeforeEach
    void resetState() throws Exception {
        drivePublicLinkRepository.deleteAll();
        driveItemRepository.deleteAll();
        authSessionRepository.deleteAll();
        entityManager.clear();
        storage.clear();
        configureStorageMock();
        aliceToken = createSession(ALICE_ID, ALICE_NAME);
        bobToken = createSession(BOB_ID, BOB_NAME);
    }

    // -------------------------------------------------------------------
    // 1. Happy path: create → meta → verify → preview → download
    // -------------------------------------------------------------------
    @Test
    void happyPathCreateMetaVerifyPreviewDownload() throws Exception {
        DriveItem file = seedAlicePersonalFile("happy.txt", "txt", "text/plain", "hello-public-link");

        // POST /api/links — returns 201 with token + shareUrl
        String createBody = "{"
                + "\"itemId\":\"" + file.getId() + "\","
                + "\"permission\":\"download\","
                + "\"password\":\"secret123\","
                + "\"downloadLimit\":5"
                + "}";
        String createJson = mockMvc.perform(post("/api/links")
                .header(TOKEN_HEADER, aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.shareUrl").exists())
                .andExpect(jsonPath("$.requirePassword").value(true))
                .andExpect(jsonPath("$.permission").value("download"))
                .andExpect(jsonPath("$.downloadLimit").value(5))
                .andExpect(jsonPath("$.downloadCount").value(0))
                .andExpect(jsonPath("$.revoked").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createNode = objectMapper.readTree(createJson);
        String token = createNode.get("token").asText();
        assertTrue(StringUtils.hasText(token), "token must be populated");

        // GET /public/links/{token}/meta — anonymous
        mockMvc.perform(get("/public/links/{token}/meta", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("happy.txt"))
                .andExpect(jsonPath("$.fileType").value("txt"))
                .andExpect(jsonPath("$.permission").value("download"))
                .andExpect(jsonPath("$.requirePassword").value(true))
                .andExpect(jsonPath("$.open").value(true));

        // POST /public/links/{token}/verify — correct password issues a ticket
        String verifyJson = mockMvc.perform(post("/public/links/{token}/verify", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessTicket").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String ticket = objectMapper.readTree(verifyJson).get("accessTicket").asText();
        assertTrue(StringUtils.hasText(ticket), "accessTicket must be populated");

        // GET /public/links/{token}/preview — must include ticket (link has password)
        mockMvc.perform(get("/public/links/{token}/preview", token)
                .param("accessTicket", ticket))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value(file.getId()))
                .andExpect(jsonPath("$.fileName").value("happy.txt"));

        // GET /public/links/{token}/download — bytes + RFC5987 Content-Disposition
        mockMvc.perform(get("/public/links/{token}/download", token)
                .param("accessTicket", ticket))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        Matchers.containsString("attachment; filename*=UTF-8''")))
                .andExpect(content().bytes("hello-public-link".getBytes(StandardCharsets.UTF_8)));

        // The successful download must have bumped the count exactly once.
        entityManager.clear();
        DrivePublicLink reloaded = drivePublicLinkRepository.findByToken(token).orElseThrow();
        assertEquals(1L, (long) reloaded.getDownloadCount(), "downloadCount must be incremented on success");
    }

    // -------------------------------------------------------------------
    // 2. Wrong password → 403 "password incorrect"
    // -------------------------------------------------------------------
    @Test
    void wrongPasswordReturns403() throws Exception {
        DriveItem file = seedAlicePersonalFile("wrong-pwd.txt", "txt", "text/plain", "any");
        String token = createLink(aliceToken, file.getId(), "download", "secret123", null, null);

        mockMvc.perform(post("/public/links/{token}/verify", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("password incorrect"));
    }

    // -------------------------------------------------------------------
    // 3. Missing password on a protected link → 403 "password required"
    // -------------------------------------------------------------------
    @Test
    void missingPasswordReturns403() throws Exception {
        DriveItem file = seedAlicePersonalFile("missing-pwd.txt", "txt", "text/plain", "any");
        String token = createLink(aliceToken, file.getId(), "download", "secret123", null, null);

        mockMvc.perform(post("/public/links/{token}/verify", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("password required"));
    }

    // -------------------------------------------------------------------
    // 4. No-password link: preview / download work without an accessTicket
    // -------------------------------------------------------------------
    @Test
    void noPasswordLinkPreviewAndDownloadWithoutTicket() throws Exception {
        DriveItem file = seedAlicePersonalFile("no-pwd.txt", "txt", "text/plain", "anonymous-bytes");
        String token = createLink(aliceToken, file.getId(), "download", "", null, null);

        mockMvc.perform(get("/public/links/{token}/meta", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirePassword").value(false))
                .andExpect(jsonPath("$.open").value(true));

        // No accessTicket required — service skips the ticket check when
        // passwordHash is the empty-string sentinel.
        mockMvc.perform(get("/public/links/{token}/preview", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value(file.getId()));

        mockMvc.perform(get("/public/links/{token}/download", token))
                .andExpect(status().isOk())
                .andExpect(content().bytes("anonymous-bytes".getBytes(StandardCharsets.UTF_8)));
    }

    // -------------------------------------------------------------------
    // 5. Permission check — preview-only link cannot download
    // -------------------------------------------------------------------
    @Test
    void previewOnlyLinkDownloadReturns403() throws Exception {
        DriveItem file = seedAlicePersonalFile("preview-only.txt", "txt", "text/plain", "secret-bytes");
        String token = createLink(aliceToken, file.getId(), "preview", "", null, null);

        // verify still issues a ticket (uniform client flow); since there's
        // no password, an empty body works.
        mockMvc.perform(post("/public/links/{token}/verify", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessTicket").exists());

        // download is rejected by assertOpen with permission="preview".
        mockMvc.perform(get("/public/links/{token}/download", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("permission denied"));
    }

    // -------------------------------------------------------------------
    // 6. revoke → 410 on every public endpoint (meta stays non-throwing)
    // -------------------------------------------------------------------
    @Test
    void revokeReturns410OnAllPublicEndpoints() throws Exception {
        DriveItem file = seedAlicePersonalFile("revoke-me.txt", "txt", "text/plain", "doomed");
        String createJson = mockMvc.perform(post("/api/links")
                .header(TOKEN_HEADER, aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":\"" + file.getId()
                        + "\",\"permission\":\"download\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode created = objectMapper.readTree(createJson);
        String linkId = created.get("id").asText();
        String token = created.get("token").asText();

        // Revoke as the creator.
        mockMvc.perform(post("/api/links/{id}/revoke", linkId)
                .header(TOKEN_HEADER, aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(true));

        // meta stays non-throwing per task 2.5: returns 200 with open=false.
        mockMvc.perform(get("/public/links/{token}/meta", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false));

        // verify / preview / download all 410 with "link revoked".
        mockMvc.perform(post("/public/links/{token}/verify", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("link revoked"));

        mockMvc.perform(get("/public/links/{token}/preview", token))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("link revoked"));

        mockMvc.perform(get("/public/links/{token}/download", token))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("link revoked"));
    }

    // -------------------------------------------------------------------
    // 7. Expiry → 410 (fast-forward via direct repository write, no sleep)
    // -------------------------------------------------------------------
    @Test
    void expiredLinkReturns410() throws Exception {
        DriveItem file = seedAlicePersonalFile("expiry.txt", "txt", "text/plain", "stale");
        // Create with expiresAt one minute in the future so the create-time
        // future-instant validation is satisfied. We will fast-forward
        // below by mutating the entity directly.
        String futureExpiry = Instant.now().plusSeconds(60).toString();
        String token = createLink(aliceToken, file.getId(), "download", "", null, futureExpiry);

        // Fast-forward: write a past timestamp via the repository (bypasses
        // the service's "must be future" guard, which would reject this).
        forceExpiresAtToPast(token);

        mockMvc.perform(get("/public/links/{token}/meta", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false));

        mockMvc.perform(post("/public/links/{token}/verify", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("link expired"));

        mockMvc.perform(get("/public/links/{token}/preview", token))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("link expired"));
    }

    // -------------------------------------------------------------------
    // 8. Download limit — atomic refusal, downloadCount frozen at limit
    // -------------------------------------------------------------------
    @Test
    void downloadLimitAtomicallyRefusesBeyondCap() throws Exception {
        DriveItem file = seedAlicePersonalFile("budget.txt", "txt", "text/plain", "budget-bytes");
        String token = createLink(aliceToken, file.getId(), "download", "", 2L, null);

        // First two calls succeed.
        mockMvc.perform(get("/public/links/{token}/download", token))
                .andExpect(status().isOk())
                .andExpect(content().bytes("budget-bytes".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/public/links/{token}/download", token))
                .andExpect(status().isOk())
                .andExpect(content().bytes("budget-bytes".getBytes(StandardCharsets.UTF_8)));

        // Third call is refused before the file stream is opened.
        mockMvc.perform(get("/public/links/{token}/download", token))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("link exhausted"));

        // Refused calls must NOT bump the count past the limit.
        entityManager.clear();
        DrivePublicLink reloaded = drivePublicLinkRepository.findByToken(token).orElseThrow();
        assertEquals(2L, (long) reloaded.getDownloadCount(),
                "downloadCount must equal the limit and never overshoot");
        assertEquals(2L, (long) reloaded.getDownloadLimit(),
                "downloadLimit must remain as configured");
    }

    // -------------------------------------------------------------------
    // 9. Non-creator update / revoke → 403 "link access denied"
    // -------------------------------------------------------------------
    @Test
    void nonCreatorCannotUpdateOrRevoke() throws Exception {
        DriveItem file = seedAlicePersonalFile("alice-only.txt", "txt", "text/plain", "private");
        String createJson = mockMvc.perform(post("/api/links")
                .header(TOKEN_HEADER, aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":\"" + file.getId()
                        + "\",\"permission\":\"download\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String linkId = objectMapper.readTree(createJson).get("id").asText();
        assertNotNull(linkId);

        // Bob (a logged-in but unrelated user) tries to PATCH the link.
        mockMvc.perform(patch("/api/links/{id}", linkId)
                .header(TOKEN_HEADER, bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permission\":\"preview\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("link access denied"));

        // Bob tries to revoke.
        mockMvc.perform(post("/api/links/{id}/revoke", linkId)
                .header(TOKEN_HEADER, bobToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("link access denied"));

        // Sanity: link is still active (not modified by Bob's failed calls).
        entityManager.clear();
        DrivePublicLink reloaded = drivePublicLinkRepository.findById(linkId).orElseThrow();
        assertFalse(reloaded.isRevoked(), "Bob's failed revoke must not flip the state");
    }

    // -------------------------------------------------------------------
    // 10. Non-existent token → 404 on meta and verify
    // -------------------------------------------------------------------
    @Test
    void nonexistentTokenReturns404() throws Exception {
        mockMvc.perform(get("/public/links/{token}/meta", "nonexistent-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("link not found"));

        mockMvc.perform(post("/public/links/{token}/verify", "nonexistent-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"anything\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("link not found"));
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private String createSession(String personOaId, String personName) {
        O2AuthenticationResult result = new O2AuthenticationResult();
        result.setPersonOaId(personOaId);
        result.setName(personName);
        result.setDistinguishedName("uid=" + personOaId + ",ou=people,dc=cloudnext");
        result.setToken("o2-" + personOaId);
        result.setTokenType("user");
        AuthLoginResponse session = authSessionService.createSession(result);
        return session.getToken();
    }

    /**
     * Persist a personal-scope DriveItem owned by Alice, with a deterministic
     * storage key the in-memory mock can resolve. Mirrors
     * {@code AbstractDriveIntegrationTest.seedFile} but stays local to this
     * test class so the file lives in this class's
     * {@link #storage} map.
     */
    private DriveItem seedAlicePersonalFile(String name, String fileType, String contentType, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        DriveItem item = new DriveItem();
        item.setId(UUID.randomUUID().toString());
        item.setParentId("");
        item.setOwnerPersonOaId(ALICE_ID);
        item.setOwnerName(ALICE_NAME);
        item.setName(name);
        item.setItemType(DriveItemType.FILE);
        item.setFileType(fileType);
        item.setContentType(contentType);
        item.setSizeBytes((long) bytes.length);
        item.setStorageKey("seed-" + item.getId());
        item.setScopeType(DriveScopeType.PERSONAL);
        item.setScopeId(ALICE_ID);
        storage.put(item.getStorageKey(), bytes);
        DriveItem saved = driveItemRepository.saveAndFlush(item);
        return driveItemRepository.findById(saved.getId()).orElseThrow();
    }

    /**
     * Create a public link via {@code POST /api/links} and return the
     * generated token. {@code password}, {@code downloadLimit} and
     * {@code expiresAt} are emitted only when non-null/non-blank, mirroring
     * the JSON shape produced by the front-end client.
     */
    private String createLink(
            String sessionToken,
            String itemId,
            String permission,
            String password,
            Long downloadLimit,
            String expiresAt) throws Exception {
        StringBuilder body = new StringBuilder();
        body.append("{\"itemId\":\"").append(itemId).append("\",")
                .append("\"permission\":\"").append(permission).append("\"");
        if (password != null) {
            // Empty string is meaningful here (= "no password"); pass through.
            body.append(",\"password\":\"").append(password).append("\"");
        }
        if (downloadLimit != null) {
            body.append(",\"downloadLimit\":").append(downloadLimit.longValue());
        }
        if (StringUtils.hasText(expiresAt)) {
            body.append(",\"expiresAt\":\"").append(expiresAt).append("\"");
        }
        body.append("}");

        String created = mockMvc.perform(post("/api/links")
                .header(TOKEN_HEADER, sessionToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(created).get("token").asText();
    }

    /**
     * Bypass {@code DrivePublicLinkService.update}'s "expiresAt must lie in
     * the future" guard by writing a past timestamp directly through the
     * repository. Spring Data's {@code saveAndFlush} runs the write in its
     * own transaction (started by the repository proxy), so the change is
     * visible to subsequent {@link DrivePublicLinkRepository#findByToken
     * findByToken} calls without needing a class-level transaction.
     * {@link EntityManager#clear()} drops any L1-cached snapshot of the
     * old (future) {@code expiresAt} so the public-end controllers see the
     * past timestamp on their next read.
     */
    private void forceExpiresAtToPast(String token) {
        DrivePublicLink link = drivePublicLinkRepository.findByToken(token).orElseThrow();
        link.setExpiresAt(Instant.now().minusSeconds(3600).toString());
        drivePublicLinkRepository.saveAndFlush(link);
        entityManager.clear();
    }

    /**
     * In-memory {@link DriveStorageService} stand-in. {@code store} writes
     * to {@link #storage}, {@code load} reads from it, {@code delete}
     * removes the entry. Keeps the integration test off the local disk.
     */
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
