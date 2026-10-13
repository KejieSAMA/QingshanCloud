package com.x.cloudnext.migration;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MysqlMigrationFormatTest {
    @Test void baselineContainsEveryTableAndColumn() throws Exception {
        String sql;
        try (java.io.InputStream in = getClass().getResourceAsStream("/db/mysql/V001__cloudnext.sql")) {
            assertNotNull(in); sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^--[^\\n]*", "");
        }
        List<MysqlMigration.Table> tables = MysqlMigration.parseSchema(sql);
        assertEquals(28, tables.size());
        MysqlMigration.Table audit = tables.stream().filter(t -> t.name.equals("cloudnext_audit_log")).findFirst().orElseThrow();
        assertTrue(audit.columns.stream().anyMatch(c -> c.name.equals("payload") && c.type.equals("LONGTEXT")));
        assertTrue(audit.columns.stream().anyMatch(c -> c.name.equals("created_at") && c.type.equals("DATETIME(6)")));
        assertEquals("id", audit.columns.get(audit.primaryIndex).name);
    }

    @Test void canonicalDigestDistinguishesNullEmptyUnicodeAndBoundaries() throws Exception {
        assertNotEquals(MysqlMigration.canonicalDigest(Arrays.asList((String) null)), MysqlMigration.canonicalDigest(List.of("")));
        assertNotEquals(MysqlMigration.canonicalDigest(List.of("a", "bc")), MysqlMigration.canonicalDigest(List.of("ab", "c")));
        assertNotEquals(MysqlMigration.canonicalDigest(List.of("ID")), MysqlMigration.canonicalDigest(List.of("id")));
        assertEquals(MysqlMigration.canonicalDigest(List.of("中文📁")), MysqlMigration.canonicalDigest(List.of("中文📁")));
    }

    @Test void refusesUnsafeIdentifiersAndNonDPaths() {
        assertThrows(IllegalArgumentException.class, () -> MysqlMigration.q("id;DROP TABLE something"));
        assertThrows(IllegalStateException.class, () -> MysqlMigration.dPath("C:/tmp/source.mv.db", true));
        assertThrows(IllegalStateException.class, () -> MysqlMigration.dPath("D:relative.mv.db", true));
        assertThrows(IllegalStateException.class, () -> MysqlMigration.dPath("\\\\server\\share\\source.mv.db", true));
    }
}
