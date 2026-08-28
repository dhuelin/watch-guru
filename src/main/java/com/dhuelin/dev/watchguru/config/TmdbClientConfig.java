package com.dhuelin.dev.watchguru.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/** Builds the dedicated {@link RestClient} used to talk to TMDB. */
@Configuration
public class TmdbClientConfig {

    @Bean
    RestClient tmdbRestClient(TmdbProperties properties, RestClient.Builder builder) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(properties.connectTimeout(), properties.readTimeout());

        return builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiToken())
                .build();
    }
}
