package com.x.cloudnext.server.auth.entity;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;

@Entity
@Table(name = "cloudnext_auth_session")
public class AuthSession {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "token", nullable = false, length = 128, unique = true)
    private String token;

    @Column(name = "person_oa_id", nullable = false, length = 64)
    private String personOaId;

    @Column(name = "auth_provider", length = 32)
    private String authProvider;

    @Column(name = "tenant_id", length = 128)
    private String tenantId;

    @Column(name = "form_employee_id", length = 128)
    private String formEmployeeId;

    @Column(name = "employee_no", length = 128)
    private String employeeNo;

    @Column(name = "auth_version")
    private Long authVersion;

    @Column(name = "person_name", nullable = false, length = 255)
    private String personName;

    @Column(name = "person_distinguished_name", nullable = false, length = 255)
    private String personDistinguishedName;

    @Column(name = "token_type", nullable = false, length = 32)
    private String tokenType;

    @Column(name = "role_list_json", nullable = false, length = 4000)
    private String roleListJson = "[]";

    @Column(name = "identity_list_json", nullable = false, length = 4000)
    private String identityListJson = "[]";

    @Column(name = "expires_at", nullable = false, length = 64)
    private String expiresAt = "";

    @Column(name = "created_at", nullable = false, length = 64)
    private String createdAt = "";

    @Column(name = "updated_at", nullable = false, length = 64)
    private String updatedAt = "";

    @PrePersist
    public void prePersist() {
        String now = Instant.now().toString();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now().toString();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPersonOaId() {
        return personOaId;
    }

    public void setPersonOaId(String personOaId) {
        this.personOaId = personOaId;
    }

    public String getAuthProvider() { return authProvider; }
    public void setAuthProvider(String authProvider) { this.authProvider = authProvider; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getFormEmployeeId() { return formEmployeeId; }
    public void setFormEmployeeId(String formEmployeeId) { this.formEmployeeId = formEmployeeId; }
    public String getEmployeeNo() { return employeeNo; }
    public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
    public Long getAuthVersion() { return authVersion; }
    public void setAuthVersion(Long authVersion) { this.authVersion = authVersion; }

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

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getRoleListJson() {
        return roleListJson;
    }

    public void setRoleListJson(String roleListJson) {
        this.roleListJson = roleListJson;
    }

    public String getIdentityListJson() {
        return identityListJson;
    }

    public void setIdentityListJson(String identityListJson) {
        this.identityListJson = identityListJson;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
