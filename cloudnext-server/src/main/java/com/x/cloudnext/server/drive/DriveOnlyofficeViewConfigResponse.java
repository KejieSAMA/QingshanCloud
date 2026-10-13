package com.x.cloudnext.server.drive;

import java.util.LinkedHashMap;
import java.util.Map;

public class DriveOnlyofficeViewConfigResponse {

    private String fileName;
    private String mode;
    private String docserviceApi;
    private String documentServerUrl;
    private Map<String, Object> docEditorConfig = new LinkedHashMap<>();

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getDocserviceApi() {
        return docserviceApi;
    }

    public void setDocserviceApi(String docserviceApi) {
        this.docserviceApi = docserviceApi;
    }

    public String getDocumentServerUrl() {
        return documentServerUrl;
    }

    public void setDocumentServerUrl(String documentServerUrl) {
        this.documentServerUrl = documentServerUrl;
    }

    public Map<String, Object> getDocEditorConfig() {
        return docEditorConfig;
    }

    public void setDocEditorConfig(Map<String, Object> docEditorConfig) {
        this.docEditorConfig = docEditorConfig;
    }
}
