package com.x.cloudnext.server.orgsync;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PreDestroy;
import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.x.cloudnext.server.config.CloudNextProperties;

@Service
public class OrgSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrgSyncService.class);

    private final CloudNextProperties properties;
    private final OrgMirrorSyncService orgMirrorSyncService;
    private final OrgSyncSnapshot snapshot = OrgSyncSnapshot.bootstrap();
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cloudnext-org-sync");
        thread.setDaemon(true);
        return thread;
    });
    private boolean syncInFlight = false;
    private long runSequence = 0L;

    public OrgSyncService(CloudNextProperties properties, OrgMirrorSyncService orgMirrorSyncService) {
        this.properties = properties;
        this.orgMirrorSyncService = orgMirrorSyncService;
    }

    @PostConstruct
    public void initialize() {
        refreshConfiguredState();
    }

    @PreDestroy
    public void shutdown() {
        syncExecutor.shutdownNow();
    }

    public synchronized OrgSyncSnapshot snapshot() {
        return snapshot;
    }

    public synchronized OrgSyncSnapshot triggerManualSync() {
        refreshConfiguredState();
        if (!isLegacyO2Mode()) {
            snapshot.markWaitingConfig("legacy-o2-sync-disabled-in-form-mode");
            return snapshot;
        }
        if (!properties.getReadonlyJdbc().isConfigured()) {
            snapshot.markWaitingConfig("readonly-jdbc");
            return snapshot;
        }
        if (syncInFlight) {
            return snapshot;
        }
        queueMirrorSync("manual-trigger");
        return snapshot;
    }

    @Scheduled(cron = "${cloudnext.org-sync.cron:0 0/5 * * * *}")
    public void scheduledSync() {
        synchronized (this) {
            if (!isLegacyO2Mode()) {
                return;
            }
            refreshConfiguredState();
            if (!properties.getReadonlyJdbc().isConfigured()) {
                snapshot.markWaitingConfig("readonly-jdbc");
                return;
            }
            if (syncInFlight) {
                return;
            }
            queueMirrorSync("scheduled-trigger");
        }
    }

    private void refreshConfiguredState() {
        if (syncInFlight) {
            return;
        }
        if (!properties.getReadonlyJdbc().isConfigured()) {
            snapshot.markWaitingConfig("readonly-jdbc");
            snapshot.setBatchSize(properties.getOrgSync().getBatchSize());
            snapshot.setIntervalMinutes(properties.getOrgSync().getIntervalMinutes());
            snapshot.setCron(properties.getOrgSync().getCron());
            return;
        }
        snapshot.markScheduled(
                properties.getOrgSync().getCron(),
                properties.getOrgSync().getBatchSize(),
                properties.getOrgSync().getIntervalMinutes(),
                true);
    }

    private boolean isLegacyO2Mode() {
        return "O2OA".equalsIgnoreCase(properties.getAuth().getEffectiveProvider());
    }

    private synchronized void queueMirrorSync(String source) {
        syncInFlight = true;
        String runId = "org-sync-" + Instant.now().toEpochMilli() + "-" + (++runSequence);
        snapshot.markQueued(source, runId);
        syncExecutor.submit(() -> runMirrorSync(source, runId));
    }

    private void runMirrorSync(String source, String runId) {
        try {
            synchronized (this) {
                snapshot.markRunning("loading-source");
            }
            OrgMirrorStats stats = orgMirrorSyncService.syncAll();
            String message = "mirror sync completed from " + source + " with units=" + stats.getUnits()
                    + ", persons=" + stats.getPersons() + ", identities=" + stats.getIdentities();
            synchronized (this) {
                snapshot.markSuccess(message, stats);
            }
        } catch (Exception e) {
            LOGGER.error("CloudNext org mirror sync failed. source={}, runId={}", source, runId, e);
            synchronized (this) {
                snapshot.markFailure(buildFailureMessage(e), buildFailureLog(e));
            }
        } finally {
            synchronized (this) {
                syncInFlight = false;
            }
        }
    }

    private String buildFailureMessage(Exception e) {
        String message = e == null ? "" : e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return e == null ? "org-sync failed" : e.getClass().getSimpleName();
        }
        return message;
    }

    private String buildFailureLog(Exception e) {
        if (e == null) {
            return "";
        }
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        printWriter.println("Root cause: " + e.getClass().getName());
        if (e.getMessage() != null && !e.getMessage().trim().isEmpty()) {
            printWriter.println("Message: " + e.getMessage());
        }
        printWriter.println("Stack trace:");
        e.printStackTrace(printWriter);
        printWriter.flush();
        return writer.toString();
    }
}
