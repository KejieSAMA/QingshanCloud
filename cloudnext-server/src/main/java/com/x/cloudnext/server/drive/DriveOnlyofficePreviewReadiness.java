package com.x.cloudnext.server.drive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DriveOnlyofficePreviewReadiness {

    private final boolean configured;
    private final boolean jwtEnabled;
    private final List<String> missingConfigKeys;

    public DriveOnlyofficePreviewReadiness(boolean configured, boolean jwtEnabled, List<String> missingConfigKeys) {
        this.configured = configured;
        this.jwtEnabled = jwtEnabled;
        this.missingConfigKeys = missingConfigKeys == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(missingConfigKeys));
    }

    public boolean isConfigured() {
        return configured;
    }

    public boolean isJwtEnabled() {
        return jwtEnabled;
    }

    public List<String> getMissingConfigKeys() {
        return missingConfigKeys;
    }

    public String getUnavailableCode() {
        return "onlyoffice_not_configured";
    }

    public String getUnavailableMessage() {
        if (missingConfigKeys.isEmpty()) {
            return "OnlyOffice preview is not configured.";
        }
        return "OnlyOffice preview is not configured. Missing config: " + String.join(", ", missingConfigKeys);
    }

    public List<String> getWarnings() {
        if (configured) {
            return Collections.emptyList();
        }
        return Collections.singletonList(getUnavailableMessage());
    }
}
