package com.dhuelin.dev.watchguru.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Translates a {@code DATABASE_URL} into the three properties Spring reads.
 *
 * <p>Every container host that provisions a database hands it over the same
 * way: one variable, in libpq's URL form,
 * {@code postgres://user:password@host:5432/dbname}. Spring Boot does not read
 * that -- it wants {@code spring.datasource.url} in JDBC form, with the
 * credentials as separate properties -- so without this the application starts
 * against no database at all and fails somewhere further in, with an error
 * that says nothing about the real cause.
 *
 * <p>Nothing here overrides a value that is already set. The property source
 * is added last, so an explicit {@code SPRING_DATASOURCE_URL} still wins; this
 * is a fallback for the hosted case, not a replacement for configuring it.
 *
 * <p>Runs as an {@link EnvironmentPostProcessor} rather than as a bean because
 * the datasource is built long before the application context has beans in it.
 * Registered in
 * {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports}.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "watch-guru-database-url";
    private static final String VARIABLE = "DATABASE_URL";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication app) {
        String url = environment.getProperty(VARIABLE);
        if (url == null || url.isBlank()) {
            return;
        }
        Map<String, Object> translated = translate(url.trim());
        if (!translated.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, translated));
        }
    }

    /**
     * The three properties a {@code postgres://} URL implies, or nothing at
     * all if it is not one.
     *
     * <p>A URL this cannot read is passed over in silence rather than throwing.
     * Refusing to start would be the wrong call: the variable may be set for
     * something else entirely, and an explicit {@code spring.datasource.url}
     * alongside it is a perfectly good configuration that must not be broken
     * by a stray environment variable.
     */
    static Map<String, Object> translate(String databaseUrl) {
        URI uri;
        try {
            uri = new URI(databaseUrl);
        } catch (URISyntaxException e) {
            return Map.of();
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equals("postgres") || scheme.equals("postgresql"))
                || uri.getHost() == null) {
            return Map.of();
        }

        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if (uri.getPort() != -1) {
            jdbc.append(':').append(uri.getPort());
        }
        jdbc.append(uri.getPath() == null ? "" : uri.getPath());
        if (uri.getRawQuery() != null) {
            // Kept verbatim: it is where sslmode lives, and a host that hands
            // out sslmode=disable for its private network means it.
            jdbc.append('?').append(uri.getRawQuery());
        }

        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", jdbc.toString());

        String userInfo = uri.getRawUserInfo();
        if (userInfo != null && !userInfo.isEmpty()) {
            int colon = userInfo.indexOf(':');
            String user = colon < 0 ? userInfo : userInfo.substring(0, colon);
            properties.put("spring.datasource.username", decode(user));
            if (colon >= 0) {
                // Percent-decoded, because a generated password routinely
                // contains characters that had to be escaped to fit in a URL.
                properties.put("spring.datasource.password", decode(userInfo.substring(colon + 1)));
            }
        }
        return properties;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
