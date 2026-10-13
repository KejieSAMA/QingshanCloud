package com.x.cloudnext.server.testing;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.env.MapPropertySource;

public class MysqlTestDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        try { MysqlTestDatabase.initialize(); }
        catch (Exception exception) { throw new IllegalStateException("Isolated MySQL test initialization failed", exception); }
        // Do not use utilities that log the full inline property array (including passwords).
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("CLOUDNEXT_DB_URL", MysqlTestDatabase.url());
        settings.put("CLOUDNEXT_DB_USERNAME", MysqlTestDatabase.required("CLOUDNEXT_TEST_DB_USERNAME"));
        settings.put("CLOUDNEXT_DB_PASSWORD", MysqlTestDatabase.required("CLOUDNEXT_TEST_DB_PASSWORD"));
        settings.put("spring.datasource.url", MysqlTestDatabase.url());
        settings.put("spring.datasource.username", MysqlTestDatabase.required("CLOUDNEXT_TEST_DB_USERNAME"));
        settings.put("spring.datasource.password", MysqlTestDatabase.required("CLOUDNEXT_TEST_DB_PASSWORD"));
        settings.put("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        settings.put("spring.datasource.hikari.maximum-pool-size", "5");
        settings.put("spring.datasource.hikari.minimum-idle", "0");
        settings.put("spring.jpa.hibernate.ddl-auto", "validate");
        settings.put("spring.jpa.properties.hibernate.jdbc.time_zone", "UTC");
        settings.put("spring.jpa.database-platform", "org.hibernate.dialect.MySQL8Dialect");
        settings.put("cloudnext.scheduling.enabled", "false");
        settings.put("cloudnext.org-sync.cron", "-");
        settings.put("cloudnext.auth.form-base-url", "");
        settings.put("cloudnext.auth.form-shadow-sync-enabled", "false");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolatedMysqlTest", settings));
    }
}
