package com.x.cloudnext.server.namespace;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.persistence.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.*;
import org.springframework.web.server.ResponseStatusException;
import com.x.cloudnext.server.auth.*;
import com.x.cloudnext.server.auth.entity.AuthSession;
import com.x.cloudnext.server.form.FormIntegrationClient;
import com.x.cloudnext.server.form.repository.PrincipalBindingRepository;

@Service
public class DriveNamespaceService {
    private final DriveNamespaceRepository namespaces;
    private final FormIntegrationClient form;
    private final PrincipalBindingRepository principals;
    @Autowired private com.x.cloudnext.server.form.repository.IdentityBindingRepository identities;
    @Autowired private com.x.cloudnext.server.form.CanonicalBindingService bindings;
    @PersistenceContext private EntityManager em;
    @Autowired @Lazy private AuthSessionService sessions;
    public DriveNamespaceService(DriveNamespaceRepository namespaces, FormIntegrationClient form, PrincipalBindingRepository principals) {
        this.namespaces = namespaces; this.form = form; this.principals = principals;
    }
    public static String namespaceId(String tenant, String entry) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(("cloudnext:" + tenant + ":" + entry).getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(); for (byte b : digest) value.append(String.format("%02x", b)); return value.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    @Transactional
    public DriveDescriptor forLogin(AuthenticationResult principal) {
        String tenant = StringUtils.hasText(principal.getTenantId()) ? principal.getTenantId() : "legacy";
        String entry = StringUtils.hasText(principal.getDriveEntryId()) ? principal.getDriveEntryId() : "default";
        String name = "青山云盘";
        if (!"default".equals(entry)) {
            if (!"FORM".equalsIgnoreCase(principal.getAuthProvider()) || principal.getDriveProtocol() != 1) throw denied();
            name = form.resolveDrive(tenant, principal.getFormEmployeeId(), entry, principal.getAuthVersion());
        }
        String id = namespaceId(tenant, entry);
        namespaces.register(id, tenant, entry);
        return new DriveDescriptor(id, entry, name);
    }
    public DriveDescriptor describe(AuthSession session) {
        DriveNamespace namespace = namespaces.findById(session.getDriveId()).orElseThrow(DriveNamespaceService::denied);
        if (StringUtils.hasText(session.getTenantId()) && !session.getTenantId().equals(namespace.getTenantId())) throw denied();
        String name = "青山云盘";
        if (!"default".equals(namespace.getEntryId())) {
            name = form.resolveDrive(namespace.getTenantId(), session.getFormEmployeeId(), namespace.getEntryId(), session.getAuthVersion());
        } else if ("FORM".equalsIgnoreCase(session.getAuthProvider())) {
            form.resolveDrive(namespace.getTenantId(), session.getFormEmployeeId(), "default", session.getAuthVersion());
        }
        return new DriveDescriptor(namespace.getId(), namespace.getEntryId(), name);
    }
    public void bind(DriveDescriptor drive, String tenant, String employee, long version) {
        DriveContext.bind(new DriveContext.State(drive, tenant, employee, version));
        if (TransactionSynchronizationManager.isActualTransactionActive()) DriveContext.apply(em);
    }
    public void enterPublicLink(String driveId) {
        DriveNamespace namespace = namespaces.findById(driveId).orElseThrow(DriveNamespaceService::denied);
        if ("default".equals(namespace.getEntryId())) {
            bind(new DriveDescriptor(driveId, "default", "青山云盘"), namespace.getTenantId(), "", 0L); return;
        }
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String token = attributes == null ? null : attributes.getRequest().getHeader("X-CloudNext-Token");
        AuthLoginResponse session = sessions.currentSession(token);
        if (session == null) {
            // Only an entry identifier is exposed, so the caller can authenticate before viewing link metadata.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "DRIVE_LOGIN_REQUIRED:" + namespace.getEntryId());
        }
        if (attributes == null || !driveId.equals(attributes.getRequest().getHeader("X-CloudNext-Drive"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "DRIVE_CONTEXT_REQUIRED");
        }
        if (session.getDrive() == null || !driveId.equals(session.getDrive().getId())) throw denied();
    }
    public void requireEligiblePerson(String canonicalPerson) {
        DriveContext.State context = DriveContext.current();
        if (context == null || context.drive.isDefault()) return;
        var person = principals.findByCanonicalPersonId(canonicalPerson).orElseThrow(DriveNamespaceService::denied);
        if (!context.tenantId.equals(person.getTenantId())) throw denied();
        form.resolveDrive(person.getTenantId(), person.getFormEmployeeId(), context.drive.getEntryId(), null);
    }
    public boolean eligiblePerson(String canonicalPerson) {
        try { requireEligiblePerson(canonicalPerson); return true; }
        catch (ResponseStatusException failure) {
            if (failure.getStatus() == HttpStatus.UNAUTHORIZED || failure.getStatus() == HttpStatus.FORBIDDEN || failure.getStatus() == HttpStatus.NOT_FOUND) return false;
            throw failure;
        }
    }
    public boolean eligibleUnit(String canonicalUnit) {
        if (DriveContext.current() == null || DriveContext.current().drive.isDefault()) return true;
        return identities.findAll().stream().filter(identity -> canonicalUnit.equals(identity.getCanonicalUnitId()) && "ACTIVE".equals(identity.getStatus()))
            .anyMatch(identity -> eligiblePerson(identity.getCanonicalPersonId()));
    }
    public void requireEligibleUnit(String canonicalUnit) { if (!eligibleUnit(canonicalUnit)) throw denied(); }
    public void enterTicket(String driveId, String tenant, String employee, long version) {
        ServletRequestAttributes request = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (request != null) {
            String expected = request.getRequest().getHeader("X-CloudNext-Drive");
            if (StringUtils.hasText(expected) && !driveId.equals(expected)) throw denied();
            String token = request.getRequest().getHeader("X-CloudNext-Token");
            if (StringUtils.hasText(token) && sessions.currentSession(token) == null) throw denied();
            if (StringUtils.hasText(token) && !StringUtils.hasText(expected) && DriveContext.current() != null && !DriveContext.current().drive.isDefault()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "DRIVE_CONTEXT_REQUIRED");
            }
        }
        DriveNamespace namespace = namespaces.findById(driveId).orElseThrow(DriveNamespaceService::denied);
        String name = "青山云盘";
        if (!"default".equals(namespace.getEntryId())) {
            if (!namespace.getTenantId().equals(tenant) || !StringUtils.hasText(employee) || version <= 0) throw denied();
            name = form.resolveDrive(tenant, employee, namespace.getEntryId(), version);
        } else if (StringUtils.hasText(tenant) && StringUtils.hasText(employee) && version > 0) {
            if (!namespace.getTenantId().equals(tenant)) throw denied();
            form.resolveDrive(tenant, employee, "default", version);
        }
        bind(new DriveDescriptor(driveId, namespace.getEntryId(), name), namespace.getTenantId(), employee, version);
    }
    public void enterLegacyTicket(String driveId) {
        DriveNamespace namespace = namespaces.findById(driveId).orElseThrow(DriveNamespaceService::denied);
        if (!"default".equals(namespace.getEntryId())) throw denied();
        bind(new DriveDescriptor(driveId, "default", "青山云盘"), namespace.getTenantId(), "", 0L);
    }
    public AuthLoginResponse ticketViewer(com.x.cloudnext.server.drive.DrivePreviewTicketPayload payload) {
        AuthLoginResponse viewer = new AuthLoginResponse();
        viewer.setDrive(DriveContext.current().drive); viewer.setAuthenticated(true);
        viewer.setPersonOaId(payload.getViewerPersonOaIdRaw()); viewer.setPersonName(payload.getViewerName());
        java.util.List<AuthIdentityDto> values = new java.util.ArrayList<>();
        for (int i = 0; i < Math.max(payload.getViewerIdentityIds().size(), payload.getViewerUnitIds().size()); i++) {
            AuthIdentityDto identity = new AuthIdentityDto();
            identity.setId(i < payload.getViewerIdentityIds().size() ? payload.getViewerIdentityIds().get(i) : "");
            identity.setUnit(i < payload.getViewerUnitIds().size() ? payload.getViewerUnitIds().get(i) : ""); values.add(identity);
        }
        viewer.setIdentityList(values);
        if (StringUtils.hasText(payload.getTenantId()) && StringUtils.hasText(payload.getFormEmployeeId())) {
            var person = principals.findByAuthProviderIgnoreCaseAndTenantIdAndFormEmployeeId("FORM", payload.getTenantId(), payload.getFormEmployeeId()).orElseThrow(DriveNamespaceService::denied);
            if (!person.getCanonicalPersonId().equals(payload.getViewerPersonOaIdRaw())) throw denied();
            bindings.refreshAuthorization(viewer, payload.getTenantId(), payload.getFormEmployeeId());
        }
        return viewer;
    }
    public void forEachMaintenanceDrive(Runnable work) {
        if (RequestContextHolder.getRequestAttributes() != null || DriveContext.current() != null) {
            throw new IllegalStateException("global drive maintenance cannot run inside a user request");
        }
        for (DriveNamespace namespace : namespaces.findAll()) {
            try {
                DriveContext.bind(new DriveContext.State(new DriveDescriptor(namespace.getId(), namespace.getEntryId(), namespace.getEntryId()), namespace.getTenantId(), "", 0L));
                if (maintenanceAllowed()) work.run();
            } catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(DriveNamespaceService.class).warn("Drive maintenance paused for namespace={}", namespace.getId(), failure);
            } finally { DriveContext.clear(); }
        }
    }
    public boolean maintenanceAllowed() {
        if (RequestContextHolder.getRequestAttributes() != null || DriveContext.current() == null) return false;
        return DriveContext.current().drive.isDefault() || form.driveEnabled(DriveContext.current().tenantId, DriveContext.current().drive.getEntryId());
    }
    private static ResponseStatusException denied() { return new ResponseStatusException(HttpStatus.FORBIDDEN, "drive access denied"); }
}
