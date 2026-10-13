package com.x.cloudnext.server.drive;

public enum DriveShareTargetType {
    PERSON,
    UNIT,
    IDENTITY;

    public static DriveShareTargetType fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return PERSON;
        }
        return DriveShareTargetType.valueOf(value.trim().toUpperCase());
    }
}
