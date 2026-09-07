package com.dhuelin.dev.watchguru.imdb;

import java.math.BigDecimal;

/**
 * One row of {@code title.ratings.tsv}.
 *
 * @param imdbId      the {@code tconst}, e.g. {@code tt0903747}
 * @param rating      average rating, 1.0 to 10.0
 * @param voteCount   number of votes
 */
public record ImdbRating(String imdbId, BigDecimal rating, int voteCount) {
}
