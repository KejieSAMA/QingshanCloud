package com.x.cloudnext.server.form;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import com.x.cloudnext.server.form.entity.DirectorySyncState;

/** Joins the existing directory job. A login timeout never cancels the shared job. */
@Service
public class FormLoginDirectorySync {
    private final FormDirectorySyncService sync;
    private final CanonicalBindingService bindings;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "form-login-directory-sync");
        thread.setDaemon(true);
        return thread;
    });
    private CompletableFuture<DirectorySyncState> inFlight;
    private String inFlightTenant;

    public FormLoginDirectorySync(FormDirectorySyncService sync, CanonicalBindingService bindings) {
        this.sync = sync;
        this.bindings = bindings;
    }

    public void ensureBinding(String tenantId, String employeeId) {
        if (bindings.hasBinding(tenantId, employeeId)) return;
        DirectorySyncState result;
        try {
            result = request(tenantId).get(15, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw FormSsoException.pending();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw FormSsoException.pending();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof FormSsoException) throw (FormSsoException) e.getCause();
            throw FormSsoException.syncFailed();
        }
        // Read only after the sync transaction has committed, including when we joined a scheduled run.
        if (bindings.hasBinding(tenantId, employeeId)) return;
        if ("FAILED".equals(result.getLastOutcome())) throw FormSsoException.syncFailed();
        if ("RUNNING".equals(result.getLastOutcome()) || "NOT_STARTED".equals(result.getLastOutcome()))
            throw FormSsoException.pending();
        throw new FormSsoException(org.springframework.http.HttpStatus.CONFLICT, "DIRECTORY_BINDING_MISSING",
                "人员目录尚未建立绑定，请稍后重试；持续失败请联系管理员核对同步状态。");
    }

    synchronized CompletableFuture<DirectorySyncState> request(String tenantId) {
        if (inFlight != null && !inFlight.isDone()) {
            if (!tenantId.equals(inFlightTenant)) throw FormSsoException.syncFailed();
            return inFlight;
        }
        inFlightTenant = tenantId;
        inFlight = CompletableFuture.supplyAsync(() -> synchronizeOrJoin(tenantId), executor);
        return inFlight;
    }

    private DirectorySyncState synchronizeOrJoin(String tenantId) {
        DirectorySyncState state = sync.current();
        if (state.getSnapshotTenantId() != null && !state.getSnapshotTenantId().isEmpty()
                && !tenantId.equals(state.getSnapshotTenantId())) throw FormSsoException.syncFailed();
        boolean joined = sync.isRunning() || "RUNNING".equals(state.getLastOutcome());
        if (!joined) state = sync.syncForLogin(tenantId);
        // A concurrent scheduler or another server may have acquired the durable lock first.
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(2);
        while (sync.isRunning() || "RUNNING".equals(state.getLastOutcome())) {
            if (System.nanoTime() >= deadline) throw FormSsoException.pending();
            try { Thread.sleep(200); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw FormSsoException.pending(); }
            state = sync.current();
        }
        return state;
    }

    @PreDestroy public void close() { executor.shutdownNow(); }
}
