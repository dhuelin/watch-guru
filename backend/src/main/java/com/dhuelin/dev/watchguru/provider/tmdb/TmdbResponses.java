package com.dhuelin.dev.watchguru.provider.tmdb;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Wire records for the TMDB endpoints in use.
 *
 * <p>Fields are a deliberate subset: TMDB returns considerably more than the
 * application stores, and unknown properties are ignored by the configured
 * object mapper.
 */
final class TmdbResponses {

    private TmdbResponses() {
    }

    record MultiSearch(
            int page,
            List<MultiSearchResult> results,
            @JsonProperty("total_pages") int totalPages,
            @JsonProperty("total_results") long totalResults
    ) {
    }

    record MultiSearchResult(
            Long id,
            @JsonProperty("media_type") String mediaType,
            String title,
            String name,
            @JsonProperty("original_title") String originalTitle,
            @JsonProperty("original_name") String originalName,
            @JsonProperty("release_date") String releaseDate,
            @JsonProperty("first_air_date") String firstAirDate,
            String overview,
            @JsonProperty("poster_path") String posterPath,
            @JsonProperty("backdrop_path") String backdropPath,
            @JsonProperty("original_language") String originalLanguage,
            @JsonProperty("vote_average") BigDecimal voteAverage,
            @JsonProperty("vote_count") Integer voteCount,
            BigDecimal popularity,
            Boolean adult,
            @JsonProperty("genre_ids") List<Long> genreIds
    ) {
    }

    /** Shared shape for {@code /movie/{id}} and {@code /tv/{id}}. */
    record TitleDetail(
            Long id,
            @JsonProperty("imdb_id") String imdbId,
            String title,
            String name,
            @JsonProperty("original_title") String originalTitle,
            @JsonProperty("original_name") String originalName,
            String tagline,
            String overview,
            String homepage,
            String status,
            @JsonProperty("release_date") String releaseDate,
            @JsonProperty("first_air_date") String firstAirDate,
            @JsonProperty("last_air_date") String lastAirDate,
            @JsonProperty("poster_path") String posterPath,
            @JsonProperty("backdrop_path") String backdropPath,
            @JsonProperty("original_language") String originalLanguage,
            @JsonProperty("vote_average") BigDecimal voteAverage,
            @JsonProperty("vote_count") Integer voteCount,
            BigDecimal popularity,
            Boolean adult,
            Integer runtime,
            @JsonProperty("episode_run_time") List<Integer> episodeRunTime,
            @JsonProperty("number_of_seasons") Integer numberOfSeasons,
            @JsonProperty("number_of_episodes") Integer numberOfEpisodes,
            List<Genre> genres,
            List<SeasonSummary> seasons,
            @JsonProperty("external_ids") ExternalIds externalIds
    ) {
    }

    record Genre(Long id, String name) {
    }

    record ExternalIds(@JsonProperty("imdb_id") String imdbId) {
    }

    record SeasonSummary(
            Long id,
            @JsonProperty("season_number") Integer seasonNumber,
            String name,
            String overview,
            @JsonProperty("air_date") String airDate,
            @JsonProperty("episode_count") Integer episodeCount,
            @JsonProperty("poster_path") String posterPath
    ) {
    }

    record SeasonDetail(
            Long id,
            @JsonProperty("season_number") Integer seasonNumber,
            String name,
            String overview,
            @JsonProperty("air_date") String airDate,
            @JsonProperty("poster_path") String posterPath,
            List<EpisodeDetail> episodes
    ) {
    }

    record EpisodeDetail(
            Long id,
            @JsonProperty("season_number") Integer seasonNumber,
            @JsonProperty("episode_number") Integer episodeNumber,
            String name,
            String overview,
            @JsonProperty("air_date") String airDate,
            Integer runtime,
            @JsonProperty("still_path") String stillPath,
            @JsonProperty("vote_average") BigDecimal voteAverage
    ) {
    }

    /** {@code /{type}/{id}/watch/providers} — results are keyed by region code. */
    record WatchProviders(Long id, Map<String, RegionOffers> results) {
    }

    record RegionOffers(
            String link,
            List<WatchProvider> flatrate,
            List<WatchProvider> rent,
            List<WatchProvider> buy,
            List<WatchProvider> ads,
            List<WatchProvider> free
    ) {
    }

    record WatchProvider(
            @JsonProperty("provider_id") Long providerId,
            @JsonProperty("provider_name") String providerName,
            @JsonProperty("logo_path") String logoPath,
            @JsonProperty("display_priority") Integer displayPriority
    ) {
    }

    record WatchProviderList(List<WatchProvider> results) {
    }
}
