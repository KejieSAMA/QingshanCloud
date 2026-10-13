package com.x.cloudnext.server.drive;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DriveConflictStrategy {

    ABORT("abort"),
    KEEP_BOTH("keep_both"),
    REPLACE("replace");

    private final String value;

    DriveConflictStrategy(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static DriveConflictStrategy fromValue(String value) {
        if (value == null) {
            return ABORT;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (DriveConflictStrategy strategy : values()) {
            if (strategy.value.equals(normalized)) {
                return strategy;
            }
        }
        throw new IllegalArgumentException("unsupported conflictStrategy: " + value);
    }
}
