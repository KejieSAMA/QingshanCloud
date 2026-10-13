package com.x.cloudnext.server.drive;

public enum DriveSharePermission {
    READ,
    DOWNLOAD,
    EDIT;

    public boolean allowsPreview() {
        return true;
    }

    public boolean allowsDownload() {
        return this == DOWNLOAD || this == EDIT;
    }

    public boolean satisfies(DriveSharePermission required) {
        if (required == null) {
            return true;
        }
        return rank() >= required.rank();
    }

    public static DriveSharePermission max(DriveSharePermission left, DriveSharePermission right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.rank() >= right.rank() ? left : right;
    }

    private int rank() {
        switch (this) {
            case EDIT:
                return 3;
            case DOWNLOAD:
                return 2;
            case READ:
            default:
                return 1;
        }
    }

    public static DriveSharePermission fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return READ;
        }
        return DriveSharePermission.valueOf(value.trim().toUpperCase());
    }
}
