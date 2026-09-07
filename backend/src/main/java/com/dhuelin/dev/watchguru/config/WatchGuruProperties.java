package com.dhuelin.dev.watchguru.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Activates binding for the application's own configuration records. */
@Configuration
@EnableConfigurationProperties({TmdbProperties.class, CatalogProperties.class})
public class WatchGuruProperties {
}
