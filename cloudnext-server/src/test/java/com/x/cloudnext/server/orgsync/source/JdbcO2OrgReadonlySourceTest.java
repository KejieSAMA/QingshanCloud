package com.x.cloudnext.server.orgsync.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.x.cloudnext.server.testing.MysqlTestDatabase;

import com.x.cloudnext.server.config.CloudNextProperties;
import com.x.cloudnext.server.orgsync.model.OrgMembershipSourceRecord;

class JdbcO2OrgReadonlySourceTest {
    private static final List<String> FIXTURE_TABLES = List.of("ORG_PERSON", "ORG_ROLE_personList", "ORG_ROLE_groupList",
            "ORG_GROUP_personList", "ORG_GROUP_groupList", "ORG_GROUP_unitList", "ORG_GROUP_identityList");

    @BeforeEach
    void prepareFixture() throws Exception {
        MysqlTestDatabase.initialize();
        removeFixture();
    }

    @AfterEach
    void removeFixture() throws Exception {
        try (java.sql.Connection connection = MysqlTestDatabase.connect(); java.sql.Statement sql = connection.createStatement()) {
            for (String table : FIXTURE_TABLES) sql.execute("DROP TABLE IF EXISTS `" + table + "`");
        }
    }

    @Test
    void personExportFailsWhenEmployeeColumnIsMissing() {
        TestDatabase database = database();
        database.jdbc.execute("create table ORG_PERSON (xid varchar(64), xname varchar(255), xunique varchar(255), "
                + "xdistinguishedName varchar(255), xmobile varchar(64), xmail varchar(64), xstatus varchar(32), "
                + "xupdateTime varchar(64), xsequence varchar(128))");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> database.source.listPersons());

        assertTrue(failure.getMessage().contains("ORG_PERSON.xemployee"));
    }

    @Test
    void membershipExportFailsWhenARequiredTableIsMissing() {
        TestDatabase database = database();
        createMembershipTable(database.jdbc, "ORG_ROLE_personList", "xrole", "xpersonList");
        createMembershipTable(database.jdbc, "ORG_ROLE_groupList", "xrole", "xgroupList");
        createMembershipTable(database.jdbc, "ORG_GROUP_personList", "xgroup", "xpersonList");
        createMembershipTable(database.jdbc, "ORG_GROUP_groupList", "xgroup", "xgroupList");
        createMembershipTable(database.jdbc, "ORG_GROUP_unitList", "xgroup", "xunitList");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> database.source.listMemberships());

        assertTrue(failure.getMessage().contains("ORG_GROUP_identityList"));
    }

    @Test
    void membershipExportFailsWhenRequiredColumnsAreMissing() {
        TestDatabase database = database();
        database.jdbc.execute("create table ORG_ROLE_personList (xrole varchar(64), unexpected varchar(64))");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> database.source.listMemberships());

        assertTrue(failure.getMessage().contains("required O2 membership columns"));
    }

    @Test
    void membershipExportFailsInsteadOfGuessingAnUnknownSourceColumn() {
        TestDatabase database = database();
        database.jdbc.execute("create table ORG_ROLE_personList (unexpected varchar(64), xpersonList varchar(64))");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> database.source.listMemberships());

        assertTrue(failure.getMessage().contains("required O2 membership columns"));
    }

    @Test
    void membershipUsesUpdateTimeWhenSequenceIsBlank() {
        TestDatabase database = database();
        database.jdbc.execute("create table ORG_ROLE_personList (xrole varchar(64), xpersonList varchar(64), "
                + "xsequence varchar(64), xupdateTime varchar(64))");
        database.jdbc.update("insert into ORG_ROLE_personList "
                + "(xrole, xpersonList, xsequence, xupdateTime) values (?, ?, ?, ?)",
                "role-1", "person-1", null, "2026-07-20T12:34:56Z");
        createMembershipTable(database.jdbc, "ORG_ROLE_groupList", "xrole", "xgroupList");
        createMembershipTable(database.jdbc, "ORG_GROUP_personList", "xgroup", "xpersonList");
        createMembershipTable(database.jdbc, "ORG_GROUP_groupList", "xgroup", "xgroupList");
        createMembershipTable(database.jdbc, "ORG_GROUP_unitList", "xgroup", "xunitList");
        createMembershipTable(database.jdbc, "ORG_GROUP_identityList", "xgroup", "xidentityList");

        List<OrgMembershipSourceRecord> memberships = database.source.listMemberships();

        assertEquals("2026-07-20T12:34:56Z", memberships.get(0).getSyncVersion());
    }

    @Test
    void membershipWithoutSourceVersionUsesStableRelationshipHash() {
        TestDatabase database = database();
        createMembershipTable(database.jdbc, "ORG_ROLE_personList", "xrole", "xpersonList");
        createMembershipTable(database.jdbc, "ORG_ROLE_groupList", "xrole", "xgroupList");
        createMembershipTable(database.jdbc, "ORG_GROUP_personList", "xgroup", "xpersonList");
        createMembershipTable(database.jdbc, "ORG_GROUP_groupList", "xgroup", "xgroupList");
        createMembershipTable(database.jdbc, "ORG_GROUP_unitList", "xgroup", "xunitList");
        createMembershipTable(database.jdbc, "ORG_GROUP_identityList", "xgroup", "xidentityList");

        List<OrgMembershipSourceRecord> first = database.source.listMemberships();
        List<OrgMembershipSourceRecord> second = database.source.listMemberships();

        assertEquals(6, first.size());
        assertEquals(first.stream().map(OrgMembershipSourceRecord::getSyncVersion).collect(Collectors.toList()),
                second.stream().map(OrgMembershipSourceRecord::getSyncVersion).collect(Collectors.toList()));
        assertTrue(first.stream().allMatch(item -> item.getSyncVersion().matches("[0-9a-f]{64}")));
    }

    @Test
    void membershipRecognizesProductionO2OwnerColumns() {
        TestDatabase database = database();
        createMembershipTable(database.jdbc, "ORG_ROLE_personList", "ROLE_XID", "xpersonList");
        createMembershipTable(database.jdbc, "ORG_ROLE_groupList", "ROLE_XID", "xgroupList");
        createMembershipTable(database.jdbc, "ORG_GROUP_personList", "GROUP_XID", "xpersonList");
        createMembershipTable(database.jdbc, "ORG_GROUP_groupList", "GROUP_XID", "xgroupList");
        createMembershipTable(database.jdbc, "ORG_GROUP_unitList", "GROUP_XID", "xunitList");
        createMembershipTable(database.jdbc, "ORG_GROUP_identityList", "GROUP_XID", "xidentityList");

        List<OrgMembershipSourceRecord> memberships = database.source.listMemberships();

        assertEquals(6, memberships.size());
        assertTrue(memberships.stream().anyMatch(item -> "role".equals(item.getSourceType())
                && "source-ORG_ROLE_personList".equals(item.getSourceId())));
        assertTrue(memberships.stream().anyMatch(item -> "group".equals(item.getSourceType())
                && "source-ORG_GROUP_identityList".equals(item.getSourceId())));
    }

    private void createMembershipTable(JdbcTemplate jdbc, String table, String sourceColumn,
            String targetColumn) {
        jdbc.execute("create table " + table + " (" + sourceColumn + " varchar(64), " + targetColumn
                + " varchar(64))");
        jdbc.update("insert into " + table + " (" + sourceColumn + ", " + targetColumn + ") values (?, ?)",
                "source-" + table, "target-" + table);
    }

    private TestDatabase database() {
        String url = MysqlTestDatabase.url();
        CloudNextProperties properties = new CloudNextProperties();
        properties.getReadonlyJdbc().setUrl(url);
        properties.getReadonlyJdbc().setUsername(MysqlTestDatabase.required("CLOUDNEXT_TEST_DB_USERNAME"));
        properties.getReadonlyJdbc().setPassword(MysqlTestDatabase.required("CLOUDNEXT_TEST_DB_PASSWORD"));
        properties.getReadonlyJdbc().setDriverClassName("com.mysql.cj.jdbc.Driver");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, properties.getReadonlyJdbc().getUsername(), properties.getReadonlyJdbc().getPassword());
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        return new TestDatabase(new JdbcTemplate(dataSource), new JdbcO2OrgReadonlySource(properties));
    }

    private static class TestDatabase {
        private final JdbcTemplate jdbc;
        private final JdbcO2OrgReadonlySource source;

        private TestDatabase(JdbcTemplate jdbc, JdbcO2OrgReadonlySource source) {
            this.jdbc = jdbc;
            this.source = source;
        }
    }
}
