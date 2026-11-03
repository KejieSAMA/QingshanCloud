package com.x.cloudnext.server.namespace;

import javax.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A verified, immutable namespace for one request. Never populated from a URL/header drive ID. */
public final class DriveContext {
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();
    private DriveContext() {}
    public static final class State {
        public final DriveDescriptor drive;
        public final String tenantId;
        public final String employeeId;
        public final long authVersion;
        public State(DriveDescriptor drive, String tenantId, String employeeId, long authVersion) {
            this.drive = drive; this.tenantId = tenantId; this.employeeId = employeeId; this.authVersion = authVersion;
        }
    }
    public static State current() { return CURRENT.get(); }
    public static String id() { return current() == null ? null : current().drive.getId(); }
    public static String requireId() {
        if (id() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "drive context required");
        return id();
    }
    public static void bind(State state) {
        if (id() != null && !id().equals(state.drive.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cross-drive access denied");
        }
        if (current() != null && current().employeeId != null && !current().employeeId.isEmpty()
            && state.employeeId != null && !state.employeeId.isEmpty() && !current().employeeId.equals(state.employeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "preview viewer does not match current session");
        }
        CURRENT.set(state);
    }
    public static void clear() { CURRENT.remove(); }
    public static void apply(EntityManager em) {
        Session session = em.unwrap(Session.class);
        if (id() == null) session.disableFilter("driveNamespace");
        else session.enableFilter("driveNamespace").setParameter("driveId", id());
    }
    public static void assertSame(String driveId) {
        if (id() != null && !id().equals(driveId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "item not found in current drive");
        }
    }
    public static boolean containsStorageKey(String key) {
        return current() == null || key.startsWith("drives/" + id() + "/") || (current().drive.isDefault() && !key.startsWith("drives/"));
    }
}
