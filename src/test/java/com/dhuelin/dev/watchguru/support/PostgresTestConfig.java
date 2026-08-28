package com.dhuelin.dev.watchguru.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real Postgres for integration tests.
 *
 * <p>Tests run against Postgres rather than an in-memory database on purpose:
 * the Flyway migrations use Postgres-specific features (partial unique indexes,
 * {@code at time zone}), and {@code ddl-auto: validate} only proves the entity
 * mappings match the migrations if the migrations actually ran.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }
}
