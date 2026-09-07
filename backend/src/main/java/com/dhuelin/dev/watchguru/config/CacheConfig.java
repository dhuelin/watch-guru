package com.dhuelin.dev.watchguru.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caching for provider search results.
 *
 * <p>Search is the highest-volume endpoint and the only one that was not cached
 * at all: title details and availability already have TTL-based freshness in the
 * database, but every keystroke-debounced search went straight to TMDB. Two apps
 * with search boxes is enough to start collecting 429s, which reach the user as
 * a search that simply failed.
 *
 * <p>Deliberately in-memory rather than in the database. These entries live for
 * a minute and exist to collapse a burst, not to be durable; putting them in
 * Postgres would mean writes on the read path for data that is stale almost
 * immediately.
 *
 * <p>The corollary is that this cache is per-instance. Behind several replicas
 * the hit rate falls but nothing breaks, and a shared cache can be introduced
 * later without changing any calling code.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Cache of provider search pages, keyed by query, page and language. */
    public static final String SEARCH_CACHE = "tmdbSearch";

    @Bean
    CaffeineCacheManager cacheManager(TmdbProperties properties) {
        TmdbProperties.Search search = properties.search();

        CaffeineCacheManager manager = new CaffeineCacheManager(SEARCH_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(search.cacheTtl())
                .maximumSize(search.cacheMaxSize())
                // Required for the cache metrics actuator exposes; without it
                // the hit ratio is invisible and there is no way to tell whether
                // this is working.
                .recordStats());
        // search() always returns a page object, so nulls never arise; an empty
        // page is a real value and is cached like any other. That matters: a
        // user typing a title that does not exist is exactly the case that
        // would otherwise pound the provider with misses.
        manager.setAllowNullValues(false);
        return manager;
    }
}
