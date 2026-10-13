package com.x.cloudnext.server.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.x.cloudnext.server.auth.entity.AuthSession;
import com.x.cloudnext.server.auth.repository.AuthSessionRepository;
import com.x.cloudnext.server.config.CloudNextProperties;
import com.x.cloudnext.server.form.CanonicalBindingService;

@Service
public class AuthSessionService {

    private final CloudNextProperties properties;
    private final AuthSessionRepository authSessionRepository;
    private final ObjectMapper objectMapper;
    private final CanonicalBindingService canonicalBindingService;

    public AuthSessionService(
            CloudNextProperties properties,
            AuthSessionRepository authSessionRepository,
            ObjectMapper objectMapper,
            CanonicalBindingService canonicalBindingService) {
        this.properties = properties;
        this.authSessionRepository = authSessionRepository;
        this.objectMapper = objectMapper;
        this.canonicalBindingService = canonicalBindingService;
    }

    @Transactional
    public AuthLoginResponse createSession(O2AuthenticationResult result) {
        return createSession(AuthenticationResult.fromO2(result));
    }

    @Transactional
    public AuthLoginResponse createSession(AuthenticationResult result) {
        return createSession(result, false);
    }

    @Transactional
    public AuthLoginResponse createSession(AuthenticationResult result, boolean rememberMe) {
        AuthSession session = new AuthSession();
        session.setId(UUID.randomUUID().toString());
        session.setToken(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        session.setPersonOaId(result.getPersonKey());
        session.setAuthProvider(result.getAuthProvider());
        session.setTenantId(result.getTenantId());
        session.setFormEmployeeId(result.getFormEmployeeId());
        session.setEmployeeNo(result.getEmployeeNo());
        session.setAuthVersion(result.getAuthVersion());
        session.setPersonName(result.getName());
        session.setPersonDistinguishedName(StringUtils.hasText(result.getDisplayPath()) ? result.getDisplayPath() : "");
        session.setTokenType(StringUtils.hasText(result.getTokenType()) ? result.getTokenType() : "user");
        session.setRoleListJson(writeJson(result.getRoleList()));
        session.setIdentityListJson(writeJson(result.getIdentityList()));
        session.setExpiresAt(Instant.now().plus(resolveSessionTtlMinutes(rememberMe), ChronoUnit.MINUTES).toString());
        authSessionRepository.save(session);
        return toResponse(session, result.isMustChangePassword());
    }

    long resolveSessionTtlMinutes(boolean rememberMe) {
        if (!rememberMe) {
            return properties.getAuth().getSessionTtlMinutes().longValue();
        }
        return properties.getAuth().getRememberSessionTtlDays().longValue() * 24L * 60L;
    }

    @Transactional(readOnly = true)
    public AuthLoginResponse currentSession(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return authSessionRepository.findByToken(token)
                .filter(this::isActive)
                .filter(this::isSubjectStillAuthorized)
                .map(session -> toResponse(session, false))
                .orElse(null);
    }

    @Transactional
    public boolean logout(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        return authSessionRepository.findByToken(token).map(session -> {
            authSessionRepository.delete(session);
            return true;
        }).orElse(false);
    }

    private boolean isActive(AuthSession session) {
        try {
            return Instant.parse(session.getExpiresAt()).isAfter(Instant.now());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isSubjectStillAuthorized(AuthSession session) {
        if (!"FORM".equalsIgnoreCase(session.getAuthProvider())) {
            return true;
        }
        return canonicalBindingService.sessionSubjectIsActive(
                session.getTenantId(), session.getFormEmployeeId(), session.getPersonOaId(),
                session.getAuthVersion() == null ? 0L : session.getAuthVersion());
    }

    private AuthLoginResponse toResponse(AuthSession session, boolean passwordExpired) {
        AuthLoginResponse response = new AuthLoginResponse();
        response.setAuthenticated(true);
        response.setToken(session.getToken());
        response.setTokenType(session.getTokenType());
        response.setPersonKey(session.getPersonOaId());
        response.setPersonOaId(session.getPersonOaId());
        response.setEmployeeNo(session.getEmployeeNo());
        response.setAuthProvider(StringUtils.hasText(session.getAuthProvider()) ? session.getAuthProvider() : "O2OA");
        response.setDisplayPath(session.getPersonDistinguishedName());
        response.setPersonName(session.getPersonName());
        response.setPersonDistinguishedName(session.getPersonDistinguishedName());
        response.setPasswordExpired(passwordExpired);
        response.setRoleList(readRoleList(session.getRoleListJson()));
        response.setIdentityList(readIdentityList(session.getIdentityListJson()));
        response.setExpiresAt(session.getExpiresAt());
        if("FORM".equalsIgnoreCase(session.getAuthProvider()))canonicalBindingService.refreshAuthorization(response,session.getTenantId(),session.getFormEmployeeId());
        return response;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Collections.emptyList() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cloudnext auth failed to serialize session payload", e);
        }
    }

    private List<String> readRoleList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<AuthIdentityDto> readIdentityList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<AuthIdentityDto>>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
