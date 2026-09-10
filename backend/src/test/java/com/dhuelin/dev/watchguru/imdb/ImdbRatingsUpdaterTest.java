package com.dhuelin.dev.watchguru.imdb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The batch UPDATE, which builds a statement whose length depends on the batch.
 */
class ImdbRatingsUpdaterTest {

    private JdbcTemplate jdbc;
    private ImdbRatingsUpdater updater;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        updater = new ImdbRatingsUpdater(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(2);
    }

    private static ImdbRating rating(String id, String value, int votes) {
        return new ImdbRating(id, new BigDecimal(value), votes);
    }

    @Test
    @DisplayName("a batch becomes one statement, not one per row")
    void oneStatementPerBatch() {
        updater.applyBatch(List.of(
                rating("tt0000001", "9.5", 100),
                rating("tt0000002", "8.5", 200),
                rating("tt0000003", "7.5", 300)));

        // Row-at-a-time over 1.5 million rows is 1.5 million round trips.
        verify(jdbc).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("every value is bound as a parameter, never interpolated")
    void valuesAreBound() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);

        updater.applyBatch(List.of(
                rating("tt0000001", "9.5", 100),
                rating("tt0000002", "8.5", 200)));

        verify(jdbc).update(sql.capture(), args.capture());

        // Three placeholders per row, three bound values per row, and no row
        // data anywhere in the statement text.
        assertThat(args.getValue()).hasSize(6);
        assertThat(args.getValue()).containsExactly(
                "tt0000001", new BigDecimal("9.5"), 100,
                "tt0000002", new BigDecimal("8.5"), 200);
        assertThat(sql.getValue()).doesNotContain("tt0000001");
    }

    @Test
    @DisplayName("the statement only touches titles already in the catalog")
    void onlyUpdatesExistingTitles() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        updater.applyBatch(List.of(rating("tt0000001", "9.5", 100)));
        verify(jdbc).update(sql.capture(), any(Object[].class));

        // An UPDATE joined on imdb_id: enrichment, never insertion. IMDb
        // publishes millions of titles this catalog has no use for.
        assertThat(sql.getValue()).startsWith("UPDATE title");
        assertThat(sql.getValue()).contains("title.imdb_id = incoming.imdb_id");
        assertThat(sql.getValue()).doesNotContainIgnoringCase("insert");
    }

    @Test
    @DisplayName("rows whose values have not changed are left alone")
    void skipsUnchangedRows() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        updater.applyBatch(List.of(rating("tt0000001", "9.5", 100)));
        verify(jdbc).update(sql.capture(), any(Object[].class));

        // IMDb republishes the whole file daily but most rows do not move.
        // Without this guard every run rewrites the entire catalog and bumps
        // updated_at on titles nothing has changed about.
        assertThat(sql.getValue()).contains("IS DISTINCT FROM");
    }

    @Test
    @DisplayName("an empty batch does not reach the database")
    void emptyBatchIsANoOp() {
        assertThat(updater.applyBatch(List.of())).isZero();
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("the number of changed rows is returned")
    void returnsUpdatedCount() {
        assertThat(updater.applyBatch(List.of(rating("tt0000001", "9.5", 100)))).isEqualTo(2);
    }
}
