package com.x.cloudnext.server.auth;

public class AuthStatusResponse {

    private String mode;
    private String provider;
    private boolean configured;
    private boolean ssoEnabled;
    private String formSsoStartUrl;
    private boolean o2Configured;
    private String loginPath;
    private String rsaPublicKeyPath;
    private int sessionTtlMinutes;
    private int rememberSessionTtlDays;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getProvider(){return provider;} public void setProvider(String v){provider=v;}
    public boolean isConfigured(){return configured;} public void setConfigured(boolean v){configured=v;}
    public boolean isSsoEnabled(){return ssoEnabled;} public void setSsoEnabled(boolean v){ssoEnabled=v;}
    public String getFormSsoStartUrl(){return formSsoStartUrl;} public void setFormSsoStartUrl(String v){formSsoStartUrl=v;}

    public boolean isO2Configured() {
        return o2Configured;
    }

    public void setO2Configured(boolean o2Configured) {
        this.o2Configured = o2Configured;
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

    public int getSessionTtlMinutes() {
        return sessionTtlMinutes;
    }

    public void setSessionTtlMinutes(int sessionTtlMinutes) {
        this.sessionTtlMinutes = sessionTtlMinutes;
    }

    public int getRememberSessionTtlDays() {
        return rememberSessionTtlDays;
    }

    public void setRememberSessionTtlDays(int rememberSessionTtlDays) {
        this.rememberSessionTtlDays = rememberSessionTtlDays;
    }
}
