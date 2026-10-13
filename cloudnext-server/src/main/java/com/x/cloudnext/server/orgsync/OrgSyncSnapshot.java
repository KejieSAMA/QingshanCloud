package com.x.cloudnext.server.orgsync;

import java.time.Instant;

public class OrgSyncSnapshot {

    private String phase;
    private String mode;
    private Boolean ready;
    private Boolean hasReadonlyJdbcSource;
    private Integer batchSize;
    private Integer intervalMinutes;
    private String cron;
    private String lastOutcome;
    private String lastMessage;
    private String lastStartedAt;
    private String lastFinishedAt;
    private String nextAction;
    private String currentRunId;
    private Boolean running = Boolean.FALSE;
    private String progressStage;
    private String failureLog;
    private Long mirroredUnits = 0L;
    private Long mirroredPersons = 0L;
    private Long mirroredIdentities = 0L;
    private Long mirroredRoles = 0L;
    private Long mirroredGroups = 0L;
    private Long mirroredMemberships = 0L;

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Boolean getReady() {
        return ready;
    }

    public void setReady(Boolean ready) {
        this.ready = ready;
    }

    public Boolean getHasReadonlyJdbcSource() {
        return hasReadonlyJdbcSource;
    }

    public void setHasReadonlyJdbcSource(Boolean hasReadonlyJdbcSource) {
        this.hasReadonlyJdbcSource = hasReadonlyJdbcSource;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }

    public Integer getIntervalMinutes() {
        return intervalMinutes;
    }

    public void setIntervalMinutes(Integer intervalMinutes) {
        this.intervalMinutes = intervalMinutes;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getLastOutcome() {
        return lastOutcome;
    }

    public void setLastOutcome(String lastOutcome) {
        this.lastOutcome = lastOutcome;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getLastStartedAt() {
        return lastStartedAt;
    }

    public void setLastStartedAt(String lastStartedAt) {
        this.lastStartedAt = lastStartedAt;
    }

    public String getLastFinishedAt() {
        return lastFinishedAt;
    }

    public void setLastFinishedAt(String lastFinishedAt) {
        this.lastFinishedAt = lastFinishedAt;
    }

    public String getNextAction() {
        return nextAction;
    }

    public void setNextAction(String nextAction) {
        this.nextAction = nextAction;
    }

    public String getCurrentRunId() {
        return currentRunId;
    }

    public void setCurrentRunId(String currentRunId) {
        this.currentRunId = currentRunId;
    }

    public Boolean getRunning() {
        return running;
    }

    public void setRunning(Boolean running) {
        this.running = running;
    }

    public String getProgressStage() {
        return progressStage;
    }

    public void setProgressStage(String progressStage) {
        this.progressStage = progressStage;
    }

    public String getFailureLog() {
        return failureLog;
    }

    public void setFailureLog(String failureLog) {
        this.failureLog = failureLog;
    }

    public Long getMirroredUnits() {
        return mirroredUnits;
    }

    public void setMirroredUnits(Long mirroredUnits) {
        this.mirroredUnits = mirroredUnits;
    }

    public Long getMirroredPersons() {
        return mirroredPersons;
    }

    public void setMirroredPersons(Long mirroredPersons) {
        this.mirroredPersons = mirroredPersons;
    }

    public Long getMirroredIdentities() {
        return mirroredIdentities;
    }

    public void setMirroredIdentities(Long mirroredIdentities) {
        this.mirroredIdentities = mirroredIdentities;
    }

    public Long getMirroredRoles() {
        return mirroredRoles;
    }

    public void setMirroredRoles(Long mirroredRoles) {
        this.mirroredRoles = mirroredRoles;
    }

    public Long getMirroredGroups() {
        return mirroredGroups;
    }

    public void setMirroredGroups(Long mirroredGroups) {
        this.mirroredGroups = mirroredGroups;
    }

    public Long getMirroredMemberships() {
        return mirroredMemberships;
    }

    public void setMirroredMemberships(Long mirroredMemberships) {
        this.mirroredMemberships = mirroredMemberships;
    }

    public static OrgSyncSnapshot bootstrap() {
        OrgSyncSnapshot snapshot = new OrgSyncSnapshot();
        snapshot.setPhase("bootstrap");
        snapshot.setMode("mirror");
        snapshot.setReady(Boolean.FALSE);
        snapshot.setHasReadonlyJdbcSource(Boolean.FALSE);
        snapshot.setBatchSize(0);
        snapshot.setIntervalMinutes(0);
        snapshot.setCron("");
        snapshot.setLastOutcome("not-started");
        snapshot.setLastMessage("");
        snapshot.setLastStartedAt("");
        snapshot.setLastFinishedAt("");
        snapshot.setNextAction("load-config");
        snapshot.setCurrentRunId("");
        snapshot.setRunning(Boolean.FALSE);
        snapshot.setProgressStage("");
        snapshot.setFailureLog("");
        return snapshot;
    }

    public void markWaitingConfig(String dependency) {
        this.phase = "waiting-config";
        this.ready = Boolean.FALSE;
        this.hasReadonlyJdbcSource = Boolean.FALSE;
        this.lastOutcome = "blocked";
        this.lastMessage = dependency;
        this.nextAction = "configure-" + dependency;
        this.running = Boolean.FALSE;
        this.progressStage = "waiting-config";
    }

    public void markScheduled(String cron, Integer batchSize, Integer intervalMinutes, boolean hasReadonlyJdbcSource) {
        this.phase = "scheduled";
        this.ready = Boolean.FALSE;
        this.hasReadonlyJdbcSource = hasReadonlyJdbcSource;
        this.cron = cron;
        this.batchSize = batchSize;
        this.intervalMinutes = intervalMinutes;
        this.lastOutcome = "scheduled";
        this.lastMessage = "org-sync scheduler is ready";
        this.nextAction = "wait-first-run";
        this.running = Boolean.FALSE;
        this.progressStage = "scheduled";
    }

    public void markQueued(String message, String runId) {
        this.phase = "sync-queued";
        this.ready = Boolean.FALSE;
        this.lastOutcome = "queued";
        this.lastMessage = message;
        this.nextAction = "wait-manual-run";
        this.currentRunId = runId;
        this.running = Boolean.TRUE;
        this.progressStage = "queued";
        this.failureLog = "";
    }

    public void markRunning(String stage) {
        this.phase = "sync-running";
        this.ready = Boolean.FALSE;
        this.lastOutcome = "running";
        this.lastMessage = "org-sync mirror job is running";
        this.lastStartedAt = Instant.now().toString();
        this.nextAction = "finish-run";
        this.running = Boolean.TRUE;
        this.progressStage = stage;
        this.failureLog = "";
    }

    public void markSuccess(String message, OrgMirrorStats stats) {
        this.phase = "sync-ready";
        this.ready = Boolean.TRUE;
        this.lastOutcome = "success";
        this.lastMessage = message;
        this.lastFinishedAt = Instant.now().toString();
        this.nextAction = "implement-incremental-sync";
        this.running = Boolean.FALSE;
        this.progressStage = "completed";
        this.failureLog = "";
        if (stats != null) {
            this.mirroredUnits = stats.getUnits();
            this.mirroredPersons = stats.getPersons();
            this.mirroredIdentities = stats.getIdentities();
            this.mirroredRoles = stats.getRoles();
            this.mirroredGroups = stats.getGroups();
            this.mirroredMemberships = stats.getMemberships();
        }
    }

    public void markFailure(String message, String failureLog) {
        this.phase = "sync-failed";
        this.ready = Boolean.FALSE;
        this.lastOutcome = "failed";
        this.lastMessage = message;
        this.lastFinishedAt = Instant.now().toString();
        this.nextAction = "inspect-sync-error";
        this.running = Boolean.FALSE;
        this.progressStage = "failed";
        this.failureLog = failureLog == null ? "" : failureLog;
    }
}
