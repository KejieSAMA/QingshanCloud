package com.x.cloudnext.server.auth;

import javax.validation.constraints.NotBlank;

public class AuthLoginRequest {

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
