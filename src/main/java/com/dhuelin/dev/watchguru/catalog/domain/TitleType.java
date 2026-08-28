package com.dhuelin.dev.watchguru.catalog.domain;

/** Media type of a catalog entry. Mirrors the two TMDB endpoints we consume. */
public enum TitleType {
    MOVIE,
    TV_SERIES;

    /** Path segment TMDB uses for this type ({@code /movie/...} or {@code /tv/...}). */
    public String tmdbPath() {
        return this == MOVIE ? "movie" : "tv";
    }
}
