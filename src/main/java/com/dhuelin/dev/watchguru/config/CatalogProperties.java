package com.dhuelin.dev.watchguru.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Freshness windows for locally cached TMDB data.
 *
 * @param detailTtl       how long a cached title detail record is served before refetching
 * @param availabilityTtl how long cached streaming availability is served before refetching
 */
@ConfigurationProperties(prefix = "watch-guru.catalog")
public record CatalogProperties(
        Duration detailTtl,
        Duration availabilityTtl
) {
}
