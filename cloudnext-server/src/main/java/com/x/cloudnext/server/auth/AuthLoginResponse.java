package com.x.cloudnext.server.auth;

import java.util.ArrayList;
import java.util.List;

public class AuthLoginResponse {

    private boolean authenticated;
    private String token;
    private String tokenType;
    private String personKey;
    private String personOaId;
    private String employeeNo;
    private String authProvider;
    private String displayPath;
    private String personName;
    private String personDistinguishedName;
    private boolean passwordExpired;
    private List<String> roleList = new ArrayList<>();
    private List<AuthIdentityDto> identityList = new ArrayList<>();
    private String expiresAt;

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getPersonOaId() {
        return personOaId;
    }

    public String getPersonKey() { return personKey; }
    public void setPersonKey(String personKey) { this.personKey = personKey; }
    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
    public String getAuthProvider() { return authProvider; }
    public void setAuthProvider(String authProvider) { this.authProvider = authProvider; }
    public String getDisplayPath() { return displayPath; }
    public void setDisplayPath(String displayPath) { this.displayPath = displayPath; }

    public void setPersonOaId(String personOaId) {
        this.personOaId = personOaId;
    }

    public String getPersonName() {
        return personName;
    }

    public void setPersonName(String personName) {
        this.personName = personName;
    }

    public String getPersonDistinguishedName() {
        return personDistinguishedName;
    }

    public void setPersonDistinguishedName(String personDistinguishedName) {
        this.personDistinguishedName = personDistinguishedName;
    }

    public boolean isPasswordExpired() {
        return passwordExpired;
    }

    public void setPasswordExpired(boolean passwordExpired) {
        this.passwordExpired = passwordExpired;
    }

    public List<String> getRoleList() {
        return roleList;
    }

    public void setRoleList(List<String> roleList) {
        this.roleList = roleList;
    }

    public List<AuthIdentityDto> getIdentityList() {
        return identityList;
    }

    public void setIdentityList(List<AuthIdentityDto> identityList) {
        this.identityList = identityList;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }
}
