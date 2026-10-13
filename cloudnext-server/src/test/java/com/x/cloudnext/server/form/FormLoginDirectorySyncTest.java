package com.x.cloudnext.server.form;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import com.x.cloudnext.server.form.entity.DirectorySyncState;

class FormLoginDirectorySyncTest {
    private DirectorySyncState state(String outcome) {
        DirectorySyncState state=new DirectorySyncState(); state.setLastOutcome(outcome); return state;
    }
    @Test void existingBindingDoesNotLoadDirectory() {
        FormDirectorySyncService sync=mock(FormDirectorySyncService.class);
        CanonicalBindingService bindings=mock(CanonicalBindingService.class);
        when(bindings.hasBinding("tenant","employee")).thenReturn(true);
        FormLoginDirectorySync service=new FormLoginDirectorySync(sync,bindings);
        try { service.ensureBinding("tenant","employee"); verifyNoInteractions(sync); }
        finally { service.close(); }
    }
    @Test void concurrentMissingBindingsShareOneCommittedSnapshot() throws Exception {
        FormDirectorySyncService sync=mock(FormDirectorySyncService.class);
        CanonicalBindingService bindings=mock(CanonicalBindingService.class);
        AtomicBoolean committed=new AtomicBoolean();
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        when(bindings.hasBinding("tenant","employee")).thenAnswer(i->committed.get());
        when(sync.current()).thenReturn(state("NOT_STARTED"));
        when(sync.syncForLogin("tenant")).thenAnswer(i->{entered.countDown();release.await();committed.set(true);return state("SUCCESS");});
        FormLoginDirectorySync service=new FormLoginDirectorySync(sync,bindings);
        try {
            CompletableFuture<DirectorySyncState> first=service.request("tenant");
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            for(int i=0;i<10;i++) assertSame(first,service.request("tenant"));
            release.countDown(); first.get(2,TimeUnit.SECONDS);
            service.ensureBinding("tenant","employee");
            verify(sync,times(1)).syncForLogin("tenant");
        } finally { release.countDown();service.close(); }
    }
    @Test void joinsScheduledJobWithoutStartingAnotherSync() throws Exception {
        FormDirectorySyncService sync=mock(FormDirectorySyncService.class);
        CanonicalBindingService bindings=mock(CanonicalBindingService.class);
        when(sync.current()).thenReturn(state("RUNNING"),state("SUCCESS"));
        FormLoginDirectorySync service=new FormLoginDirectorySync(sync,bindings);
        try {
            assertEquals("SUCCESS",service.request("tenant").get(2,TimeUnit.SECONDS).getLastOutcome());
            verify(sync,never()).syncForLogin(anyString());
        } finally { service.close(); }
    }
    @Test void failedSyncDoesNotPretendEmployeeWasCreated() {
        FormDirectorySyncService sync=mock(FormDirectorySyncService.class);
        CanonicalBindingService bindings=mock(CanonicalBindingService.class);
        when(sync.current()).thenReturn(state("NOT_STARTED"));
        when(sync.syncForLogin("tenant")).thenReturn(state("FAILED"));
        FormLoginDirectorySync service=new FormLoginDirectorySync(sync,bindings);
        try { assertEquals("DIRECTORY_SYNC_FAILED",assertThrows(FormSsoException.class,()->service.ensureBinding("tenant","employee")).getCode()); }
        finally { service.close(); }
    }
    @Test void loginTimeoutDoesNotCancelBackgroundSynchronization() throws Exception {
        FormDirectorySyncService sync=mock(FormDirectorySyncService.class);
        CanonicalBindingService bindings=mock(CanonicalBindingService.class);
        CountDownLatch release=new CountDownLatch(1);
        when(sync.current()).thenReturn(state("NOT_STARTED"));
        when(sync.syncForLogin("tenant")).thenAnswer(i->{release.await();return state("SUCCESS");});
        FormLoginDirectorySync service=new FormLoginDirectorySync(sync,bindings);
        try {
            CompletableFuture<DirectorySyncState> future=service.request("tenant");
            assertEquals("DIRECTORY_SYNC_PENDING",assertThrows(FormSsoException.class,()->service.ensureBinding("tenant","employee")).getCode());
            assertFalse(future.isCancelled()); assertFalse(future.isDone());
            release.countDown(); assertEquals("SUCCESS",future.get(2,TimeUnit.SECONDS).getLastOutcome());
        } finally { release.countDown();service.close(); }
    }
    @Test void differentTenantDoesNotTriggerSyncOrGuessBinding() {
        FormDirectorySyncService sync=mock(FormDirectorySyncService.class);
        CanonicalBindingService bindings=mock(CanonicalBindingService.class);
        DirectorySyncState existing=state("SUCCESS");existing.setSnapshotTenantId("other-tenant");
        when(sync.current()).thenReturn(existing);
        FormLoginDirectorySync service=new FormLoginDirectorySync(sync,bindings);
        try {
            assertThrows(FormSsoException.class,()->service.ensureBinding("tenant","employee"));
            verify(sync,never()).syncForLogin(anyString());
        } finally { service.close(); }
    }
}
