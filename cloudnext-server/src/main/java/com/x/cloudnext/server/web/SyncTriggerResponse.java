package com.x.cloudnext.server.web;

import com.x.cloudnext.server.orgsync.OrgSyncSnapshot;

public class SyncTriggerResponse {

    private Boolean accepted;
    private String phase;
    private String lastOutcome;
    private String lastMessage;
    private String nextAction;
    private String currentRunId;
    private Boolean running;
    private String progressStage;
    private String failureLog;

    public Boolean getAccepted() {
        return accepted;
    }

    public void setAccepted(Boolean accepted) {
        this.accepted = accepted;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getLastOutcome() {
        return lastOutcome;
    }

    public void setLastOutcome(String lastOutcome) {
        this.lastOutcome = lastOutcome;
    }

    public String getNextAction() {
        return nextAction;
    }

    public void setNextAction(String nextAction) {
        this.nextAction = nextAction;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
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

    public static SyncTriggerResponse fromSnapshot(OrgSyncSnapshot snapshot) {
        SyncTriggerResponse response = new SyncTriggerResponse();
        response.setAccepted(Boolean.TRUE);
        response.setPhase(snapshot.getPhase());
        response.setLastOutcome(snapshot.getLastOutcome());
        response.setLastMessage(snapshot.getLastMessage());
        response.setNextAction(snapshot.getNextAction());
        response.setCurrentRunId(snapshot.getCurrentRunId());
        response.setRunning(snapshot.getRunning());
        response.setProgressStage(snapshot.getProgressStage());
        response.setFailureLog(snapshot.getFailureLog());
        return response;
    }
}
