package com.x.cloudnext.server.drive;

/**
 * Actions that the Scope access gateway ({@link DriveScopeAccessService}) reasons about.
 *
 * <p>The matrix combining {@link DriveScopeType} (PERSONAL / WORKSPACE) and the caller's
 * role decides whether a given action is permitted on a {@link com.x.cloudnext.server.drive.entity.DriveItem}.
 */
public enum DriveScopeAction {

    READ,
    WRITE,
    DELETE,
    MANAGE_SHARE,
    MANAGE_MEMBER
}
