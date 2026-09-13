package com.dhuelin.dev.watchguru.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * The {@link RestClient} used to talk to Trakt.
 *
 * <p>The two Trakt headers are set once here. {@code trakt-api-key} is the
 * client id and identifies the application on every call, authorised or not;
 * {@code trakt-api-version} pins the API this code was written against, so a
 * future version of theirs does not silently change what arrives.
 */
@Configuration
public class TraktClientConfig {

    @Bean
    RestClient traktRestClient(TraktProperties properties, RestClient.Builder builder) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(properties.connectTimeout(), properties.readTimeout());

        return builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("trakt-api-version", "2")
                .defaultHeader("trakt-api-key", properties.clientId() == null ? "" : properties.clientId())
                .build();
    }
}
