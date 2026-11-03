package com.x.cloudnext.server.namespace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;
import com.x.cloudnext.server.testing.MysqlTestDatabase;

/** Explicitly authorized isolated-database test only. No Spring application or schedulers are started. */
class DriveNamespaceMigrationTest {
    private static final String TENANT = "namespace-migration-fixture";
    private static final List<String> SCOPED = Arrays.asList("drive_item", "drive_share", "drive_public_link", "workspace", "workspace_member",
        "user_preference", "audit_log", "drive_public_link_access_log", "drive_user_item", "drive_item_version", "drive_write_receipt", "storage_cleanup", "auth_session");

    @Test void migrationPreservesHistoricalColumnsAndRefusesWrongTenantAndReplay() throws Exception {
        String oldSchema = resource("V001_before_drive_namespaces.sql"), currentSchema = resource("V001__cloudnext.sql"), migration = resource("V002__drive_namespaces.sql");
        Set<String> oldTables = tables(oldSchema), currentTables = tables(currentSchema);
        assertEquals(28, oldTables.size()); assertEquals(29, currentTables.size());
        MysqlTestDatabase.initialize();
        try (Connection connection = MysqlTestDatabase.connect()) {
            assertEquals("qingshancloud_test", connection.getCatalog());
            assertEquals(currentTables, existingTables(connection));
            try (ResultSet routines = connection.getMetaData().getProcedures(connection.getCatalog(), null, "cloudnext_apply_drive_namespaces_v2")) {
                assertFalse(routines.next(), "Refusing a pre-existing migration routine");
            }
            boolean createdRoutine = false;
            try {
                dropKnownTables(connection, currentTables); createSchema(connection, oldSchema);
                for (String suffix : SCOPED) seed(connection, "cloudnext_" + suffix);
                seed(connection, "cloudnext_principal_binding");
                Map<String,List<List<String>>> before = snapshot(connection, oldTables);
                String procedure = migration.substring(migration.indexOf("CREATE PROCEDURE"), migration.indexOf("END$$") + 3);
                execute(connection, procedure);
                createdRoutine = true;
                setTenant(connection, "wrong-tenant");
                assertEquals("45000", assertThrows(SQLException.class, () -> execute(connection, "CALL cloudnext_apply_drive_namespaces_v2()" )).getSQLState());
                assertFalse(existingTables(connection).contains("cloudnext_drive_namespace"));
                setTenant(connection, TENANT); execute(connection, "CALL cloudnext_apply_drive_namespaces_v2()");
                assertEquals(before, snapshot(connection, oldTables));
                String expected = DriveNamespaceService.namespaceId(TENANT, "default");
                for (String suffix : SCOPED) {
                    try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT drive_id FROM cloudnext_" + suffix)) {
                        assertTrue(rows.next()); assertEquals(expected, rows.getString(1));
                    }
                }
                assertEquals("45000", assertThrows(SQLException.class, () -> execute(connection, "CALL cloudnext_apply_drive_namespaces_v2()" )).getSQLState());
            } finally {
                if (createdRoutine) execute(connection, "DROP PROCEDURE cloudnext_apply_drive_namespaces_v2");
                dropKnownTables(connection, currentTables); createSchema(connection, currentSchema);
            }
        }
    }
    private static String resource(String name) throws Exception {
        try (var input = DriveNamespaceMigrationTest.class.getResourceAsStream("/db/mysql/" + name)) {
            if (input == null) throw new IllegalStateException("Missing reviewed migration fixture: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private static Set<String> tables(String sql) {
        Set<String> values = new LinkedHashSet<>(); Matcher matcher = Pattern.compile("CREATE TABLE `([a-z_]+)`").matcher(sql);
        while (matcher.find()) values.add(matcher.group(1)); return values;
    }
    private static Set<String> existingTables(Connection connection) throws Exception {
        Set<String> values = new LinkedHashSet<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SHOW FULL TABLES")) {
            while (rows.next()) { assertEquals("BASE TABLE", rows.getString(2)); values.add(rows.getString(1)); }
        }
        return values;
    }
    private static void dropKnownTables(Connection connection, Set<String> reviewed) throws Exception {
        assertEquals("qingshancloud_test", connection.getCatalog());
        Set<String> existing = existingTables(connection); assertTrue(reviewed.containsAll(existing), "Refusing unrelated test objects");
        for (String table : existing) execute(connection, "DROP TABLE `" + table + "`");
    }
    private static void createSchema(Connection connection, String sql) throws Exception {
        for (String statement : sql.replaceAll("(?m)^--[^\\n]*", "").split(";")) if (!statement.trim().isEmpty()) execute(connection, statement);
    }
    private static void setTenant(Connection connection, String tenant) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SET @cloudnext_namespace_tenant=?")) { statement.setString(1, tenant); statement.execute(); }
    }
    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) { statement.execute(sql); }
    }
    private static void seed(Connection connection, String table) throws Exception {
        List<String> columns = new ArrayList<>(); List<Integer> types = new ArrayList<>();
        try (ResultSet metadata = connection.getMetaData().getColumns(connection.getCatalog(), null, table, "%")) {
            while (metadata.next()) if (metadata.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls) { columns.add(metadata.getString("COLUMN_NAME")); types.add(metadata.getInt("DATA_TYPE")); }
        }
        String sql = "INSERT INTO `" + table + "` (`" + String.join("`,`", columns) + "`) VALUES (" + String.join(",", Collections.nCopies(columns.size(), "?")) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < columns.size(); i++) {
                String name = columns.get(i);
                if (types.get(i) == Types.BIT || types.get(i) == Types.BOOLEAN) statement.setBoolean(i + 1, false);
                else if (types.get(i) == Types.BIGINT || types.get(i) == Types.INTEGER) statement.setLong(i + 1, 1L);
                else if (types.get(i) == Types.TIMESTAMP) statement.setTimestamp(i + 1, Timestamp.valueOf("2026-01-01 00:00:00"));
                else statement.setString(i + 1, name.equals("tenant_id") ? TENANT : name.equals("storage_key") ? "legacy-object.docx" : name.equals("name") ? "历史文件" : "fixture");
            }
            statement.executeUpdate();
        }
    }
    private static Map<String,List<List<String>>> snapshot(Connection connection, Set<String> tables) throws Exception {
        Map<String,List<List<String>>> values = new LinkedHashMap<>();
        for (String table : tables) {
            List<List<String>> records = new ArrayList<>();
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT * FROM `" + table + "`")) {
                while (rows.next()) {
                    List<String> record = new ArrayList<>();
                    for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) if (!"drive_id".equals(rows.getMetaData().getColumnName(i))) record.add(rows.getString(i));
                    records.add(record);
                }
            }
            values.put(table, records);
        }
        return values;
    }
}
