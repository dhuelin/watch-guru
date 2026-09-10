package com.dhuelin.dev.watchguru.imdb;

import com.dhuelin.dev.watchguru.support.PostgresTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The batch UPDATE against a real database.
 *
 * <p>{@link ImdbRatingsUpdaterTest} covers the statement's shape with a mocked
 * {@code JdbcTemplate}, which proves what SQL is produced but not that Postgres
 * accepts it or that it does the right thing. This is the riskiest part of the
 * import -- a hand-built statement joining against a VALUES list, with casts
 * and a null-safe comparison -- so it is exercised for real.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestConfig.class)
class ImdbRatingsUpdaterIntegrationTest {

    @Autowired
    private ImdbRatingsUpdater updater;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("delete from title where tmdb_id in (900001, 900002, 900003)");
        jdbc.update("""
                insert into title (tmdb_id, title_type, imdb_id, primary_title,
                                   imdb_rating, imdb_vote_count)
                values (900001, 'TV_SERIES', 'tt90000001', 'Already Rated', 9.5, 2143567),
                       (900002, 'TV_SERIES', 'tt90000002', 'Never Rated', null, null),
                       (900003, 'MOVIE',     'tt90000003', 'Absent From Batch', null, null)
                """);
    }

    private BigDecimal ratingOf(String imdbId) {
        return jdbc.queryForObject(
                "select imdb_rating from title where imdb_id = ?", BigDecimal.class, imdbId);
    }

    @Test
    @DisplayName("a title with no rating gets one")
    void fillsAMissingRating() {
        int updated = updater.applyBatch(List.of(
                new ImdbRating("tt90000002", new BigDecimal("9.2"), 2298000)));

        assertThat(updated).isEqualTo(1);
        assertThat(ratingOf("tt90000002")).isEqualByComparingTo("9.2");
    }

    @Test
    @DisplayName("a row whose values have not changed is not rewritten")
    void skipsUnchangedRows() {
        // IMDb republishes the whole file daily while most ratings stay put.
        // Without the IS DISTINCT FROM guard every nightly run would rewrite
        // the entire catalog and bump updated_at on titles nothing changed
        // about. This asserts the guard works against real null-handling.
        int updated = updater.applyBatch(List.of(
                new ImdbRating("tt90000001", new BigDecimal("9.5"), 2143567)));

        assertThat(updated).isZero();
    }

    @Test
    @DisplayName("a changed vote count alone is enough to update")
    void updatesWhenOnlyVotesMove() {
        int updated = updater.applyBatch(List.of(
                new ImdbRating("tt90000001", new BigDecimal("9.5"), 2143999)));

        assertThat(updated).isEqualTo(1);
    }

    @Test
    @DisplayName("a rating for a title we do not hold is ignored, never inserted")
    void ignoresUnknownTitles() {
        long before = jdbc.queryForObject("select count(*) from title", Long.class);

        int updated = updater.applyBatch(List.of(
                new ImdbRating("tt99999999", new BigDecimal("5.0"), 10)));

        // Enrichment, not import. IMDb publishes millions of titles this
        // catalog has no use for.
        assertThat(updated).isZero();
        assertThat(jdbc.queryForObject("select count(*) from title", Long.class)).isEqualTo(before);
    }

    @Test
    @DisplayName("titles absent from the batch are left alone")
    void leavesUnrelatedTitlesUntouched() {
        updater.applyBatch(List.of(new ImdbRating("tt90000002", new BigDecimal("9.2"), 2298000)));

        assertThat(ratingOf("tt90000003")).isNull();
    }

    @Test
    @DisplayName("a mixed batch applies only the rows that need it")
    void appliesOnlyWhatChanged() {
        int updated = updater.applyBatch(List.of(
                new ImdbRating("tt90000001", new BigDecimal("9.5"), 2143567),  // unchanged
                new ImdbRating("tt90000002", new BigDecimal("9.2"), 2298000),  // new
                new ImdbRating("tt99999999", new BigDecimal("5.0"), 10)));     // not ours

        assertThat(updated).isEqualTo(1);
        assertThat(ratingOf("tt90000002")).isEqualByComparingTo("9.2");
    }

    @Test
    @DisplayName("the largest realistic vote count fits the column")
    void handlesLargeVoteCounts() {
        // IMDb's most-voted titles are in the millions; the column is INTEGER,
        // so this confirms the ceiling is nowhere near.
        int updated = updater.applyBatch(List.of(
                new ImdbRating("tt90000002", new BigDecimal("9.9"), 30_000_000)));

        assertThat(updated).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select imdb_vote_count from title where imdb_id = 'tt90000002'", Integer.class))
                .isEqualTo(30_000_000);
    }
}
