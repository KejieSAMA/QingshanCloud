package com.x.cloudnext.server.drive;

/**
 * Membership roles inside a Workspace.
 *
 * <p>The Workspace / WorkspaceMember entities themselves are introduced in task 1.1.
 * This enum lives next to the access-gateway types so that {@link DriveScopeAccessService}
 * can reference it without depending on entity classes that do not exist yet.
 */
public enum WorkspaceRole {

    OWNER,
    ADMIN,
    MEMBER,
    VIEWER
}
