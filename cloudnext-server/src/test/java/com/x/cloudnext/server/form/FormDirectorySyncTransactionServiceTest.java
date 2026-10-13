package com.x.cloudnext.server.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.x.cloudnext.server.config.CloudNextProperties;
import com.x.cloudnext.server.form.entity.DirectorySyncState;
import com.x.cloudnext.server.form.repository.DirectorySyncStateRepository;
import com.x.cloudnext.server.migration.repository.CloudNextMetaRepository;

class FormDirectorySyncTransactionServiceTest {

    @Test
    void blocksDirectoryMutationWhileAppliedCutoverIsStillInMaintenance() {
        Fixture fixture = fixture(true);

        assertThrows(FormDirectorySyncTransactionService.CutoverAlreadyAppliedException.class,
                () -> fixture.service.begin(false));
    }

    @Test
    void resumesDirectorySynchronizationAfterMaintenanceEnds() {
        Fixture fixture = fixture(false);

        DirectorySyncState result = fixture.service.begin(false);

        assertEquals(1L, result.getAttemptGeneration());
        assertEquals("RUNNING", result.getLastOutcome());
    }

    private Fixture fixture(boolean maintenanceMode) {
        DirectorySyncStateRepository states = mock(DirectorySyncStateRepository.class);
        CloudNextMetaRepository meta = mock(CloudNextMetaRepository.class);
        FormDirectoryApplyService apply = mock(FormDirectoryApplyService.class);
        CloudNextProperties properties = new CloudNextProperties();
        properties.getCutover().setMaintenanceMode(maintenanceMode);

        DirectorySyncState state = new DirectorySyncState();
        state.setSourceKey(FormDirectoryApplyService.SOURCE_KEY);
        when(states.existsById(FormDirectoryApplyService.SOURCE_KEY)).thenReturn(true);
        when(states.findForUpdate(FormDirectoryApplyService.SOURCE_KEY)).thenReturn(Optional.of(state));
        when(states.saveAndFlush(any(DirectorySyncState.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(meta.existsById("cutover_migration_version")).thenReturn(true);

        return new Fixture(new FormDirectorySyncTransactionService(states, meta, apply, properties));
    }

    private static class Fixture {
        private final FormDirectorySyncTransactionService service;

        private Fixture(FormDirectorySyncTransactionService service) {
            this.service = service;
        }
    }
}
