package com.dhuelin.dev.watchguru.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * IMDb bulk-dataset enrichment.
 *
 * <p>There is no free public IMDb REST API. The official route is IMDb
 * Essential Metadata on AWS Data Exchange, which is paid; the "IMDb APIs" on
 * RapidAPI are unofficial scrapers with no stability guarantee. IMDb does
 * publish bulk datasets, refreshed daily, and {@code title.ratings.tsv.gz}
 * joins cleanly on the {@code imdb_id} TMDB already gives us.
 *
 * <p><strong>Licensing.</strong> These datasets are licensed for personal and
 * non-commercial use. That is fine for a free app and is a blocker for a paid
 * or ad-supported one. {@code enabled} is the switch that makes dropping IMDb
 * ratings a configuration change rather than a code change, which is the whole
 * reason it exists.
 *
 * @param enabled     whether the job runs at all
 * @param ratingsUrl  the gzipped TSV to read
 * @param cron        when to run; nightly and off-peak by default
 * @param batchSize   rows per UPDATE statement
 * @param readTimeout how long to wait on the download
 */
@ConfigurationProperties(prefix = "watch-guru.imdb")
public record ImdbProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://datasets.imdbws.com/title.ratings.tsv.gz") String ratingsUrl,
        @DefaultValue("0 30 3 * * *") String cron,
        @DefaultValue("5000") int batchSize,
        @DefaultValue("10m") Duration readTimeout) {
}
