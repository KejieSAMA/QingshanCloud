package com.x.cloudnext.server.testing;

import com.x.cloudnext.server.auth.AuthLoginResponse;
import com.x.cloudnext.server.namespace.*;

/** Explicit fixture ownership; never used by application/runtime code. */
public final class NamespaceTestSupport {
    private NamespaceTestSupport() {}
    public static <T> T inDefaultDrive(java.util.function.Supplier<T> work) {
        DriveContext.State previous = DriveContext.current();
        DriveContext.clear();
        DriveContext.bind(new DriveContext.State(new DriveDescriptor(DriveNamespaceService.namespaceId("legacy", "default"), "default", "青山云盘"), "legacy", "", 0L));
        try { return work.get(); }
        finally { DriveContext.clear(); if (previous != null) DriveContext.bind(previous); }
    }
    public static void background(Runnable work) {
        var request = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        DriveContext.State previous = DriveContext.current();
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        DriveContext.clear();
        try { work.run(); }
        finally {
            DriveContext.clear(); if (previous != null) DriveContext.bind(previous);
            if (request != null) org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(request);
        }
    }
    public static <T extends DriveScopedEntity> T scoped(T entity) {
        entity.setDriveId(DriveContext.id() == null ? DriveNamespaceService.namespaceId("legacy", "default") : DriveContext.id());
        return entity;
    }
    public static AuthLoginResponse session(AuthLoginResponse session) {
        session.setDrive(DriveContext.current() == null
            ? new DriveDescriptor(DriveNamespaceService.namespaceId("legacy", "default"), "default", "青山云盘") : DriveContext.current().drive);
        return session;
    }
}
