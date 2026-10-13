package com.x.cloudnext.server.drive;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.x.cloudnext.server.config.CloudNextProperties;

@Service
public class DriveOnlyofficePreviewStatusService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DriveOnlyofficePreviewStatusService.class);

    private static final String DOCUMENT_SERVER_URL_KEY = "cloudnext.drive.onlyoffice.document-server-url";
    private static final String DOCSERVICE_API_KEY = "cloudnext.drive.onlyoffice.docservice-api";
    private static final String JWT_SECRET_KEY = "cloudnext.drive.onlyoffice.jwt-secret";
    private static final String CALLBACK_BASE_URL_KEY = "cloudnext.endpoints.callback-base-url";

    private final CloudNextProperties properties;

    public DriveOnlyofficePreviewStatusService(CloudNextProperties properties) {
        this.properties = properties;
    }

    public DriveOnlyofficePreviewReadiness getReadiness() {
        List<String> missingConfigKeys = new ArrayList<>();
        CloudNextProperties.Onlyoffice onlyoffice = properties.getDrive().getOnlyoffice();
        if (!StringUtils.hasText(onlyoffice.getDocumentServerUrl())) {
            missingConfigKeys.add(DOCUMENT_SERVER_URL_KEY);
        }
        if (!StringUtils.hasText(onlyoffice.getDocserviceApi())) {
            missingConfigKeys.add(DOCSERVICE_API_KEY);
        }
        boolean jwtEnabled = Boolean.TRUE.equals(onlyoffice.getJwtEnabled());
        if (jwtEnabled && !StringUtils.hasText(onlyoffice.getJwtSecret())) {
            missingConfigKeys.add(JWT_SECRET_KEY);
        }
        return new DriveOnlyofficePreviewReadiness(missingConfigKeys.isEmpty(), jwtEnabled, missingConfigKeys);
    }

    public void requireConfigured() {
        DriveOnlyofficePreviewReadiness readiness = getReadiness();
        if (!readiness.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, readiness.getUnavailableMessage());
        }
    }

    public void requireEditConfigured() {
        DriveOnlyofficePreviewReadiness readiness = getEditReadiness();
        if (!readiness.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, readiness.getUnavailableMessage());
        }
    }

    public void requireEditEnabled() {
        if (!Boolean.TRUE.equals(properties.getIteration().getOnlyofficeEdit().isEnabled())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "cloudnext onlyoffice edit is disabled");
        }
    }

    public DriveOnlyofficePreviewReadiness getEditReadiness() {
        DriveOnlyofficePreviewReadiness readiness = getReadiness();
        List<String> missingConfigKeys = new ArrayList<>(readiness.getMissingConfigKeys());
        if (!StringUtils.hasText(properties.getEndpoints().getCallbackBaseUrl())) {
            missingConfigKeys.add(CALLBACK_BASE_URL_KEY);
        }
        return new DriveOnlyofficePreviewReadiness(
                missingConfigKeys.isEmpty(),
                readiness.isJwtEnabled(),
                missingConfigKeys);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logReadinessWarning() {
        DriveOnlyofficePreviewReadiness readiness = getReadiness();
        if (!readiness.isConfigured()) {
            LOGGER.warn("cloudnext onlyoffice preview is not configured, missing keys: {}",
                    String.join(", ", readiness.getMissingConfigKeys()));
        }
    }
}
