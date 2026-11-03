package com.x.cloudnext.server.auth;

import javax.validation.constraints.NotBlank;

public class AuthLoginRequest {
    @javax.validation.constraints.Pattern(regexp = "[a-zA-Z0-9_-]{1,80}")
    private String entryId;
    public String getEntryId() { return entryId; }
    public void setEntryId(String value) { entryId = value; }


    @NotBlank
    private String credential;

    @NotBlank
    private String password;

    private boolean rememberMe;

    public String getCredential() {
        return credential;
    }

    public void setCredential(String credential) {
        this.credential = credential;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isRememberMe() {
        return rememberMe;
    }

    public void setRememberMe(boolean rememberMe) {
        this.rememberMe = rememberMe;
    }
}
