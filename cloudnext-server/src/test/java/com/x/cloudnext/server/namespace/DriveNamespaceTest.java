package com.x.cloudnext.server.namespace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.x.cloudnext.server.auth.entity.AuthSession;
import com.x.cloudnext.server.auth.SsoBootstrapService;
import com.x.cloudnext.server.drive.entity.DriveItem;
import com.x.cloudnext.server.form.FormIntegrationClient;
import com.x.cloudnext.server.form.repository.PrincipalBindingRepository;

class DriveNamespaceTest {
    @AfterEach void clear() { DriveContext.clear(); }
    @Test void sameOwnerCannotReadOrReassignAnotherDriveItem() {
        DriveDescriptor a = new DriveDescriptor("a", "cloud_a", "A"), b = new DriveDescriptor("b", "cloud_b", "B");
        DriveContext.bind(new DriveContext.State(a, "tenant", "person", 1));
        DriveItem item = new DriveItem(); item.setOwnerPersonOaId("person"); item.assignDriveNamespace();
        assertEquals("a", item.getDriveId());
        DriveContext.clear(); DriveContext.bind(new DriveContext.State(b, "tenant", "person", 1));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, item::checkDriveNamespace).getStatus());
        assertThrows(IllegalStateException.class, () -> item.setDriveId("b"));
    }
    @Test void requestCannotChangeItsVerifiedDrive() {
        DriveContext.bind(new DriveContext.State(new DriveDescriptor("a", "cloud_a", "A"), "tenant", "person", 1));
        assertThrows(ResponseStatusException.class, () -> DriveContext.bind(new DriveContext.State(new DriveDescriptor("b", "cloud_b", "B"), "tenant", "person", 1)));
    }
    @Test void existingSessionRenewsAdmissionAndNeverFallsBackToDefault() {
        DriveNamespaceRepository repository = mock(DriveNamespaceRepository.class);
        FormIntegrationClient form = mock(FormIntegrationClient.class);
        DriveNamespace namespace = new DriveNamespace(); namespace.setId("a"); namespace.setTenantId("tenant"); namespace.setEntryId("cloud_a");
        when(repository.findById("a")).thenReturn(Optional.of(namespace));
        DriveNamespaceService service = new DriveNamespaceService(repository, form, mock(PrincipalBindingRepository.class));
        AuthSession session = new AuthSession(); session.setDriveId("a"); session.setTenantId("tenant"); session.setFormEmployeeId("person"); session.setAuthVersion(1L);
        when(form.resolveDrive("tenant", "person", "cloud_a", 1L)).thenReturn("A renamed").thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertEquals("A renamed", service.describe(session).getName());
        assertThrows(ResponseStatusException.class, () -> service.describe(session));
        verify(form, times(2)).resolveDrive("tenant", "person", "cloud_a", 1L);
    }
    @Test void bootstrapStatesAreIndependentAndContainNoSessionSecret() {
        var a = new SsoBootstrapService.BootstrapResult("a-cookie-secret", "/");
        var b = new SsoBootstrapService.BootstrapResult("b-cookie-secret", "/");
        assertTrue(a.getState().matches("[a-f0-9]{32}"));
        assertNotEquals(a.getState(), b.getState());
        assertFalse(a.getState().contains("cookie-secret"));
    }
    @Test void archivedDriveMaintenanceIsSkippedAndDoesNotBlockOtherDrives() {
        DriveNamespaceRepository repository = mock(DriveNamespaceRepository.class);
        FormIntegrationClient form = mock(FormIntegrationClient.class);
        DriveNamespace a = new DriveNamespace(); a.setId("a"); a.setTenantId("tenant"); a.setEntryId("cloud_a");
        DriveNamespace b = new DriveNamespace(); b.setId("b"); b.setTenantId("tenant"); b.setEntryId("cloud_b");
        when(repository.findAll()).thenReturn(java.util.List.of(a, b));
        when(form.driveEnabled("tenant", "cloud_a")).thenReturn(false);
        when(form.driveEnabled("tenant", "cloud_b")).thenReturn(true);
        DriveNamespaceService service = new DriveNamespaceService(repository, form, mock(PrincipalBindingRepository.class));
        java.util.List<String> visited = new java.util.ArrayList<>();
        service.forEachMaintenanceDrive(() -> visited.add(DriveContext.requireId()));
        assertEquals(java.util.List.of("b"), visited);
        assertNull(DriveContext.current());
    }
}
