package com.x.cloudnext.migration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** H2 is used ONLY here to manufacture old-format migration input, never in application tests. */
class MysqlMigrationIntegrationTest {
    @TempDir Path storage;
    Connection from, to;
    MysqlMigration migration;
    List<MysqlMigration.Table> tables;
    private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    private String setting(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing isolated migration test setting: " + name);
        return value;
    }

    @BeforeEach void prepare() throws Exception {
        if (!"isolated-mysql".equals(setting("CLOUDNEXT_TEST_ENVIRONMENT"))) throw new IllegalStateException("Isolated test marker required");
        String url = setting("CLOUDNEXT_TEST_DB_URL");
        if (!url.startsWith("jdbc:mysql://") || !"/qingshancloud_test".equals(java.net.URI.create(url.substring(5)).getPath()))
            throw new IllegalStateException("Migration tests must only use qingshancloud_test");
        if (setting("CLOUDNEXT_TEST_DB_USERNAME").equals(System.getenv("CLOUDNEXT_DB_USERNAME")))
            throw new IllegalStateException("Production account forbidden in tests");
        Properties settings = new Properties();
        settings.setProperty("user", setting("CLOUDNEXT_TEST_DB_USERNAME"));
        settings.setProperty("password", setting("CLOUDNEXT_TEST_DB_PASSWORD"));
        settings.setProperty("connectionTimeZone", "UTC"); settings.setProperty("forceConnectionTimeZoneToSession", "true");
        to = DriverManager.getConnection(url, settings);
        try (Statement sql = to.createStatement()) {
            try (ResultSet rs = sql.executeQuery("SELECT DATABASE(), VERSION()")) {
                if (!rs.next() || !"qingshancloud_test".equals(rs.getString(1)) || !rs.getString(2).startsWith("8.0."))
                    throw new IllegalStateException("Test database identity mismatch");
            }
            try (ResultSet rs = sql.executeQuery("SHOW GRANTS FOR CURRENT_USER()")) {
                while (rs.next()) {
                    String grant = rs.getString(1);
                    if (!grant.matches("(?i)^GRANT USAGE ON \\*\\.\\* TO .*$")
                            && (!grant.contains(" ON `qingshancloud_test`.* TO ") || grant.contains("WITH GRANT OPTION")))
                        throw new IllegalStateException("Test account has unsafe grants");
                }
            }
        }
        String schema;
        try (java.io.InputStream in = getClass().getResourceAsStream("/db/mysql/V001__cloudnext.sql")) {
            assertNotNull(in); schema = new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^--[^\\n]*", "");
        }
        tables = MysqlMigration.parseSchema(schema);
        cleanTarget();
        migration = new MysqlMigration(Map.of("CLOUDNEXT_MIGRATION_SOURCE_TIME_ZONE", "UTC"));
        from = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        try (Statement sql = from.createStatement()) {
            for (MysqlMigration.Table table : tables) {
                String fields = table.columns.stream().map(c -> MysqlMigration.q(c.name) + " "
                        + (c.type.equals("DATETIME(6)") ? "TIMESTAMP(9)" : c.type.equals("LONGTEXT") ? "CLOB" : c.type.equals("BIT(1)") ? "BOOLEAN" : c.type))
                        .collect(Collectors.joining(","));
                sql.execute("CREATE TABLE " + MysqlMigration.q(table.name) + " (" + fields + ", PRIMARY KEY ("
                        + MysqlMigration.q(table.columns.get(table.primaryIndex).name) + "))");
            }
        }
    }

    private void cleanTarget() throws Exception {
        if (tables == null) return; // No destructive cleanup if prepare/identity checks did not complete.
        Set<String> allowed = tables.stream().map(t -> t.name).collect(Collectors.toSet());
        List<String> existing = new ArrayList<>();
        try (Statement sql = to.createStatement(); ResultSet rs = sql.executeQuery("SHOW FULL TABLES")) {
            while (rs.next()) {
                if (!allowed.contains(rs.getString(1)) || !"BASE TABLE".equals(rs.getString(2)))
                    throw new IllegalStateException("Unrelated objects in test database; refusing cleanup");
                existing.add(rs.getString(1));
            }
        }
        try (Statement sql = to.createStatement()) { for (String table : existing) sql.execute("DROP TABLE " + MysqlMigration.q(table)); }
    }

    @AfterEach void close() throws Exception {
        try { if (to != null && tables != null) cleanTarget(); }
        finally { if (from != null) from.close(); if (to != null) to.close(); }
    }

    private void seed(String name, Map<String, Object> supplied) throws Exception {
        MysqlMigration.Table table = tables.stream().filter(t -> t.name.equals(name)).findFirst().orElseThrow();
        String columns = table.columns.stream().map(c -> MysqlMigration.q(c.name)).collect(Collectors.joining(","));
        String placeholders = table.columns.stream().map(c -> "?").collect(Collectors.joining(","));
        try (PreparedStatement sql = from.prepareStatement("INSERT INTO " + MysqlMigration.q(name) + " (" + columns + ") VALUES (" + placeholders + ")")) {
            for (int i = 0; i < table.columns.size(); i++) {
                MysqlMigration.Column column = table.columns.get(i);
                Object value = supplied.containsKey(column.name) ? supplied.get(column.name)
                        : column.type.startsWith("DATETIME") ? Timestamp.from(Instant.parse("2026-10-13T00:00:00Z"))
                        : column.type.startsWith("BIT") ? false : column.type.equals("BIGINT") || column.type.equals("INT") ? 0L : "";
                if (value instanceof Timestamp) sql.setTimestamp(i + 1, (Timestamp) value, UTC);
                else sql.setObject(i + 1, value);
            }
            sql.executeUpdate();
        }
    }

    @Test void copiesUnicodeCaseSensitiveIdsLargePayloadNullsAndNanoseconds() throws Exception {
        String payload = "中文📁".repeat(20000);
        seed("cloudnext_audit_log", Map.of("id", "ID", "action", "COPY", "payload", payload,
                "created_at", Timestamp.from(Instant.parse("2026-10-13T01:02:03.999999999Z"))));
        Map<String, Object> second = new HashMap<>(); second.put("id", "id"); second.put("action", "COPY"); second.put("payload", null);
        seed("cloudnext_audit_log", second);
        migration.importAll(from, to, storage, migration.inventory(from, storage));
        migration.compare(from, to);
        try (Statement sql = to.createStatement(); ResultSet rs = sql.executeQuery("SELECT payload,created_at FROM cloudnext_audit_log WHERE id='ID'")) {
            assertTrue(rs.next()); assertEquals(payload, rs.getString(1));
            assertEquals(Instant.parse("2026-10-13T01:02:03.999999Z"), rs.getTimestamp(2, UTC).toInstant());
        }
    }

    @Test void refusesNonemptyTargetWithoutDroppingExistingTables() throws Exception {
        try (Statement sql = to.createStatement()) { sql.execute(tables.get(0).ddl); }
        assertThrows(IllegalStateException.class, () -> migration.importAll(from, to, storage, migration.inventory(from, storage)));
        try (Statement sql = to.createStatement(); ResultSet rs = sql.executeQuery("SHOW TABLES")) { assertTrue(rs.next()); }
    }

    @Test void convertsLegacyWallClockUsingConfirmedSourceTimezone() throws Exception {
        seed("cloudnext_audit_log", Map.of("id", "timezone", "action", "COPY"));
        try (Statement sql = from.createStatement()) {
            sql.executeUpdate("UPDATE cloudnext_audit_log SET created_at=TIMESTAMP '2026-10-13 09:02:03.123456789'");
        }
        MysqlMigration shanghai = new MysqlMigration(Map.of("CLOUDNEXT_MIGRATION_SOURCE_TIME_ZONE", "Asia/Shanghai"));
        shanghai.importAll(from, to, storage, shanghai.inventory(from, storage));
        shanghai.compare(from, to);
        try (Statement sql = to.createStatement(); ResultSet rs = sql.executeQuery("SELECT created_at FROM cloudnext_audit_log")) {
            assertTrue(rs.next()); assertEquals(Instant.parse("2026-10-13T01:02:03.123456Z"), rs.getTimestamp(1, UTC).toInstant());
        }
    }

    @Test void rollsBackConstraintFailureAndCanRetryFromEmptyTarget() throws Exception {
        Map<String, Object> bad = new HashMap<>(); bad.put("id", "bad"); bad.put("action", "COPY"); bad.put("created_at", null);
        seed("cloudnext_audit_log", bad);
        assertThrows(SQLException.class, () -> migration.importAll(from, to, storage, migration.inventory(from, storage)));
        try (Statement sql = to.createStatement(); ResultSet rs = sql.executeQuery("SHOW TABLES")) { assertFalse(rs.next()); }
        try (Statement sql = from.createStatement()) { sql.executeUpdate("DELETE FROM cloudnext_audit_log"); }
        migration.importAll(from, to, storage, migration.inventory(from, storage));
        migration.compare(from, to);
    }

    @Test void storageChangesPreventCommitAndExistingMissingKeysArePreserved() throws Exception {
        seed("cloudnext_drive_item", Map.of("id", "file", "name", "中文", "storage_key", "missing.bin", "size_bytes", 8L));
        Files.writeString(storage.resolve("existing.txt"), "before");
        Map<String, Object> before = migration.inventory(from, storage);
        Files.writeString(storage.resolve("existing.txt"), "after");
        assertThrows(IllegalStateException.class, () -> migration.importAll(from, to, storage, before));
        Map<String, Object> current = migration.inventory(from, storage);
        assertEquals(List.of("missing.bin"), current.get("preexistingMissing"));
        migration.importAll(from, to, storage, current);
        migration.compare(from, to);
        assertEquals(current, migration.inventory(to, storage));
    }

    @Test void detectsChangedContentAndClearsOnlyEphemeralAuthenticationState() throws Exception {
        seed("cloudnext_audit_log", Map.of("id", "audit", "action", "COPY", "payload", "original"));
        seed("cloudnext_auth_session", Map.of("id", "session", "token", "token"));
        migration.importAll(from, to, storage, migration.inventory(from, storage));
        try (Statement sql = to.createStatement()) { sql.executeUpdate("UPDATE cloudnext_audit_log SET payload='changed'"); }
        assertThrows(IllegalStateException.class, () -> migration.compare(from, to));
        try (Statement sql = to.createStatement()) { sql.executeUpdate("UPDATE cloudnext_audit_log SET payload='original'"); }
        migration.compare(from, to);
        migration.clearSessions(to);
        try (Statement sql = to.createStatement(); ResultSet rs = sql.executeQuery("SELECT COUNT(*) FROM cloudnext_auth_session")) { assertTrue(rs.next()); assertEquals(0, rs.getLong(1)); }
        try (Statement sql = to.createStatement(); ResultSet rs = sql.executeQuery("SELECT COUNT(*) FROM cloudnext_audit_log")) { assertTrue(rs.next()); assertEquals(1, rs.getLong(1)); }
        try (Statement sql = to.createStatement(); ResultSet rs = sql.executeQuery("SELECT meta_value FROM cloudnext_meta WHERE meta_key='onlyoffice_ticket_not_before_epoch_ms'")) { assertTrue(rs.next()); assertTrue(Long.parseLong(rs.getString(1)) > 0); }
    }
}
