package com.dhuelin.dev.watchguru.imdb;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Applies a batch of parsed ratings to the titles we already hold.
 *
 * <p>Enrichment, not import: rows for titles absent from the catalog are
 * ignored rather than inserted. IMDb publishes ratings for millions of titles
 * and this application has no use for one it has never heard of.
 *
 * <p>One statement per batch rather than one per row. A row-at-a-time update
 * over 1.5 million rows means 1.5 million round trips; joining against a VALUES
 * list turns each batch into a single indexed operation against
 * {@code uq_title_imdb}.
 */
@Component
public class ImdbRatingsUpdater {

    private final JdbcTemplate jdbc;

    public ImdbRatingsUpdater(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @return how many catalog rows were actually changed
     */
    @Transactional
    public int applyBatch(List<ImdbRating> batch) {
        if (batch.isEmpty()) {
            return 0;
        }

        // Placeholders are generated; the values themselves are always bound,
        // never interpolated.
        String values = batch.stream()
                .map(r -> "(?, CAST(? AS NUMERIC(3,1)), CAST(? AS INTEGER))")
                .collect(Collectors.joining(", "));

        String sql = """
                UPDATE title
                   SET imdb_rating = incoming.rating,
                       imdb_vote_count = incoming.votes,
                       updated_at = now()
                  FROM (VALUES %s) AS incoming (imdb_id, rating, votes)
                 WHERE title.imdb_id = incoming.imdb_id
                   AND (title.imdb_rating IS DISTINCT FROM incoming.rating
                        OR title.imdb_vote_count IS DISTINCT FROM incoming.votes)
                """.formatted(values);

        Object[] args = new Object[batch.size() * 3];
        int i = 0;
        for (ImdbRating rating : batch) {
            args[i++] = rating.imdbId();
            args[i++] = rating.rating();
            args[i++] = rating.voteCount();
        }

        // The IS DISTINCT FROM guard matters more than it looks: IMDb
        // republishes the whole file daily but most rows do not move, so
        // without it every run would rewrite the entire catalog and bump
        // updated_at on titles nothing has changed about.
        return jdbc.update(sql, args);
    }
}
