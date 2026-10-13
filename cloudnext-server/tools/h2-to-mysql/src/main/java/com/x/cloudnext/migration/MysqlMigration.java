package com.x.cloudnext.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Offline, single-worker migration. Passwords are read from environment only. No application dependency. */
public final class MysqlMigration {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    private static final String LOCK = "cloudnext_h2_mysql_migration";
    private static final Set<String> MODES = Set.of("preview", "import", "verify", "clear-sessions");
    private final Map<String, String> env;
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<Table> tables;
    private final String schema;
    private final Calendar sourceTimeZone;
    private long truncatedTimestamps;
    private Path reportFile;

    public MysqlMigration(Map<String, String> env) throws Exception {
        this.env = env;
        try (InputStream input = MysqlMigration.class.getResourceAsStream("/db/mysql/V001__cloudnext.sql")) {
            if (input == null) throw new IllegalStateException("MySQL baseline resource missing");
            schema = new String(input.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^--[^\\n]*", "");
        }
        tables = parseSchema(schema);
        sourceTimeZone = Calendar.getInstance(TimeZone.getTimeZone(ZoneId.of(required("CLOUDNEXT_MIGRATION_SOURCE_TIME_ZONE"))));
    }

    public static void main(String[] args) {
        try {
            if (args.length != 2 || !"--mode".equals(args[0]) || !MODES.contains(args[1]))
                throw new IllegalArgumentException("Usage: java -jar h2-to-mysql.jar --mode preview|import|verify|clear-sessions");
            new MysqlMigration(System.getenv()).run(args[1]);
        } catch (Exception exception) {
            // JDBC exceptions can contain SQL, account details or source values. Keep CLI errors redacted.
            System.err.println("Migration refused/failed (" + exception.getClass().getSimpleName()
                    + "). See the protected migration report; no runtime configuration was switched.");
            System.exit(1);
        }
    }

    public void run(String mode) throws Exception {
        if (!MODES.contains(mode)) throw new IllegalArgumentException("Invalid mode");
        reportFile = dPath(required("CLOUDNEXT_MIGRATION_REPORT"), false);
        // Every invocation gets a NEW report; never overwrite evidence from a prior invocation.
        Files.createFile(reportFile);
        report.put("mode", mode);
        report.put("startedAt", Instant.now().toString());
        report.put("schemaSha256", sha(schema.getBytes(StandardCharsets.UTF_8)));
        report.put("sourceTimeZone", sourceTimeZone.getTimeZone().getID());
        report.put("timestampPolicy", "UTC DATETIME(6), truncate nanoseconds to microseconds");
        report.put("status", "PRECHECKING");
        saveReport();
        try {
            Path source = dPath(required("CLOUDNEXT_MIGRATION_SOURCE"), true);
            Path storage = dPath(required("CLOUDNEXT_STORAGE_LOCAL_ROOT"), true);
            if (!source.toString().endsWith(".mv.db") || !Files.isRegularFile(source))
                throw new IllegalStateException("Source must be a cold H2 .mv.db backup");
            String sourceSha = fileSha(source);
            if (!sourceSha.equalsIgnoreCase(required("CLOUDNEXT_MIGRATION_SOURCE_SHA256")))
                throw new IllegalStateException("Cold source checksum mismatch");
            report.put("sourceSha256", sourceSha);
            try (Connection from = source(source); Connection to = target()) {
                checkSourceTables(from);
                checkServer(to);
                report.put("sourceTables", snapshot(from, true));
                report.put("sourceIntegrity", integrity(from));
                Map<String, Object> originalStorage = inventory(from, storage);
                report.put("storageBefore", originalStorage);
                if ("preview".equals(mode)) {
                    requireEmptyTarget(to);
                    report.put("status", "PREVIEWED");
                } else {
                    assertOwners(from);
                    if ("import".equals(mode)) {
                        importAll(from, to, storage, originalStorage);
                    } else {
                        boolean clear = "clear-sessions".equals(mode);
                        if (clear) acquireLock(to);
                        try {
                            checkTargetTables(to);
                            to.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                            to.setAutoCommit(false);
                            compare(from, to);
                            if (!Objects.equals(inventory(to, storage), originalStorage))
                                throw new IllegalStateException("Storage inventory changed");
                            if (clear) clearSessions(to);
                            else to.rollback(); // Read-only verification snapshot, no writes.
                            report.put("status", clear ? "CUTOVER_READY" : "VERIFIED");
                        } finally {
                            if (!to.getAutoCommit()) { to.rollback(); to.setAutoCommit(true); }
                            if (clear) releaseLock(to);
                        }
                    }
                }
            }
            if (!sourceSha.equals(fileSha(source))) throw new IllegalStateException("Source backup changed during migration");
            report.put("finishedAt", Instant.now().toString());
            saveReport();
            System.out.println("Migration " + report.get("status") + "; protected report written; application not started.");
        } catch (Exception exception) {
            report.put("status", "FAILED");
            report.put("errorType", exception.getClass().getSimpleName());
            // Only our own safe descriptions, never driver messages or row values.
            if (exception instanceof IllegalStateException || exception instanceof IllegalArgumentException)
                report.put("failure", exception.getMessage());
            if (exception instanceof SQLException) report.put("sqlState", ((SQLException) exception).getSQLState());
            saveReport();
            throw exception;
        }
    }

    private static void acquireLock(Connection connection) throws Exception {
        try (Statement sql = connection.createStatement(); ResultSet rs = sql.executeQuery("SELECT GET_LOCK('" + LOCK + "',0)")) {
            if (!rs.next() || rs.getInt(1) != 1) throw new IllegalStateException("Another migration holds the lock");
        }
    }

    private static void releaseLock(Connection connection) throws Exception {
        try (Statement sql = connection.createStatement()) { sql.execute("SELECT RELEASE_LOCK('" + LOCK + "')"); }
    }

    void importAll(Connection from, Connection to, Path storage, Map<String, Object> originalStorage) throws Exception {
        List<String> created = new ArrayList<>();
        boolean committed = false;
        try (Statement statement = to.createStatement()) {
            try (ResultSet lock = statement.executeQuery("SELECT GET_LOCK('" + LOCK + "',0)")) {
                if (!lock.next() || lock.getInt(1) != 1) throw new IllegalStateException("Another migration holds the lock");
            }
            try {
                requireEmptyTarget(to);
                for (Table table : tables) {
                    report.put("creatingTable", table.name);
                    report.put("createdTables", new ArrayList<>(created));
                    saveReport();
                    statement.execute(table.ddl);
                    created.add(table.name);
                }
                report.put("createdTables", new ArrayList<>(created));
                report.put("status", "IMPORTING");
                saveReport();
                to.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                to.setAutoCommit(false);
                for (Table table : tables) copyTable(from, to, table);
                compare(from, to);
                if (!Objects.equals(inventory(to, storage), originalStorage))
                    throw new IllegalStateException("Storage changed during import");
                to.commit();
                committed = true;
                report.put("databaseCommitted", true);
                report.put("status", "VERIFIED");
            } catch (Exception exception) {
                if (!to.getAutoCommit()) to.rollback();
                to.setAutoCommit(true);
                List<String> remaining = new ArrayList<>(created);
                // DDL is not transactional in MySQL. Remove ONLY empty tables created by THIS invocation.
                for (String table : created) {
                    if (count(to, table) == 0) {
                        statement.execute("DROP TABLE " + q(table));
                        remaining.remove(table);
                    }
                }
                report.put("remainingCreatedTables", remaining);
                report.put("dataRolledBack", !committed);
                throw exception;
            } finally {
                if (!to.getAutoCommit()) to.setAutoCommit(true);
                statement.execute("SELECT RELEASE_LOCK('" + LOCK + "')");
            }
        }
    }

    private Connection source(Path file) throws Exception {
        String base = file.toString().substring(0, file.toString().length() - ".mv.db".length()).replace('\\', '/');
        if (base.contains(";")) throw new IllegalArgumentException("Invalid source path");
        Connection connection = DriverManager.getConnection("jdbc:h2:file:" + base
                + ";IFEXISTS=TRUE;ACCESS_MODE_DATA=r;AUTO_SERVER=FALSE;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                env.getOrDefault("CLOUDNEXT_MIGRATION_SOURCE_USERNAME", "sa"), env.getOrDefault("CLOUDNEXT_MIGRATION_SOURCE_PASSWORD", ""));
        connection.setReadOnly(true);
        return connection;
    }

    private Connection target() throws Exception {
        String url = required("CLOUDNEXT_DB_URL");
        if (!url.startsWith("jdbc:mysql://")) throw new IllegalStateException("Target must be MySQL");
        URI parsed = URI.create(url.substring(5));
        String database = env.getOrDefault("CLOUDNEXT_MIGRATION_TARGET_DB", "qingshancloud");
        if (!Set.of("qingshancloud", "qingshancloud_test").contains(database)
                || !Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(parsed.getHost())
                || !('/' + database).equals(parsed.getPath()) || parsed.getUserInfo() != null || parsed.getFragment() != null)
            throw new IllegalStateException("Target must be the dedicated database through loopback/SSH tunnel");
        Properties settings = new Properties();
        settings.setProperty("user", required("CLOUDNEXT_DB_USERNAME"));
        settings.setProperty("password", required("CLOUDNEXT_DB_PASSWORD"));
        settings.setProperty("connectionTimeZone", "UTC");
        settings.setProperty("forceConnectionTimeZoneToSession", "true");
        settings.setProperty("characterEncoding", "UTF-8");
        settings.setProperty("connectionCollation", "utf8mb4_0900_bin");
        settings.setProperty("jdbcCompliantTruncation", "true");
        settings.setProperty("connectTimeout", "10000");
        Connection connection = DriverManager.getConnection(url, settings);
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT DATABASE(), VERSION(), @@server_uuid")) {
            if (!rs.next() || !database.equals(rs.getString(1)) || !rs.getString(2).startsWith("8.0."))
                throw new IllegalStateException("Target MySQL 8.0 identity mismatch");
            report.put("targetDatabase", rs.getString(1));
            report.put("targetVersion", rs.getString(2));
            report.put("targetServerUuid", rs.getString(3));
            return connection;
        } catch (Exception exception) { connection.close(); throw exception; }
    }

    private void checkServer(Connection connection) throws Exception {
        Map<String, String> variables = new TreeMap<>();
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(
                "SHOW VARIABLES WHERE Variable_name IN ('datadir','tmpdir','log_error','log_bin_basename',"
                + "'relay_log_basename','general_log_file','slow_query_log_file','innodb_data_home_dir',"
                + "'innodb_log_group_home_dir','innodb_temp_tablespaces_dir')")) {
            while (rs.next()) variables.put(rs.getString(1), rs.getString(2));
        }
        report.put("mysqlPaths", variables);
        if (!variables.getOrDefault("datadir", "").matches("(?i)^D:[/\\\\].*"))
            throw new IllegalStateException("MySQL datadir is not on D:");
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            for (String value : entry.getValue().split(";")) {
                if (value.isEmpty() || "stderr".equalsIgnoreCase(value)) continue;
                if (value.startsWith("\\\\") || value.startsWith("/") || value.matches("(?i)^[A-Z]:.*") && !value.matches("(?i)^D:[/\\\\].*"))
                    throw new IllegalStateException("MySQL path outside D: in variable " + entry.getKey());
                Path resolved = Path.of(variables.get("datadir")).resolve(value).normalize();
                dPath(resolved.toString(), Files.exists(resolved));
            }
        }
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SHOW GRANTS FOR CURRENT_USER()")) {
            String database = (String) report.get("targetDatabase");
            while (rs.next()) {
                String grant = rs.getString(1);
                if (grant.matches("(?i)^GRANT USAGE ON \\*\\.\\* TO .*$")) continue;
                if (!grant.contains(" ON `" + database + "`.* TO ") || grant.contains("WITH GRANT OPTION"))
                    throw new IllegalStateException("Use a dedicated schema-only account with no roles/global privileges");
            }
        }
    }

    private Set<String> tableNames(Connection connection) throws Exception {
        Set<String> names = new TreeSet<>();
        boolean mysql = connection.getMetaData().getDatabaseProductName().equals("MySQL");
        try (ResultSet rs = connection.getMetaData().getTables(mysql ? connection.getCatalog() : null,
                mysql ? null : connection.getSchema(), "%", new String[] { "TABLE", "BASE TABLE", "VIEW" })) {
            while (rs.next()) names.add(rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private void requireEmptyTarget(Connection connection) throws Exception {
        if (!tableNames(connection).isEmpty()) throw new IllegalStateException("Target contains tables/views; refusing to overwrite or resume blindly");
        for (String system : List.of("ROUTINES", "TRIGGERS", "EVENTS")) {
            String column = system.equals("ROUTINES") ? "ROUTINE_SCHEMA" : system.equals("TRIGGERS") ? "TRIGGER_SCHEMA" : "EVENT_SCHEMA";
            try (PreparedStatement sql = connection.prepareStatement("SELECT COUNT(*) FROM information_schema." + system + " WHERE " + column + "=?")) {
                sql.setString(1, connection.getCatalog());
                try (ResultSet rs = sql.executeQuery()) { rs.next(); if (rs.getLong(1) != 0) throw new IllegalStateException("Target contains unrelated database objects"); }
            }
        }
    }

    private void checkSourceTables(Connection connection) throws Exception {
        Set<String> expected = tables.stream().map(t -> t.name).collect(Collectors.toCollection(TreeSet::new));
        if (!tableNames(connection).equals(expected)) throw new IllegalStateException("Source table inventory differs from the 28-table baseline");
        checkColumns(connection);
    }

    private void checkTargetTables(Connection connection) throws Exception {
        if (!tableNames(connection).equals(tables.stream().map(t -> t.name).collect(Collectors.toSet())))
            throw new IllegalStateException("Target table inventory mismatch");
        checkColumns(connection);
        try (Statement sql = connection.createStatement(); ResultSet rs = sql.executeQuery(
                "SELECT ENGINE,TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")) {
            while (rs.next()) if (!"InnoDB".equals(rs.getString(1)) || !"utf8mb4_0900_bin".equals(rs.getString(2)))
                throw new IllegalStateException("Target table engine/collation mismatch");
        }
    }

    private void checkColumns(Connection connection) throws Exception {
        for (Table table : tables) {
            Set<String> actual = new TreeSet<>();
            try (Statement sql = connection.createStatement(); ResultSet rs = sql.executeQuery("SELECT * FROM " + q(table.name) + " WHERE 1=0")) {
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) actual.add(rs.getMetaData().getColumnName(i).toLowerCase(Locale.ROOT));
            }
            if (!actual.equals(table.columns.stream().map(c -> c.name).collect(Collectors.toSet())))
                throw new IllegalStateException("Column inventory mismatch: " + table.name);
        }
    }

    private void copyTable(Connection from, Connection to, Table table) throws Exception {
        String columns = table.columns.stream().map(c -> q(c.name)).collect(Collectors.joining(","));
        String placeholders = table.columns.stream().map(c -> "?").collect(Collectors.joining(","));
        try (Statement source = from.createStatement(); ResultSet rows = source.executeQuery("SELECT " + columns + " FROM " + q(table.name));
                PreparedStatement target = to.prepareStatement("INSERT INTO " + q(table.name) + " (" + columns + ") VALUES (" + placeholders + ")")) {
            long count = 0;
            while (rows.next()) {
                for (int i = 0; i < table.columns.size(); i++) {
                    Column column = table.columns.get(i);
                    Object value = read(rows, i + 1, column, true);
                    if (value == null) target.setNull(i + 1, column.jdbcType());
                    else if (value instanceof Instant) target.setTimestamp(i + 1, Timestamp.from((Instant) value), UTC);
                    else if (value instanceof Boolean) target.setBoolean(i + 1, (Boolean) value);
                    else if (value instanceof Long) target.setLong(i + 1, (Long) value);
                    else target.setString(i + 1, (String) value);
                }
                target.addBatch();
                if (++count % 200 == 0) target.executeBatch();
            }
            target.executeBatch();
            System.out.println("Copied " + table.name + ": " + count + " rows (not yet committed)");
        }
    }

    private Object read(ResultSet rs, int index, Column column, boolean fromSource) throws Exception {
        if (column.type.startsWith("DATETIME")) {
            Timestamp value = rs.getTimestamp(index, fromSource ? sourceTimeZone : UTC);
            if (value == null) return null;
            Instant instant = value.toInstant();
            if (fromSource && instant.getNano() % 1000 != 0) truncatedTimestamps++;
            return instant.truncatedTo(ChronoUnit.MICROS);
        }
        if (column.type.startsWith("BIT")) { boolean value = rs.getBoolean(index); return rs.wasNull() ? null : value; }
        if (column.type.equals("BIGINT") || column.type.equals("INT")) { long value = rs.getLong(index); return rs.wasNull() ? null : value; }
        return rs.getString(index);
    }

    private Map<String, Object> snapshot(Connection connection, boolean source) throws Exception {
        Map<String, Object> result = new TreeMap<>();
        long before = truncatedTimestamps;
        for (Table table : tables) {
            SortedMap<String, String> rows = new TreeMap<>();
            String cols = table.columns.stream().map(c -> q(c.name)).collect(Collectors.joining(","));
            try (Statement sql = connection.createStatement(); ResultSet rs = sql.executeQuery("SELECT " + cols + " FROM " + q(table.name))) {
                while (rs.next()) {
                    List<Object> values = new ArrayList<>();
                    for (int i = 0; i < table.columns.size(); i++) values.add(read(rs, i + 1, table.columns.get(i), source));
                    String id = Objects.toString(values.get(table.primaryIndex), null);
                    if (id == null || rows.put(id, canonicalDigest(values)) != null) throw new IllegalStateException("Duplicate or null primary key: " + table.name);
                }
            }
            result.put(table.name, Map.of("rows", rows.size(), "primaryKeysSha256", canonicalDigest(new ArrayList<>(rows.keySet())),
                    "contentSha256", canonicalDigest(new ArrayList<>(rows.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.toList())))));
        }
        if (source) report.put("sourceTimestampsTruncatedPerScan", truncatedTimestamps - before);
        return result;
    }

    void compare(Connection from, Connection to) throws Exception {
        checkTargetTables(to);
        Map<String, Object> source = snapshot(from, true), target = snapshot(to, false);
        report.put("sourceTables", source);
        report.put("targetTables", target);
        if (!source.equals(target)) throw new IllegalStateException("Row/primary-key/content digest mismatch");
        Map<String, Object> sourceIntegrity = integrity(from), targetIntegrity = integrity(to);
        report.put("sourceIntegrity", sourceIntegrity); report.put("targetIntegrity", targetIntegrity);
        if (!sourceIntegrity.equals(targetIntegrity)) throw new IllegalStateException("Business reference integrity differs");
        assertOwners(to);
    }

    private Map<String, Object> integrity(Connection connection) throws Exception {
        Map<String, Object> result = new TreeMap<>();
        result.put("missingParents", stringQuery(connection, "SELECT i.id FROM cloudnext_drive_item i LEFT JOIN cloudnext_drive_item p ON p.id=i.parent_id WHERE i.parent_id<>'' AND p.id IS NULL"));
        result.put("missingMemberWorkspaces", stringQuery(connection, "SELECT m.id FROM cloudnext_workspace_member m LEFT JOIN cloudnext_workspace w ON w.id=m.workspace_id WHERE w.id IS NULL"));
        result.put("missingShareItems", stringQuery(connection, "SELECT s.id FROM cloudnext_drive_share s LEFT JOIN cloudnext_drive_item i ON i.id=s.item_id WHERE i.id IS NULL"));
        result.put("missingVersionItems", stringQuery(connection, "SELECT v.id FROM cloudnext_drive_item_version v LEFT JOIN cloudnext_drive_item i ON i.id=v.item_id WHERE i.id IS NULL"));
        result.put("missingWorkspaceScopes", stringQuery(connection, "SELECT i.id FROM cloudnext_drive_item i LEFT JOIN cloudnext_workspace w ON w.id=i.scope_id WHERE i.scope_type='WORKSPACE' AND w.id IS NULL"));
        Map<String, Set<String>> subjects = new HashMap<>();
        subjects.put("PERSON", subjects(connection, "SELECT canonical_person_id FROM cloudnext_principal_binding UNION SELECT oa_id FROM cloudnext_org_person"));
        subjects.put("UNIT", subjects(connection, "SELECT canonical_unit_id FROM cloudnext_unit_binding UNION SELECT oa_id FROM cloudnext_org_unit"));
        subjects.put("IDENTITY", subjects(connection, "SELECT canonical_identity_id FROM cloudnext_identity_binding UNION SELECT oa_id FROM cloudnext_org_identity"));
        List<String[]> aliases = new ArrayList<>();
        try (Statement sql = connection.createStatement(); ResultSet rs = sql.executeQuery("SELECT subject_type,alias_key,canonical_key FROM cloudnext_canonical_alias")) {
            while (rs.next()) aliases.add(new String[] {rs.getString(1), rs.getString(2), rs.getString(3)});
        }
        boolean changed;
        do {
            changed = false;
            for (String[] alias : aliases) {
                Set<String> valid = subjects.get(alias[0]);
                if (valid != null && valid.contains(alias[2])) changed |= valid.add(alias[1]);
            }
        } while (changed);
        List<String> unresolved = new ArrayList<>();
        try (Statement sql = connection.createStatement(); ResultSet rs = sql.executeQuery("SELECT id,target_type,target_id FROM cloudnext_drive_share WHERE revoked=FALSE")) {
            while (rs.next()) if (!subjects.getOrDefault(rs.getString(2), Set.of()).contains(rs.getString(3))) unresolved.add(rs.getString(1));
        }
        Collections.sort(unresolved);
        result.put("unresolvedShareSubjects", unresolved);
        List<String> owners = new ArrayList<>();
        try (Statement sql = connection.createStatement(); ResultSet rs = sql.executeQuery("SELECT id,owner_person_oa_id FROM cloudnext_drive_item")) {
            while (rs.next()) if (!subjects.get("PERSON").contains(rs.getString(2))) owners.add(rs.getString(1));
        }
        Collections.sort(owners); result.put("unresolvedFileOwners", owners);
        List<String> identities = new ArrayList<>();
        try (Statement sql = connection.createStatement(); ResultSet rs = sql.executeQuery("SELECT id,canonical_person_id,canonical_unit_id FROM cloudnext_identity_binding")) {
            while (rs.next()) if (!subjects.get("PERSON").contains(rs.getString(2)) || !subjects.get("UNIT").contains(rs.getString(3))) identities.add(rs.getString(1));
        }
        Collections.sort(identities); result.put("unresolvedIdentityBindings", identities);
        result.put("fileParentCycles", parentCycles(connection, "SELECT id,parent_id FROM cloudnext_drive_item"));
        return result;
    }

    private List<String> parentCycles(Connection connection, String query) throws Exception {
        Map<String, String> parents = new HashMap<>();
        try (Statement sql = connection.createStatement(); ResultSet rs = sql.executeQuery(query)) {
            while (rs.next()) parents.put(rs.getString(1), rs.getString(2));
        }
        Set<String> cycles = new TreeSet<>(), checked = new HashSet<>();
        for (String id : parents.keySet()) {
            Set<String> visiting = new LinkedHashSet<>();
            String current = id;
            while (current != null && !current.isEmpty() && parents.containsKey(current) && !checked.contains(current)) {
                if (!visiting.add(current)) { cycles.addAll(visiting); break; }
                current = parents.get(current);
            }
            checked.addAll(visiting);
        }
        return new ArrayList<>(cycles);
    }

    private Set<String> subjects(Connection connection, String query) throws Exception {
        return new HashSet<>(stringQuery(connection, query));
    }

    private void assertOwners(Connection connection) throws Exception {
        List<String> missing = stringQuery(connection, "SELECT w.id FROM cloudnext_workspace w WHERE w.status='ACTIVE' AND NOT EXISTS ("
                + "SELECT 1 FROM cloudnext_workspace_member m JOIN cloudnext_principal_binding p ON p.canonical_person_id=m.person_oa_id "
                + "WHERE m.workspace_id=w.id AND m.role='OWNER' AND p.account_enabled=TRUE AND p.record_status='ACTIVE' "
                + "AND p.migration_decision<>'' AND p.migration_decision NOT IN ('PENDING','TOMBSTONE','ARCHIVE','REVOKED','TRANSFERRED'))");
        report.put("workspacesWithoutLoginOwner", missing);
        if (!missing.isEmpty()) throw new IllegalStateException("An active workspace has no login-enabled OWNER");
    }

    private List<String> stringQuery(Connection connection, String query) throws Exception {
        List<String> values = new ArrayList<>();
        try (Statement sql = connection.createStatement(); ResultSet rs = sql.executeQuery(query)) { while (rs.next()) values.add(rs.getString(1)); }
        Collections.sort(values);
        return values;
    }

    Map<String, Object> inventory(Connection connection, Path root) throws Exception {
        if (!Files.isDirectory(root)) throw new IllegalStateException("Storage root missing");
        Map<String, Long> referenced = new TreeMap<>();
        for (String table : List.of("cloudnext_drive_item", "cloudnext_drive_item_version")) {
            try (Statement sql = connection.createStatement(); ResultSet rs = sql.executeQuery("SELECT storage_key,size_bytes FROM " + q(table) + " WHERE storage_key<>''")) {
                while (rs.next()) {
                    String key = rs.getString(1);
                    Long previous = referenced.put(key, rs.getLong(2));
                    if (previous != null && previous.longValue() != rs.getLong(2)) throw new IllegalStateException("One storage key has conflicting sizes");
                }
            }
        }
        SortedMap<String, Object> files = new TreeMap<>();
        List<String> missing = new ArrayList<>(), sizeMismatches = new ArrayList<>();
        try (Stream<Path> entries = Files.walk(root)) {
            for (Path path : entries.collect(Collectors.toList())) {
                if (Files.isSymbolicLink(path) || !path.toRealPath().equals(path.toAbsolutePath().normalize())
                        || !path.toRealPath().startsWith(root.toRealPath()))
                    throw new IllegalStateException("Storage contains a link/reparse target outside its root");
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    String key = root.relativize(path).toString().replace('\\', '/');
                    files.put(key, Map.of("size", Files.size(path), "sha256", fileSha(path)));
                }
            }
        }
        for (Map.Entry<String, Long> entry : referenced.entrySet()) {
            Path path = root.resolve(entry.getKey()).normalize();
            if (!path.startsWith(root) || entry.getKey().contains("\\")) throw new IllegalStateException("Unsafe storage key");
            if (!files.containsKey(entry.getKey())) missing.add(entry.getKey());
            else if (Files.size(path) != entry.getValue()) sizeMismatches.add(entry.getKey());
        }
        List<String> unreferenced = files.keySet().stream().filter(k -> !referenced.containsKey(k)).collect(Collectors.toList());
        return Map.of("files", files, "referencedKeyCount", referenced.size(), "preexistingMissing", missing,
                "preexistingSizeMismatches", sizeMismatches, "unreferencedKeys", unreferenced);
    }

    void clearSessions(Connection connection) throws Exception {
        connection.setAutoCommit(false);
        try (Statement sql = connection.createStatement()) {
            Map<String, Long> cleared = new LinkedHashMap<>();
            cleared.put("authSessions", (long) sql.executeUpdate("DELETE FROM cloudnext_auth_session"));
            cleared.put("ssoBootstraps", (long) sql.executeUpdate("DELETE FROM cloudnext_sso_bootstrap"));
            try (PreparedStatement marker = connection.prepareStatement("INSERT INTO cloudnext_meta (meta_key,meta_value,updated_at) VALUES (?,?,?) "
                    + "ON DUPLICATE KEY UPDATE meta_value=VALUES(meta_value),updated_at=VALUES(updated_at)")) {
                marker.setString(1, "onlyoffice_ticket_not_before_epoch_ms");
                marker.setString(2, Long.toString(Instant.now().toEpochMilli()));
                marker.setString(3, Instant.now().toString()); marker.executeUpdate();
            }
            report.put("sessionsCleared", cleared);
            report.put("tablesAfterSessionClear", snapshot(connection, false));
            connection.commit();
        } catch (Exception exception) { connection.rollback(); throw exception; }
        finally { connection.setAutoCommit(true); }
    }

    private static long count(Connection connection, String table) throws Exception {
        try (Statement sql = connection.createStatement(); ResultSet rs = sql.executeQuery("SELECT COUNT(*) FROM " + q(table))) { rs.next(); return rs.getLong(1); }
    }

    static String canonicalDigest(List<?> values) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Object value : values) {
            digest.update((byte) (value == null ? 0 : 1));
            if (value != null) {
                byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
            }
        }
        return hex(digest.digest());
    }

    private void saveReport() throws Exception {
        if (reportFile == null) return; // Core methods can be tested without invoking the deployment CLI.
        try (OutputStream output = Files.newOutputStream(reportFile, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            JSON.writerWithDefaultPrettyPrinter().writeValue(output, report);
        }
    }

    private String required(String key) {
        String value = env.get(key);
        if (value == null || value.trim().isEmpty()) throw new IllegalStateException("Missing setting: " + key);
        return value;
    }

    static Path dPath(String value, boolean exists) throws Exception {
        if (!value.matches("(?i)^D:[/\\\\].*") || value.contains(";")) throw new IllegalStateException("Migration paths must be absolute paths on D:");
        Path path = Path.of(value).toAbsolutePath().normalize();
        Path current = exists ? path : path.getParent();
        if (current == null || !Files.exists(current)) throw new IllegalStateException("Migration directory does not exist");
        for (Path part = current; part != null; part = part.getParent()) {
            if (Files.isSymbolicLink(part) || !part.toRealPath().equals(part.toAbsolutePath().normalize()))
                throw new IllegalStateException("Migration paths must not traverse junctions/symbolic links");
        }
        if (!current.toRealPath().toString().matches("(?i)^D:[/\\\\].*")) throw new IllegalStateException("Resolved path is not on D:");
        return path;
    }

    private static String fileSha(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[1024 * 1024]; int length;
            while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
        }
        return hex(digest.digest());
    }
    private static String sha(byte[] value) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(value)); }
    private static String hex(byte[] value) { StringBuilder text = new StringBuilder(); for (byte b : value) text.append(String.format("%02x", b & 255)); return text.toString(); }
    static String q(String identifier) { if (!identifier.matches("[a-zA-Z_][a-zA-Z0-9_]*")) throw new IllegalArgumentException("Invalid SQL identifier"); return '`' + identifier + '`'; }

    static List<Table> parseSchema(String sql) {
        List<Table> result = new ArrayList<>();
        for (String ddl : sql.split(";")) {
            if (ddl.trim().isEmpty()) continue;
            Matcher name = Pattern.compile("^\\s*CREATE TABLE `([a-z_][a-z0-9_]*)` \\(").matcher(ddl);
            if (!name.find() || !ddl.contains("ENGINE=InnoDB") || !ddl.contains("COLLATE utf8mb4_0900_bin"))
                throw new IllegalStateException("Unsupported baseline statement");
            List<Column> columns = new ArrayList<>();
            Matcher fields = Pattern.compile("(?m)^\\s*`([a-z_][a-z0-9_]*)` (VARCHAR\\(\\d+\\)|LONGTEXT|BIT\\(1\\)|BIGINT|INT|DATETIME\\(6\\))").matcher(ddl);
            while (fields.find()) columns.add(new Column(fields.group(1), fields.group(2)));
            Matcher declared = Pattern.compile("(?m)^\\s*`[^`]+` ").matcher(ddl);
            int declaredCount = 0;
            while (declared.find()) declaredCount++;
            if (columns.size() != declaredCount) throw new IllegalStateException("Unsupported baseline column definition");
            Matcher key = Pattern.compile("PRIMARY KEY \\(`([a-z_][a-z0-9_]*)`\\)").matcher(ddl);
            if (!key.find()) throw new IllegalStateException("Missing primary key");
            String primary = key.group(1); int index = -1;
            for (int i = 0; i < columns.size(); i++) if (columns.get(i).name.equals(primary)) index = i;
            if (index < 0) throw new IllegalStateException("Invalid primary key");
            result.add(new Table(name.group(1), ddl.trim(), columns, index));
        }
        if (result.size() != 28) throw new IllegalStateException("Expected 28 business tables");
        return result;
    }
    static final class Table {
        final String name, ddl; final List<Column> columns; final int primaryIndex;
        Table(String name, String ddl, List<Column> columns, int primaryIndex) { this.name = name; this.ddl = ddl; this.columns = columns; this.primaryIndex = primaryIndex; }
    }
    static final class Column {
        final String name, type;
        Column(String name, String type) { this.name = name; this.type = type; }
        int jdbcType() { return type.startsWith("BIT") ? Types.BIT : type.startsWith("DATETIME") ? Types.TIMESTAMP : type.equals("BIGINT") ? Types.BIGINT : type.equals("INT") ? Types.INTEGER : type.equals("LONGTEXT") ? Types.LONGVARCHAR : Types.VARCHAR; }
    }
}
