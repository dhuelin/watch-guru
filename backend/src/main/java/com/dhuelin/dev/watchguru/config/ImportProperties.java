package com.dhuelin.dev.watchguru.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Reading a viewing history in from another service (#21).
 *
 * @param providerLookupsPerImport how many rows of one file may be looked up
 *                                 against the metadata provider. A decade of
 *                                 Netflix history is thousands of rows, and
 *                                 firing thousands of searches upstream on one
 *                                 button press is how an API key gets
 *                                 suspended. Rows past the budget come back as
 *                                 "not looked up" -- which the user can act on
 *                                 by importing again -- rather than as "not
 *                                 found", which would be a claim this has not
 *                                 earned
 */
@ConfigurationProperties(prefix = "watch-guru.imports")
public record ImportProperties(
        @DefaultValue("50") int providerLookupsPerImport
) {
}
