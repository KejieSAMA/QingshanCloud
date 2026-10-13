package com.x.cloudnext.server.config;

import java.net.URI;

/** Shared by startup and isolated tests; never include a JDBC URL or credentials in errors. */
public final class MysqlDatabasePolicy {
    private MysqlDatabasePolicy() { }

    public static String databaseName(String url) {
        if (url == null || !url.startsWith("jdbc:mysql://")) {
            throw new IllegalStateException("CloudNext requires an explicit MySQL JDBC URL; no embedded database fallback");
        }
        try {
            URI uri = URI.create(url.substring(5));
            String path = uri.getPath();
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || path == null || !path.matches("/[a-zA-Z][a-zA-Z0-9_]*")) {
                throw new IllegalArgumentException();
            }
            return path.substring(1);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("CloudNext requires a MySQL host and explicit database name without URL credentials");
        }
    }
}
