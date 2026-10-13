package com.x.cloudnext.server.testing;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.x.cloudnext.server.config.MysqlDatabasePolicy;

/** Destructive test operations are limited to one explicitly selected, separately granted schema. */
public final class MysqlTestDatabase {
    private static boolean initialized;
    private static final String DATABASE = "qingshancloud_test";
    private MysqlTestDatabase() { }

    public static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) throw new IllegalStateException("Missing isolated test setting: " + key);
        return value;
    }

    public static String url() {
        if (!"isolated-mysql".equals(required("CLOUDNEXT_TEST_ENVIRONMENT")))
            throw new IllegalStateException("Explicit isolated-mysql test environment required");
        String url = required("CLOUDNEXT_TEST_DB_URL");
        if (!DATABASE.equals(MysqlDatabasePolicy.databaseName(url)))
            throw new IllegalStateException("Tests may only connect to qingshancloud_test");
        String productionUser = System.getenv("CLOUDNEXT_DB_USERNAME");
        if (required("CLOUDNEXT_TEST_DB_USERNAME").equals(productionUser))
            throw new IllegalStateException("The test account must differ from the production account");
        return url;
    }

    public static Connection connect() throws Exception {
        Properties settings = new Properties();
        settings.setProperty("user", required("CLOUDNEXT_TEST_DB_USERNAME"));
        settings.setProperty("password", required("CLOUDNEXT_TEST_DB_PASSWORD"));
        settings.setProperty("connectionTimeZone", "UTC");
        settings.setProperty("forceConnectionTimeZoneToSession", "true");
        settings.setProperty("characterEncoding", "UTF-8");
        settings.setProperty("connectionCollation", "utf8mb4_0900_bin");
        settings.setProperty("connectTimeout", "10000");
        Connection connection = DriverManager.getConnection(url(), settings);
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery("SELECT DATABASE(), VERSION()")) {
                if (!rs.next() || !DATABASE.equals(rs.getString(1)) || !rs.getString(2).startsWith("8.0."))
                    throw new IllegalStateException("Isolated MySQL 8.0 database identity check failed");
            }
            try (ResultSet rs = statement.executeQuery("SHOW GRANTS FOR CURRENT_USER()")) {
                while (rs.next()) {
                    String grant = rs.getString(1);
                    if (grant.matches("(?i)^GRANT USAGE ON \\*\\.\\* TO .*$")) continue;
                    if (!grant.contains(" ON `" + DATABASE + "`.* TO ") || grant.contains("WITH GRANT OPTION"))
                        throw new IllegalStateException("Test account must have grants only on qingshancloud_test and no roles/global privileges");
                }
            }
            return connection;
        } catch (Exception exception) {
            connection.close();
            throw exception;
        }
    }

    private static String schema() throws Exception {
        try (InputStream input = MysqlTestDatabase.class.getResourceAsStream("/db/mysql/V001__cloudnext.sql")) {
            if (input == null) throw new IllegalStateException("Missing MySQL baseline");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^--[^\\n]*", "");
        }
    }

    private static List<String> tables() throws Exception {
        List<String> tables = new ArrayList<>();
        Matcher matcher = Pattern.compile("CREATE TABLE `([a-z_]+)`").matcher(schema());
        while (matcher.find()) tables.add(matcher.group(1));
        if (tables.size() != 28) throw new IllegalStateException("Unexpected test schema inventory");
        return tables;
    }

    public static synchronized void initialize() throws Exception {
        if (initialized) return;
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            List<String> existing = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery("SHOW FULL TABLES")) {
                while (rs.next()) existing.add(rs.getString(1));
            }
            if (existing.isEmpty()) {
                for (String sql : schema().split(";")) if (!sql.trim().isEmpty()) statement.execute(sql);
            } else if (!existing.containsAll(tables()) || existing.size() != tables().size()) {
                throw new IllegalStateException("Test schema is incomplete or contains unrelated tables; initialize it manually");
            }
        }
        initialized = true;
    }

    public static void reset() throws Exception {
        initialize();
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                for (String table : tables()) statement.executeUpdate("DELETE FROM `" + table + "`");
                connection.commit();
            } catch (Exception exception) { connection.rollback(); throw exception; }
        }
    }
}
