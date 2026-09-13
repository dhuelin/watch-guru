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
        var tables = jdbcTemplate.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public' and table_type = 'BASE TABLE'
                order by table_name
                """, String.class);

        // Named rather than counted: when a migration lands, a failure that
        // says which table appeared is worth considerably more than one saying
        // 15 is not 14.
        assertThat(tables).containsExactly(
                "app_user",
                "availability_check",
                "device_token",
                "episode",
                "episode_watch",
                "flyway_schema_history",
                "genre",
                "imdb_import_run",
                "linked_streaming_account",
                "notification_delivery",
                "notification_preference",
                "oauth_state",
                "refresh_token",
                "season",
                "streaming_credential",
                "streaming_service",
                "sync_run",
                "title",
                "title_availability",
                "title_genre",
                "watch_event",
                "watchlist_item");
    }

    @Test
    void authColumnsFromV3Exist() {
        var columns = jdbcTemplate.queryForList("""
                select column_name from information_schema.columns
                where table_schema = 'public' and table_name = 'app_user'
                  and column_name in ('auth_subject', 'auth_issuer', 'email_verified', 'last_login_at')
                order by column_name
                """, String.class);

        assertThat(columns).containsExactly(
                "auth_issuer", "auth_subject", "email_verified", "last_login_at");
    }

    @Test
    void notificationColumnsFromV6Exist() {
        // The global off switch lives on the user rather than in the
        // preference table, so it is the one part of V6 a column check catches.
        var columns = jdbcTemplate.queryForList("""
                select column_name from information_schema.columns
                where table_schema = 'public' and table_name = 'app_user'
                  and column_name = 'notifications_enabled'
                """, String.class);

        assertThat(columns).containsExactly("notifications_enabled");
    }

    @Test
    void plexColumnsFromV8Exist() {
        var columns = jdbcTemplate.queryForList("""
                select column_name from information_schema.columns
                where table_schema = 'public' and table_name = 'linked_streaming_account'
                  and column_name in ('webhook_token_hash', 'webhook_token_issued_at')
                order by column_name
                """, String.class);

        assertThat(columns).containsExactly("webhook_token_hash", "webhook_token_issued_at");
    }

    /**
     * Only services a user can actually connect are marked as syncable.
     *
     * <p>V2 claimed Netflix and Disney+ could be; V8 corrected it. Neither
     * offers any public API for viewing activity, and a flag saying otherwise
     * puts a dead "Connect" button in front of everybody. Plex and Trakt can,
     * which is what #39 built.
     */
    @Test
    void onlyServicesThatCanActuallySyncAreMarkedSyncable() {
        var slugs = jdbcTemplate.queryForList(
                "select slug from streaming_service where supports_sync = true order by slug", String.class);

        assertThat(slugs).containsExactly("plex", "trakt");
    }
}
