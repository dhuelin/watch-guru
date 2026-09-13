package com.dhuelin.dev.watchguru.streaming.trakt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/** What Trakt sends back, in the fields this service reads. */
public final class TraktResponses {

    private TraktResponses() {
    }

    /**
     * An access token and the refresh token that renews it.
     *
     * @param expiresIn seconds, as OAuth defines it. Turned into an instant on
     *                  arrival: an expiry stored as a duration is only
     *                  meaningful next to the moment it was issued, and that
     *                  moment is exactly what gets lost in a database
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Token(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("created_at") long createdAt,
            String scope,
            @JsonProperty("token_type") String tokenType
    ) {
    }

    /**
     * One entry in somebody's watched history.
     *
     * @param id       Trakt's own id for this viewing, unique for ever and
     *                 across rewatches. It is what makes a repeated sync a
     *                 no-op, with no guessing about dates
     * @param action   {@code scrobble}, {@code checkin} or {@code watch}. All
     *                 three mean it was watched; they differ only in how it
     *                 was recorded
     * @param type     {@code movie} or {@code episode}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HistoryItem(
            long id,
            @JsonProperty("watched_at") Instant watchedAt,
            String action,
            String type,
            Movie movie,
            Episode episode,
            Show show
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Movie(String title, Integer year, Ids ids) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Episode(Integer season, Integer number, String title, Ids ids) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Show(String title, Integer year, Ids ids) {
    }

    /**
     * External identifiers.
     *
     * <p>{@code imdb} is the one worth having: it is what {@code title.imdb_id}
     * holds, so a movie or a series carrying one matches exactly rather than by
     * name and year.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ids(Long trakt, String slug, String imdb, Long tmdb) {
    }

    /** A page of history, with what the response headers said about paging. */
    public record HistoryPage(List<HistoryItem> items, int page, int pageCount) {

        public boolean hasMore() {
            return page < pageCount;
        }
    }
}
