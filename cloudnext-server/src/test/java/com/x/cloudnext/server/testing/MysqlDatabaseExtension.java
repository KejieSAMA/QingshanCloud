package com.x.cloudnext.server.testing;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class MysqlDatabaseExtension implements BeforeEachCallback, AfterEachCallback {
    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        com.x.cloudnext.server.namespace.DriveContext.clear();
        MysqlTestDatabase.reset();
    }
    @Override public void afterEach(ExtensionContext context) { com.x.cloudnext.server.namespace.DriveContext.clear(); }
}
