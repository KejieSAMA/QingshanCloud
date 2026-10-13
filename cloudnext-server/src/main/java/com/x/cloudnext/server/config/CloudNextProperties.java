package com.x.cloudnext.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "cloudnext")
public class CloudNextProperties {

    private final ReadonlyJdbc readonlyJdbc = new ReadonlyJdbc();
    private final OrgSync orgSync = new OrgSync();
    private final Auth auth = new Auth();
    private final ObjectStorage objectStorage = new ObjectStorage();
    private final Endpoints endpoints = new Endpoints();
    private final Drive drive = new Drive();
    private final Iteration iteration = new Iteration();
    private final PublicLink publicLink = new PublicLink();
    private final Storage storage = new Storage();
    private final Admin admin = new Admin();
    private final Cutover cutover = new Cutover();

    public ReadonlyJdbc getReadonlyJdbc() {
        return readonlyJdbc;
    }

    public OrgSync getOrgSync() {
        return orgSync;
    }

    public Auth getAuth() {
        return auth;
    }

    public ObjectStorage getObjectStorage() {
        return objectStorage;
    }

    public Endpoints getEndpoints() {
        return endpoints;
    }

    public Drive getDrive() {
        return drive;
    }

    public Iteration getIteration() {
        return iteration;
    }

    public PublicLink getPublicLink() {
        return publicLink;
    }

    public Storage getStorage() {
        return storage;
    }

    public Admin getAdmin() {
        return admin;
    }
    public Cutover getCutover(){return cutover;}

    public static class Cutover {
        private boolean maintenanceMode=false;
        private String migrationVersion="";
        public boolean isMaintenanceMode(){return maintenanceMode;}
        public void setMaintenanceMode(boolean maintenanceMode){this.maintenanceMode=maintenanceMode;}
        public String getMigrationVersion(){return migrationVersion;}
        public void setMigrationVersion(String migrationVersion){this.migrationVersion=migrationVersion==null?"":migrationVersion.trim();}
    }

    /**
     * Admin 白名单 — 限定 /api/admin/* 端点访问权限。
     * personOaIds 来自 Spring binding 的逗号分隔字符串，由 {@link com.x.cloudnext.server.auth.AdminAccessService}
     * 解析为 Set 后做 O(1) 校验。空值表示"全部拒绝"，确保未显式配置时不会被泛任意登录用户访问。
     */
    public static class Admin {
        private String personOaIds = "";
        private String personKeys = "";

        public String getPersonOaIds() {
            return personOaIds;
        }

        public void setPersonOaIds(String personOaIds) {
            this.personOaIds = personOaIds == null ? "" : personOaIds;
        }

        public String getPersonKeys() {
            return personKeys;
        }

        public void setPersonKeys(String personKeys) {
            this.personKeys = personKeys == null ? "" : personKeys;
        }
    }

    public static class ReadonlyJdbc {
        private String url = "";
        private String username = "";
        private String password = "";
        private String driverClassName = "com.mysql.cj.jdbc.Driver";

        public boolean isConfigured() {
            return StringUtils.hasText(this.url) && StringUtils.hasText(this.username);
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }
    }

    public static class OrgSync {
        private Integer batchSize = 500;
        private Integer intervalMinutes = 5;
        private String cron = "0 0/5 * * * *";

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
    }

    public static class Auth {
        private String mode = "form";
        private String provider = "FORM";
        private String o2BaseUrl = "";
        private String loginPath = "/jaxrs/authentication";
        private String rsaPublicKeyPath = "/jaxrs/authentication/captchaRSAPublicKey";
        private Integer sessionTtlMinutes = 480;
        private Integer rememberSessionTtlDays = 30;
        private Integer connectTimeoutMs = 5000;
        private Integer readTimeoutMs = 10000;
        private String formBaseUrl = "";
        private String formVerifyPasswordPath = "/internal/v1/cloudnext/auth/verify-password";
        private String formExchangeTicketPath = "/internal/v1/cloudnext/auth/exchange-ticket";
        private String formDirectorySnapshotPath = "/internal/v1/cloudnext/directory/snapshot";
        private String formServiceToken = "";
        private String formSsoStartUrl = "";
        private String formSsoAudience = "cloudnext";
        private Boolean formSsoEnabled = true;
        private Boolean formShadowSyncEnabled = false;
        private Boolean allowInsecureHttp = false;
        private Integer ssoBootstrapTtlSeconds = 60;

        public boolean isConfigured() {
            if ("FORM".equalsIgnoreCase(getEffectiveProvider())) {
                return isFormConfigured();
            }
            return isO2Configured();
        }

        public String getEffectiveProvider() {
            if ("delegate-o2oa".equalsIgnoreCase(mode) || "O2OA".equalsIgnoreCase(provider)) return "O2OA";
            return "FORM";
        }

        public boolean isFormConfigured() {
            return StringUtils.hasText(this.formBaseUrl) && StringUtils.hasText(this.formServiceToken);
        }

        public boolean isO2Configured() {
            return StringUtils.hasText(this.o2BaseUrl);
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getO2BaseUrl() {
            return o2BaseUrl;
        }

        public void setO2BaseUrl(String o2BaseUrl) {
            this.o2BaseUrl = o2BaseUrl;
        }

        public String getLoginPath() {
            return loginPath;
        }

        public void setLoginPath(String loginPath) {
            this.loginPath = loginPath;
        }

        public String getRsaPublicKeyPath() {
            return rsaPublicKeyPath;
        }

        public void setRsaPublicKeyPath(String rsaPublicKeyPath) {
            this.rsaPublicKeyPath = rsaPublicKeyPath;
        }

        public Integer getSessionTtlMinutes() {
            return sessionTtlMinutes;
        }

        public void setSessionTtlMinutes(Integer sessionTtlMinutes) {
            this.sessionTtlMinutes = sessionTtlMinutes == null ? 480 : Math.max(1, Math.min(480, sessionTtlMinutes));
        }

        public Integer getRememberSessionTtlDays() {
            return rememberSessionTtlDays;
        }

        public void setRememberSessionTtlDays(Integer rememberSessionTtlDays) {
            this.rememberSessionTtlDays = rememberSessionTtlDays == null
                    ? 30
                    : Math.max(1, Math.min(90, rememberSessionTtlDays));
        }

        public Integer getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(Integer connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public Integer getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(Integer readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public String getFormBaseUrl() { return formBaseUrl; }
        public void setFormBaseUrl(String formBaseUrl) { this.formBaseUrl = formBaseUrl; }
        public String getFormVerifyPasswordPath() { return formVerifyPasswordPath; }
        public void setFormVerifyPasswordPath(String value) { this.formVerifyPasswordPath = value; }
        public String getFormExchangeTicketPath() { return formExchangeTicketPath; }
        public void setFormExchangeTicketPath(String value) { this.formExchangeTicketPath = value; }
        public String getFormDirectorySnapshotPath() { return formDirectorySnapshotPath; }
        public void setFormDirectorySnapshotPath(String value) { this.formDirectorySnapshotPath = value; }
        public String getFormServiceToken() { return formServiceToken; }
        public void setFormServiceToken(String formServiceToken) { this.formServiceToken = formServiceToken; }
        public String getFormSsoStartUrl() { return formSsoStartUrl; }
        public void setFormSsoStartUrl(String formSsoStartUrl) { this.formSsoStartUrl = formSsoStartUrl; }
        public String getFormSsoAudience(){return formSsoAudience;} public void setFormSsoAudience(String v){this.formSsoAudience=StringUtils.hasText(v)?v.trim():"cloudnext";}
        public Boolean getFormSsoEnabled() { return formSsoEnabled; }
        public void setFormSsoEnabled(Boolean formSsoEnabled) { this.formSsoEnabled = formSsoEnabled; }
        public Boolean getFormShadowSyncEnabled(){return formShadowSyncEnabled;} public void setFormShadowSyncEnabled(Boolean v){formShadowSyncEnabled=v;}
        public Boolean getAllowInsecureHttp() { return allowInsecureHttp; }
        public void setAllowInsecureHttp(Boolean allowInsecureHttp) { this.allowInsecureHttp = allowInsecureHttp; }
        public Integer getSsoBootstrapTtlSeconds() { return ssoBootstrapTtlSeconds; }
        public void setSsoBootstrapTtlSeconds(Integer value) { this.ssoBootstrapTtlSeconds = value == null ? 60 : Math.max(10, Math.min(120, value)); }
    }

    public static class ObjectStorage {
        private String endpoint = "";
        private String bucket = "";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }

    public static class Endpoints {
        private String callbackBaseUrl = "";
        private String webBaseUrl = "";

        public String getCallbackBaseUrl() {
            return callbackBaseUrl;
        }

        public void setCallbackBaseUrl(String callbackBaseUrl) {
            this.callbackBaseUrl = callbackBaseUrl;
        }

        public String getWebBaseUrl() {
            return webBaseUrl;
        }

        public void setWebBaseUrl(String webBaseUrl) {
            this.webBaseUrl = webBaseUrl;
        }
    }

    public static class Drive {
        private Integer previewTicketTtlSeconds = 600;
        private Integer onlyofficeEditTicketTtlSeconds = 86400;
        // Legacy single-bucket retention; kept as a fallback when the
        // personal/workspace specific keys are not configured.
        private Integer trashRetentionDays = 30;
        // Iteration 3: dual-bucket retention split.
        private Integer personalTrashRetentionDays = 30;
        private Integer workspaceTrashRetentionDays = 30;
        private String trashCleanupCron = "0 0 3 * * *";
        private final Onlyoffice onlyoffice = new Onlyoffice();

        public Integer getPreviewTicketTtlSeconds() {
            return previewTicketTtlSeconds;
        }

        public void setPreviewTicketTtlSeconds(Integer previewTicketTtlSeconds) {
            this.previewTicketTtlSeconds = previewTicketTtlSeconds;
        }

        public Integer getOnlyofficeEditTicketTtlSeconds() {
            return onlyofficeEditTicketTtlSeconds;
        }

        public void setOnlyofficeEditTicketTtlSeconds(Integer onlyofficeEditTicketTtlSeconds) {
            this.onlyofficeEditTicketTtlSeconds = onlyofficeEditTicketTtlSeconds;
        }

        public Integer getTrashRetentionDays() {
            return trashRetentionDays;
        }

        public void setTrashRetentionDays(Integer trashRetentionDays) {
            this.trashRetentionDays = trashRetentionDays;
        }

        public Integer getPersonalTrashRetentionDays() {
            return personalTrashRetentionDays;
        }

        public void setPersonalTrashRetentionDays(Integer personalTrashRetentionDays) {
            this.personalTrashRetentionDays = personalTrashRetentionDays;
        }

        public Integer getWorkspaceTrashRetentionDays() {
            return workspaceTrashRetentionDays;
        }

        public void setWorkspaceTrashRetentionDays(Integer workspaceTrashRetentionDays) {
            this.workspaceTrashRetentionDays = workspaceTrashRetentionDays;
        }

        public String getTrashCleanupCron() {
            return trashCleanupCron;
        }

        public void setTrashCleanupCron(String trashCleanupCron) {
            this.trashCleanupCron = trashCleanupCron;
        }

        public Onlyoffice getOnlyoffice() {
            return onlyoffice;
        }
    }

    public static class Onlyoffice {
        private String documentServerUrl = "";
        private String docserviceApi = "";
        private Boolean jwtEnabled = false;
        private String jwtSecret = "";
        private String jwtHeader = "Authorization";

        public boolean hasDocumentServerConfig() {
            return StringUtils.hasText(this.documentServerUrl) && StringUtils.hasText(this.docserviceApi);
        }

        public boolean hasJwtSecret() {
            return StringUtils.hasText(this.jwtSecret);
        }

        public String getDocumentServerUrl() {
            return documentServerUrl;
        }

        public void setDocumentServerUrl(String documentServerUrl) {
            this.documentServerUrl = documentServerUrl;
        }

        public String getDocserviceApi() {
            return docserviceApi;
        }

        public void setDocserviceApi(String docserviceApi) {
            this.docserviceApi = docserviceApi;
        }

        public Boolean getJwtEnabled() {
            return jwtEnabled;
        }

        public void setJwtEnabled(Boolean jwtEnabled) {
            this.jwtEnabled = jwtEnabled;
        }

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public String getJwtHeader() {
            return jwtHeader;
        }

        public void setJwtHeader(String jwtHeader) {
            this.jwtHeader = jwtHeader;
        }
    }

    /**
     * Iteration gate flags. Each iteration's controllers are mounted via
     * {@code @ConditionalOnProperty} bound to these flags so that the
     * pendingCapabilities list and the actual HTTP surface stay in sync.
     */
    public static class Iteration {
        private final Workspaces workspaces = new Workspaces();
        private final PublicLinks publicLinks = new PublicLinks();
        private final Search search = new Search();
        private final OnlyofficeEdit onlyofficeEdit = new OnlyofficeEdit();
        private final Governance governance = new Governance();
        private final PreviewHealth previewHealth = new PreviewHealth();
        private final StorageGateway storageGateway = new StorageGateway();

        public Workspaces getWorkspaces() {
            return workspaces;
        }

        public PublicLinks getPublicLinks() {
            return publicLinks;
        }

        public Search getSearch() {
            return search;
        }

        public OnlyofficeEdit getOnlyofficeEdit() {
            return onlyofficeEdit;
        }

        public Governance getGovernance() {
            return governance;
        }

        public PreviewHealth getPreviewHealth() {
            return previewHealth;
        }

        public StorageGateway getStorageGateway() {
            return storageGateway;
        }

        public static class Workspaces {
            /** Iteration 1 capability; ships enabled once the workspace closure lands. */
            private boolean enabled = true;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }

        public static class PublicLinks {
            /** Iteration 2 capability; default off until iteration 2 validated. */
            private boolean enabled = false;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }

        public static class Search {
            /** Iteration 3 capability; default off until iteration 3 validated. */
            private boolean enabled = false;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }

        public static class OnlyofficeEdit {
            /** Online edit/save-back closure; default off until validated. */
            private boolean enabled = false;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }

        public static class Governance {
            /** Iteration 3 capability (quota / audit / retention); default off. */
            private boolean enabled = false;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }

        public static class PreviewHealth {
            /** Iteration 3 capability; default off until iteration 3 validated. */
            private boolean enabled = false;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }

        public static class StorageGateway {
            /** Iteration 3 capability; flips StorageGateway from local to s3. */
            private boolean objectStorageEnabled = false;

            public boolean isObjectStorageEnabled() {
                return objectStorageEnabled;
            }

            public void setObjectStorageEnabled(boolean objectStorageEnabled) {
                this.objectStorageEnabled = objectStorageEnabled;
            }
        }
    }

    /**
     * Public-link runtime parameters (consumed by iteration 2). The session
     * secret should be set explicitly in production; if left blank the server
     * will generate one at startup and log an INFO entry.
     */
    public static class PublicLink {
        private String baseUrl = "";
        private String sessionSecret = "";
        private Integer sessionTtlSeconds = 1800;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getSessionSecret() {
            return sessionSecret;
        }

        public void setSessionSecret(String sessionSecret) {
            this.sessionSecret = sessionSecret;
        }

        public Integer getSessionTtlSeconds() {
            return sessionTtlSeconds;
        }

        public void setSessionTtlSeconds(Integer sessionTtlSeconds) {
            this.sessionTtlSeconds = sessionTtlSeconds;
        }
    }

    /**
     * Storage gateway selection. {@code backend} is {@code local} or {@code s3}.
     * Local stays the default so existing deployments keep working; the
     * {@code s3.*} placeholders are only consumed when
     * {@code cloudnext.iteration.storage-gateway.object-storage-enabled} flips
     * to {@code true}.
     */
    public static class Storage {
        private String backend = "local";
        private final Local local = new Local();
        private final S3 s3 = new S3();

        public String getBackend() {
            return backend;
        }

        public void setBackend(String backend) {
            this.backend = backend;
        }

        public Local getLocal() {
            return local;
        }

        public S3 getS3() {
            return s3;
        }

        public static class Local {
            private String root = "./cloudnext-server/.data/drive-storage";

            public String getRoot() {
                return root;
            }

            public void setRoot(String root) {
                this.root = root;
            }
        }

        public static class S3 {
            private String endpoint = "";
            private String region = "";
            private String bucket = "";
            private String accessKey = "";
            private String secretKey = "";
            private Boolean pathStyleAccess = true;

            public String getEndpoint() {
                return endpoint;
            }

            public void setEndpoint(String endpoint) {
                this.endpoint = endpoint;
            }

            public String getRegion() {
                return region;
            }

            public void setRegion(String region) {
                this.region = region;
            }

            public String getBucket() {
                return bucket;
            }

            public void setBucket(String bucket) {
                this.bucket = bucket;
            }

            public String getAccessKey() {
                return accessKey;
            }

            public void setAccessKey(String accessKey) {
                this.accessKey = accessKey;
            }

            public String getSecretKey() {
                return secretKey;
            }

            public void setSecretKey(String secretKey) {
                this.secretKey = secretKey;
            }

            public Boolean getPathStyleAccess() {
                return pathStyleAccess;
            }

            public void setPathStyleAccess(Boolean pathStyleAccess) {
                this.pathStyleAccess = pathStyleAccess;
            }
        }
    }
}
