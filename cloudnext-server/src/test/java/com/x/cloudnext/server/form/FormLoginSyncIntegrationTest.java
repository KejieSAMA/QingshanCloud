package com.x.cloudnext.server.form;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.cloudnext.server.auth.AuthenticationResult;
import com.x.cloudnext.server.auth.AuthSessionService;
import com.x.cloudnext.server.auth.SsoBootstrapService;
import com.x.cloudnext.server.auth.SsoSessionIssuer;
import com.x.cloudnext.server.auth.repository.AuthSessionRepository;
import com.x.cloudnext.server.auth.repository.SsoBootstrapRepository;
import com.x.cloudnext.server.config.CloudNextProperties;
import com.x.cloudnext.server.form.repository.*;
import com.x.cloudnext.server.migration.entity.LegacyArchiveMigrationTask;
import com.x.cloudnext.server.migration.repository.LegacyArchiveMigrationTaskRepository;

@DataJpaTest(properties={"spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=OFF", "cloudnext.org-sync.cron=-"}, showSql=false)
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.test.context.ContextConfiguration(initializers = com.x.cloudnext.server.testing.MysqlTestDatabaseInitializer.class)
@org.junit.jupiter.api.extension.ExtendWith(com.x.cloudnext.server.testing.MysqlDatabaseExtension.class)
@Import({FormDirectorySyncService.class,FormDirectorySyncTransactionService.class,FormDirectoryApplyService.class,
        FormLoginDirectorySync.class,CanonicalBindingService.class,FormAuthenticationProvider.class,ObjectMapper.class,
        AuthSessionService.class,SsoSessionIssuer.class,SsoBootstrapService.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class FormLoginSyncIntegrationTest {
    @MockBean FormIntegrationClient client;
    @MockBean CanonicalBusinessReferenceService references;
    @Autowired FormAuthenticationProvider provider;
    @Autowired FormLoginDirectorySync loginSync;
    @Autowired FormDirectorySyncService sync;
    @Autowired FormDirectorySyncTransactionService transactions;
    @Autowired PrincipalBindingRepository principals;
    @Autowired IdentityBindingRepository identities;
    @Autowired UnitBindingRepository units;
    @Autowired DirectorySyncStateRepository states;
    @Autowired LegacyArchiveMigrationTaskRepository archives;
    @Autowired CloudNextProperties properties;
    @Autowired ObjectMapper objectMapper;
    @Autowired SsoSessionIssuer issuer;
    @Autowired SsoBootstrapService bootstrap;
    @Autowired AuthSessionRepository sessions;
    @Autowired SsoBootstrapRepository bootstraps;

    @BeforeEach void resetData() {
        bootstraps.deleteAll();sessions.deleteAll();identities.deleteAll();units.deleteAll();principals.deleteAll();states.deleteAll();archives.deleteAll();
        properties.getCutover().setMaintenanceMode(false);
    }
    private void baseline() {
        LegacyArchiveMigrationTask baseline=new LegacyArchiveMigrationTask();baseline.setVersion("isolated-test");
        baseline.setStatus("VERIFIED");baseline.setChecksum("test");baseline.setSnapshotJson("{}");archives.saveAndFlush(baseline);
    }
    private FormDirectorySnapshot snapshot(String tenant) {
        FormDirectorySnapshot snapshot=new FormDirectorySnapshot();snapshot.setTenantId(tenant);
        snapshot.setFoundationSot("sql");snapshot.setFoundationWriteImpl("sql");
        FormDirectorySnapshot.Employee employee=new FormDirectorySnapshot.Employee();
        employee.setEmployeeId("employee");employee.setEmployeeNo("TEST001");employee.setName("隔离测试员工");employee.setAccountEnabled(true);
        snapshot.setEmployees(List.of(employee));return snapshot;
    }
    private AuthenticationResult principal() {
        AuthenticationResult result=new AuthenticationResult();result.setTenantId("tenant");result.setFormEmployeeId("employee");
        result.setEmployeeNo("TEST001");result.setName("隔离测试员工");result.setAccountEnabled(true);result.setAudience("cloudnext");
        when(client.exchangeTicket("ticket")).thenReturn(result);return result;
    }
    @Test void newEmployeeFullSnapshotCommitsBeforeBindingAndPreservesTenantIsolation() {
        baseline();principal();
        when(client.fetchDirectorySnapshot(isNull())).thenAnswer(i->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"network sync must not hold login transaction");
            return FormIntegrationClient.SnapshotResponse.changed("etag-test",snapshot("tenant"));
        });
        AuthenticationResult result=provider.exchangeTicket("ticket");
        assertEquals("NEW",result.getMappingDecision());assertNotNull(result.getPersonKey());
        assertTrue(principals.existsByAuthProviderIgnoreCaseAndTenantIdAndFormEmployeeId("FORM","tenant","employee"));
        assertFalse(principals.existsByAuthProviderIgnoreCaseAndTenantIdAndFormEmployeeId("FORM","other","employee"));
        assertEquals("SUCCESS",sync.current().getLastOutcome());
        provider.exchangeTicket("ticket");verify(client,times(1)).fetchDirectorySnapshot(any());
    }
    @Test void unverifiedLegacyBaselineCreatesPendingIdentityAndDoesNotAllowLogin() {
        principal();when(client.fetchDirectorySnapshot(isNull())).thenReturn(FormIntegrationClient.SnapshotResponse.changed("etag",snapshot("tenant")));
        assertEquals("IDENTITY_MAPPING_PENDING",assertThrows(FormSsoException.class,()->provider.exchangeTicket("ticket")).getCode());
        assertEquals("PENDING",principals.findAll().get(0).getMigrationDecision());
    }
    @Test void mismatchSnapshotCannotWriteAnotherTenantsDirectory() {
        baseline();principal();when(client.fetchDirectorySnapshot(isNull())).thenReturn(FormIntegrationClient.SnapshotResponse.changed("etag",snapshot("other")));
        assertEquals("DIRECTORY_SYNC_FAILED",assertThrows(FormSsoException.class,()->provider.exchangeTicket("ticket")).getCode());
        assertEquals(0,principals.count());assertEquals("FAILED",sync.current().getLastOutcome());
    }
    @Test void databaseFailureRollsBackSnapshotAndRecordsFailedAttempt() {
        baseline();principal();FormDirectorySnapshot invalid=snapshot("tenant");
        invalid.getEmployees().get(0).setName("x".repeat(2048));
        when(client.fetchDirectorySnapshot(isNull())).thenReturn(FormIntegrationClient.SnapshotResponse.changed("etag",invalid));
        assertThrows(FormSsoException.class,()->provider.exchangeTicket("ticket"));
        assertEquals(0,principals.count());assertEquals("FAILED",sync.current().getLastOutcome());
    }
    @Test void durableCooldownAndRunningFencePreventDuplicateNetworkRequests() {
        var attempt=transactions.beginForLogin();
        assertThrows(FormDirectorySyncTransactionService.SyncAlreadyRunningException.class,()->transactions.beginForLogin());
        transactions.fail(attempt.getAttemptGeneration(),new IllegalStateException("isolated failure"));
        assertThrows(FormDirectorySyncTransactionService.SyncCooldownException.class,()->transactions.beginForLogin());
        var state=states.findById(FormDirectoryApplyService.SOURCE_KEY).orElseThrow();
        state.setLastStartedAt(Instant.now().minusSeconds(61).toString());states.saveAndFlush(state);
        assertEquals(attempt.getAttemptGeneration()+1,transactions.beginForLogin().getAttemptGeneration());
    }
    @Test void disabledBindingCannotBeReactivatedByAnEarlierTicket() {
        baseline();principal();
        when(client.fetchDirectorySnapshot(isNull())).thenReturn(FormIntegrationClient.SnapshotResponse.changed("etag",snapshot("tenant")));
        provider.exchangeTicket("ticket");
        var binding=principals.findAll().get(0);binding.setAccountEnabled(false);binding.setStatus("DISABLED");principals.saveAndFlush(binding);
        assertEquals("IDENTITY_UNAVAILABLE",assertThrows(FormSsoException.class,()->provider.exchangeTicket("ticket")).getCode());
        assertFalse(principals.findAll().get(0).isAccountEnabled());verify(client,times(1)).fetchDirectorySnapshot(any());
    }
    @Test void maintenanceDoesNotStartAutomaticSynchronization() {
        principal();properties.getCutover().setMaintenanceMode(true);
        assertEquals("DIRECTORY_SYNC_PENDING",assertThrows(FormSsoException.class,()->provider.exchangeTicket("ticket")).getCode());
        verify(client,never()).fetchDirectorySnapshot(any());assertEquals(0,principals.count());
    }
    @Test void acceptsActualV2DirectoryEnvelopeWithoutChangingIdentityMappingRules() throws Exception {
        baseline();principal();
        String json="{\"schemaVersion\":2,\"tenantId\":\"tenant\",\"authority\":{\"read\":\"sql\",\"write\":\"sql\"},"
                +"\"people\":[{\"employeeId\":\"employee\",\"employeeNo\":\"TEST001\",\"name\":\"V2 employee\",\"account\":{\"enabled\":true,\"authVersion\":0}}],"
                +"\"organizations\":[],\"assignments\":[],\"supervisors\":[],\"orgLeaderOverrides\":[],\"assignmentSupervisors\":[],\"assignmentLeaderOverrides\":[]}";
        FormDirectorySnapshot v2=objectMapper.readValue(json,FormDirectorySnapshot.class);
        when(client.fetchDirectorySnapshot(isNull())).thenReturn(FormIntegrationClient.SnapshotResponse.changed("v2-etag",v2));
        assertEquals("NEW",provider.exchangeTicket("ticket").getMappingDecision());
        assertEquals(1,principals.count());assertEquals("SUCCESS",sync.current().getLastOutcome());
    }
    @Test void unknownSnapshotVersionRemainsBlockedWithoutPartialWrites() {
        baseline();principal();FormDirectorySnapshot unknown=snapshot("tenant");unknown.setSchemaVersion(3);
        when(client.fetchDirectorySnapshot(isNull())).thenReturn(FormIntegrationClient.SnapshotResponse.changed("future-etag",unknown));
        assertEquals("DIRECTORY_SYNC_FAILED",assertThrows(FormSsoException.class,()->provider.exchangeTicket("ticket")).getCode());
        assertEquals(0,principals.count());
    }
    @Test void bootstrapCompletesOnceAndFailedIssuanceRollsBackSession() {
        baseline();principal();
        when(client.fetchDirectorySnapshot(isNull())).thenReturn(FormIntegrationClient.SnapshotResponse.changed("etag",snapshot("tenant")));
        var result=bootstrap.begin("ticket","/");
        assertEquals(1,sessions.count());assertEquals(1,bootstraps.count());
        assertNotNull(bootstrap.complete(result.getCookieValue()));assertEquals(0,bootstraps.count());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->bootstrap.complete(result.getCookieValue()));
        AuthenticationResult valid=provider.exchangeTicket("ticket");
        assertThrows(RuntimeException.class,()->issuer.issue(valid,"/"+"x".repeat(4096)));
        assertEquals(1,sessions.count(),"failed bootstrap must not leave an orphan session");assertEquals(0,bootstraps.count());
    }
}
