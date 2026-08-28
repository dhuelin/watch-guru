package com.dhuelin.dev.watchguru.api.dto;

import com.dhuelin.dev.watchguru.config.TmdbProperties;
import org.springframework.stereotype.Component;

/** Turns provider image paths into absolute CDN URLs for API responses. */
@Component
public class ImageUrls {

    private static final String POSTER_SIZE = "w500";
    private static final String BACKDROP_SIZE = "w1280";
    private static final String LOGO_SIZE = "w92";
    private static final String STILL_SIZE = "w300";

    private final TmdbProperties properties;

    public ImageUrls(TmdbProperties properties) {
        this.properties = properties;
    }

    public String poster(String path) {
        return url(path, POSTER_SIZE);
    }

    public String backdrop(String path) {
        return url(path, BACKDROP_SIZE);
    }

    public String logo(String path) {
        return url(path, LOGO_SIZE);
    }

    public String still(String path) {
        return url(path, STILL_SIZE);
    }

    private String url(String path, String size) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return properties.imageBaseUrl() + "/" + size + path;
    }
}
