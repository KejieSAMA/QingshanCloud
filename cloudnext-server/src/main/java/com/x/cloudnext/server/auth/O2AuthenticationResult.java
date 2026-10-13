package com.x.cloudnext.server.auth;

import java.util.List;

public class O2AuthenticationResult {

    private String personOaId;
    private String name;
    private String distinguishedName;
    private String token;
    private String tokenType;
    private List<String> roleList;
    private List<AuthIdentityDto> identityList;
    private Boolean passwordExpired;

    public String getPersonOaId() {
        return personOaId;
    }

    public void setPersonOaId(String personOaId) {
        this.personOaId = personOaId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDistinguishedName() {
        return distinguishedName;
    }

    public void setDistinguishedName(String distinguishedName) {
        this.distinguishedName = distinguishedName;
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

    public Boolean getPasswordExpired() {
        return passwordExpired;
    }

    public void setPasswordExpired(Boolean passwordExpired) {
        this.passwordExpired = passwordExpired;
    }
}
