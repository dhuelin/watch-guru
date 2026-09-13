package com.dhuelin.dev.watchguru.streaming.mediaserver;

import com.dhuelin.dev.watchguru.common.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** The media servers this build can talk to, by slug. */
@Component
public class MediaServers {

    private final Map<String, MediaServerAdapter> bySlug = new LinkedHashMap<>();

    public MediaServers(List<MediaServerAdapter> adapters) {
        adapters.forEach(adapter -> bySlug.put(adapter.slug(), adapter));
    }

    public Optional<MediaServerAdapter> find(String slug) {
        return slug == null ? Optional.empty()
                : Optional.ofNullable(bySlug.get(slug.toLowerCase(Locale.ROOT)));
    }

    /**
     * @throws NotFoundException for a slug this build has no adapter for, which
     *         is what a request for {@code /webhooks/kodi/...} deserves rather
     *         than a 500
     */
    public MediaServerAdapter require(String slug) {
        return find(slug).orElseThrow(() -> NotFoundException.of("Media server", slug));
    }

    public List<MediaServerAdapter> all() {
        return List.copyOf(bySlug.values());
    }
}
