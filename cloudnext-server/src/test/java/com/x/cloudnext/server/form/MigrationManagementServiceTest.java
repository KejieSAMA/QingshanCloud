package com.x.cloudnext.server.form;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MigrationManagementServiceTest {

    @Test
    void explicitTombstoneMayRetainHistoricalBusinessReferences() {
        assertTrue(MigrationManagementService.allowsLivePrincipalReferences("TOMBSTONE"));
        assertFalse(MigrationManagementService.allowsLivePrincipalReferences("ARCHIVE"));
        assertFalse(MigrationManagementService.allowsLivePrincipalReferences("REVOKED"));
        assertFalse(MigrationManagementService.allowsLivePrincipalReferences("TRANSFERRED"));
        assertFalse(MigrationManagementService.allowsLivePrincipalReferences("PENDING"));
    }
}
