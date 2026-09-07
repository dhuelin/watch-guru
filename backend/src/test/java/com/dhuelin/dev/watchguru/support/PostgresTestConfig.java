package com.dhuelin.dev.watchguru.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real Postgres for integration tests.
 *
 * <p>Tests run against Postgres rather than an in-memory database on purpose:
 * the Flyway migrations use Postgres-specific features (partial unique indexes,
 * {@code IS DISTINCT FROM}, {@code at time zone}), and {@code ddl-auto: validate}
 * only proves the entity mappings match the migrations if the migrations
 * actually ran.
 *
 * <p>Testcontainers by default, which is what CI uses. Where Docker is
 * unavailable -- a restricted network that cannot pull images, most obviously --
 * point the suite at an existing database instead:
 *
 * <pre>
 * export WATCH_GURU_TEST_USE_TESTCONTAINERS=false
 * export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/watchguru
 * export SPRING_DATASOURCE_USERNAME=watchguru
 * export SPRING_DATASOURCE_PASSWORD=watchguru
 * ./mvnw test
 * </pre>
 *
 * <p>That database is migrated and written to by the tests, so it must be a
 * throwaway.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfig {

    @Bean
    @ServiceConnection
    @ConditionalOnProperty(name = "watch-guru.test.use-testcontainers",
            havingValue = "true", matchIfMissing = true)
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }
}
