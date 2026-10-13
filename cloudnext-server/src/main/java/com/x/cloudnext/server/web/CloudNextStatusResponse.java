package com.x.cloudnext.server.web;

import java.util.ArrayList;
import java.util.List;

public class CloudNextStatusResponse {

    private String name;
    private String phase;
    private Boolean ready;
    private String orgSyncMode;
    private String orgSyncOutcome;
    private String nextAction;
    private List<String> pendingCapabilities;
    private Boolean onlyofficePreviewReady;
    private List<String> warnings = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public Boolean getReady() {
        return ready;
    }

    public void setReady(Boolean ready) {
        this.ready = ready;
    }

    public String getOrgSyncMode() {
        return orgSyncMode;
    }

    public void setOrgSyncMode(String orgSyncMode) {
        this.orgSyncMode = orgSyncMode;
    }

    public String getOrgSyncOutcome() {
        return orgSyncOutcome;
    }

    public void setOrgSyncOutcome(String orgSyncOutcome) {
        this.orgSyncOutcome = orgSyncOutcome;
    }

    public String getNextAction() {
        return nextAction;
    }

    public void setNextAction(String nextAction) {
        this.nextAction = nextAction;
    }

    public List<String> getPendingCapabilities() {
        return pendingCapabilities;
    }

    public void setPendingCapabilities(List<String> pendingCapabilities) {
        this.pendingCapabilities = pendingCapabilities;
    }

    public Boolean getOnlyofficePreviewReady() {
        return onlyofficePreviewReady;
    }

    public void setOnlyofficePreviewReady(Boolean onlyofficePreviewReady) {
        this.onlyofficePreviewReady = onlyofficePreviewReady;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
