package com.x.cloudnext.server.config;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class MysqlConfigurationTest {
    @Test void rejectsMissingEmbeddedAndCredentialBearingUrls() {
        for (String url : new String[] { "", "jdbc:h2:mem:forbidden", "jdbc:sqlite:forbidden", "jdbc:mysql://host/",
                "jdbc:mysql://user:password@host/qingshancloud", "jdbc:mysql://host/qingshancloud/other" })
            assertThrows(IllegalStateException.class, () -> MysqlDatabasePolicy.databaseName(url));
        assertThrows(IllegalStateException.class, () -> MysqlDatabasePolicy.databaseName(null));
        assertEquals("qingshancloud", MysqlDatabasePolicy.databaseName("jdbc:mysql://localhost:3307/qingshancloud"));
    }

    @Test void explicitNewSettingWinsEvenWhenInvalidOrBlank() {
        MockEnvironment env = new MockEnvironment().withProperty("CLOUDNEXT_DB_URL", "")
                .withProperty("CLOUDNEXT_LOCAL_DB_URL", "jdbc:mysql://localhost/qingshancloud");
        assertThrows(IllegalStateException.class, () -> new MysqlDataSourceConfig().dataSource(env));
    }

    @Test void refusesMissingPasswordWrongDriverAndAutomaticDdlBeforeConnecting() {
        MockEnvironment env = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:mysql://localhost/qingshancloud")
                .withProperty("spring.datasource.username", "test");
        assertThrows(IllegalStateException.class, () -> new MysqlDataSourceConfig().dataSource(env));
        env.withProperty("spring.datasource.password", "test-only").withProperty("spring.datasource.driver-class-name", "org.h2.Driver");
        assertThrows(IllegalStateException.class, () -> new MysqlDataSourceConfig().dataSource(env));
        env.withProperty("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver").withProperty("spring.jpa.hibernate.ddl-auto", "update");
        assertThrows(IllegalStateException.class, () -> new MysqlDataSourceConfig().dataSource(env));
    }

    @Test void timestampsTruncateInsteadOfRoundingAndNullsRoundTrip() {
        InstantMicrosecondConverter converter = new InstantMicrosecondConverter();
        Instant value = Instant.parse("2026-10-13T23:59:59.999999999Z");
        Timestamp timestamp = converter.convertToDatabaseColumn(value);
        assertEquals(Instant.parse("2026-10-13T23:59:59.999999Z"), timestamp.toInstant());
        assertEquals(timestamp.toInstant(), converter.convertToEntityAttribute(timestamp));
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }
}
