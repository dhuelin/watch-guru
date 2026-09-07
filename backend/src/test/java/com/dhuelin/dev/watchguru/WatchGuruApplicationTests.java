package com.dhuelin.dev.watchguru;

import com.dhuelin.dev.watchguru.support.PostgresTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole application against Postgres.
 *
 * <p>This is the guard on the schema: Flyway applies the migrations and
 * Hibernate's {@code validate} then checks every entity mapping against the
 * resulting tables, so a mismatch between an entity and a migration fails here
 * rather than at runtime.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestConfig.class)
class WatchGuruApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAndSchemaMatchesEntities() {
        Integer tables = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_type = 'BASE TABLE'
                """, Integer.class);

        // 13 domain tables plus flyway_schema_history.
        assertThat(tables).isEqualTo(14);
    }

    @Test
    void seedMigrationMarksServicesThatSupportSync() {
        var slugs = jdbcTemplate.queryForList(
                "select slug from streaming_service where supports_sync = true order by slug", String.class);

        assertThat(slugs).containsExactly("disney-plus", "netflix");
    }
}
