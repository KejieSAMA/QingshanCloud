package com.x.cloudnext.server.drive;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import com.x.cloudnext.server.testing.MysqlDatabaseExtension;
import com.x.cloudnext.server.testing.MysqlTestDatabaseInitializer;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.cloudnext.server.CloudNextServerApplication;
import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.auth.AuthSessionService;
import com.x.cloudnext.server.auth.O2AuthenticationResult;
import com.x.cloudnext.server.auth.repository.AuthSessionRepository;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.drive.repository.DriveItemRepository;
import com.x.cloudnext.server.form.entity.PrincipalBinding;
import com.x.cloudnext.server.form.repository.PrincipalBindingRepository;
@SpringBootTest(classes = CloudNextServerApplication.class)
@ContextConfiguration(initializers = MysqlTestDatabaseInitializer.class)
@ExtendWith(MysqlDatabaseExtension.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
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
abstract class AbstractDriveIntegrationTest {

    protected static final String TOKEN_HEADER = "X-CloudNext-Token";
    protected static final String DEFAULT_OWNER_ID = "person-a";
    protected static final String DEFAULT_OWNER_NAME = "User A";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected AuthSessionService authSessionService;

    @Autowired
    protected DriveItemRepository driveItemRepository;

    @Autowired
    protected AuthSessionRepository authSessionRepository;

    @Autowired
    protected PrincipalBindingRepository principalBindingRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected DrivePreviewTicketService drivePreviewTicketService;

    @Autowired
    protected DriveTrashCleanupService driveTrashCleanupService;

    @MockBean
    protected DriveStorageService driveStorageService;

    @PersistenceContext
    protected EntityManager entityManager;

    protected final Map<String, byte[]> storage = new ConcurrentHashMap<>();
    protected String token;

    @BeforeEach
    void baseSetUp() throws Exception {
        jdbcTemplate.update("delete from cloudnext_storage_cleanup");
        jdbcTemplate.update("delete from cloudnext_drive_write_receipt");
        jdbcTemplate.update("delete from cloudnext_drive_item_version");
        jdbcTemplate.update("delete from cloudnext_drive_user_item");
        driveItemRepository.deleteAll();
        authSessionRepository.deleteAll();
        principalBindingRepository.deleteAll();
        entityManager.clear();
        storage.clear();
        configureStorageMock();
        token = createSession(DEFAULT_OWNER_ID, DEFAULT_OWNER_NAME);
    }

    protected String createSession(String personOaId, String personName) {
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

    protected void ensureActivePrincipal(String personKey, String displayName) {
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

    protected DriveItem seedFolder(String ownerPersonOaId, String parentId, String name) {
        DriveItem item = new DriveItem();
        item.setId(UUID.randomUUID().toString());
        item.setParentId(parentId == null ? "" : parentId);
        item.setOwnerPersonOaId(ownerPersonOaId);
        item.setOwnerName(ownerDisplayName(ownerPersonOaId));
        item.setName(name);
        item.setItemType(DriveItemType.FOLDER);
        item.setFileType("folder");
        item.setContentType("");
        item.setSizeBytes(0L);
        item.setStorageKey("");
        item.setScopeType(DriveScopeType.PERSONAL);
        item.setScopeId(ownerPersonOaId);
        DriveItem saved = driveItemRepository.saveAndFlush(item);
        return driveItemRepository.findById(saved.getId()).orElseThrow();
    }

    protected DriveItem seedFile(
            String ownerPersonOaId,
            String parentId,
            String name,
            String fileType,
            String contentType,
            byte[] bytes) {
        byte[] content = bytes == null ? new byte[0] : bytes;
        DriveItem item = new DriveItem();
        item.setId(UUID.randomUUID().toString());
        item.setParentId(parentId == null ? "" : parentId);
        item.setOwnerPersonOaId(ownerPersonOaId);
        item.setOwnerName(ownerDisplayName(ownerPersonOaId));
        item.setName(name);
        item.setItemType(DriveItemType.FILE);
        item.setFileType(fileType);
        item.setContentType(contentType);
        item.setSizeBytes((long) content.length);
        item.setStorageKey("seed-" + item.getId());
        item.setScopeType(DriveScopeType.PERSONAL);
        item.setScopeId(ownerPersonOaId);
        storage.put(item.getStorageKey(), content);
        DriveItem saved = driveItemRepository.saveAndFlush(item);
        return driveItemRepository.findById(saved.getId()).orElseThrow();
    }

    protected DriveItem syncItem(DriveItem item) {
        jdbcTemplate.update(
                "update cloudnext_drive_item set parent_id=?, owner_person_oa_id=?, owner_name=?, name=?, item_type=?, "
                        + "file_type=?, content_type=?, size_bytes=?, storage_key=?, scope_type=?, scope_id=?, storage_backend=?, "
                        + "deleted=?, deleted_at=?, created_at=?, updated_at=?, last_accessed_at=? where id=?",
                item.getParentId(),
                item.getOwnerPersonOaId(),
                item.getOwnerName(),
                item.getName(),
                item.getItemType().name(),
                item.getFileType(),
                item.getContentType(),
                item.getSizeBytes(),
                item.getStorageKey(),
                item.getScopeType().name(),
                item.getScopeId(),
                item.getStorageBackend(),
                item.isDeleted(),
                item.getDeletedAt(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                item.getLastAccessedAt(),
                item.getId());
        entityManager.clear();
        return driveItemRepository.findById(item.getId()).orElseThrow();
    }

    protected DriveItem reloadItem(String itemId) {
        entityManager.clear();
        return driveItemRepository.findById(itemId).orElseThrow();
    }

    protected MockMultipartFile uploadFile(String fileName, String contentType, String content) {
        return new MockMultipartFile(
                "file",
                fileName,
                contentType,
                content.getBytes(StandardCharsets.UTF_8));
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected Instant now() {
        return Instant.now();
    }

    private void configureStorageMock() throws IOException {
        Mockito.reset(driveStorageService);
        Mockito.lenient().when(driveStorageService.store(anyString(), any(MultipartFile.class))).thenAnswer(invocation -> {
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
        Mockito.lenient().when(driveStorageService.store(anyString(), anyString(), any(byte[].class), anyString()))
                .thenAnswer(invocation -> {
                    String itemId = invocation.getArgument(0);
                    byte[] bytes = invocation.getArgument(2);
                    String contentType = invocation.getArgument(3);
                    String storageKey = "mock-" + itemId + "-" + UUID.randomUUID();
                    storage.put(storageKey, bytes == null ? new byte[0] : bytes);
                    return new DriveStoredFile(
                            storageKey,
                            bytes == null ? 0L : bytes.length,
                            StringUtils.hasText(contentType) ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE);
                });
        Mockito.lenient().when(driveStorageService.copy(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String sourceStorageKey = invocation.getArgument(0);
                    String targetItemId = invocation.getArgument(1);
                    String contentType = invocation.getArgument(3);
                    byte[] bytes = storage.get(sourceStorageKey);
                    if (bytes == null) {
                        throw new IllegalStateException("source storage object not found");
                    }
                    String targetStorageKey = "mock-copy-" + targetItemId + "-" + UUID.randomUUID();
                    storage.put(targetStorageKey, bytes.clone());
                    return new DriveStoredFile(
                            targetStorageKey,
                            (long) bytes.length,
                            StringUtils.hasText(contentType) ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE);
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

    private String ownerDisplayName(String ownerPersonOaId) {
        if (DEFAULT_OWNER_ID.equals(ownerPersonOaId)) {
            return DEFAULT_OWNER_NAME;
        }
        return "User-" + ownerPersonOaId;
    }
}
