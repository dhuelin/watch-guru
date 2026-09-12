package com.dhuelin.dev.watchguru.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Connecting a Plex server (#39).
 *
 * @param publicBaseUrl the address this service is reachable at from the
 *                      user's own network, e.g. {@code https://api.watch-guru.dev}.
 *                      The webhook URL handed to the user is built from it.
 *                      Left empty, the URL is derived from the incoming
 *                      request, which is right in development and wrong behind
 *                      any proxy that does not forward the original host --
 *                      so a deployment sets this
 */
@ConfigurationProperties(prefix = "watch-guru.plex")
public record PlexProperties(
        @DefaultValue("") String publicBaseUrl
) {
}
