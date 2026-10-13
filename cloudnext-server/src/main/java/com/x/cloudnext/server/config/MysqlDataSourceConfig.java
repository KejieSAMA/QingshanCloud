package com.x.cloudnext.server.config;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class MysqlDataSourceConfig {
    @Bean
    public DataSource dataSource(Environment environment) {
        String url = value(environment, "CLOUDNEXT_DB_URL", "CLOUDNEXT_LOCAL_DB_URL", "spring.datasource.url");
        MysqlDatabasePolicy.databaseName(url);
        String username = value(environment, "CLOUDNEXT_DB_USERNAME", "CLOUDNEXT_LOCAL_DB_USERNAME", "spring.datasource.username");
        String password = value(environment, "CLOUDNEXT_DB_PASSWORD", "CLOUDNEXT_LOCAL_DB_PASSWORD", "spring.datasource.password");
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalStateException("CloudNext MySQL username and nonempty password must be configured");
        }
        String driver = environment.getProperty("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        if (!"com.mysql.cj.jdbc.Driver".equals(driver)) {
            throw new IllegalStateException("CloudNext only permits the MySQL JDBC driver");
        }
        if (!"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto", "validate"))) {
            throw new IllegalStateException("Apply the versioned MySQL schema explicitly; CloudNext requires ddl-auto=validate");
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("CloudNextMySQL");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setInitializationFailTimeout(10000);
        config.addDataSourceProperty("connectionTimeZone", "UTC");
        config.addDataSourceProperty("forceConnectionTimeZoneToSession", "true");
        config.addDataSourceProperty("characterEncoding", "UTF-8");
        config.addDataSourceProperty("connectionCollation", "utf8mb4_0900_bin");
        config.addDataSourceProperty("jdbcCompliantTruncation", "true");
        return new HikariDataSource(config);
    }

    private static String value(Environment environment, String primary, String legacy, String spring) {
        if (environment.containsProperty(primary)) return environment.getProperty(primary);
        if (environment.containsProperty(legacy)) return environment.getProperty(legacy);
        return environment.getProperty(spring);
    }
}
