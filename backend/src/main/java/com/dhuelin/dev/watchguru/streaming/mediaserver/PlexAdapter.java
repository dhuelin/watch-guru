package com.dhuelin.dev.watchguru.streaming.mediaserver;

import com.dhuelin.dev.watchguru.imports.domain.ImportRow;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

/**
 * Plex webhooks.
 *
 * <p>Plex posts every playback event to the URL it was given: play, pause,
 * resume, stop, rate, and -- at roughly ninety per cent of the runtime --
 * scrobble. Only the last of those is a claim that something was watched, and
 * only it is acted on. Acting on {@code media.play} would mark an episode
 * watched for anybody who opened it and changed their mind a minute later.
 *
 * <p>Plex is the one of the three that says whose account played it, in its
 * {@code user} flag, so it is also the only one that can be connected without
 * naming an account.
 */
@Component
public class PlexAdapter implements MediaServerAdapter {

    private final JsonMapper json;

    public PlexAdapter(JsonMapper json) {
        this.json = json;
    }

    @Override
    public String slug() {
        return "plex";
    }

    @Override
    public String displayName() {
        return "Plex";
    }

    @Override
    public boolean requiresAccountName() {
        return false;
    }

    @Override
    public Optional<MediaServerEvent> read(String payload, LocalDate watchedOn) {
        JsonNode root = Payloads.parse(json, payload);

        if (!"media.scrobble".equals(Payloads.text(root, "event"))) {
            return Optional.empty();
        }
        JsonNode metadata = root.path("Metadata");
        String type = Payloads.text(metadata, "type");
        if (type == null) {
            return Optional.empty();
        }
        String itemKey = Payloads.text(metadata, "ratingKey");

        Optional<ImportRow> row = switch (type.toLowerCase(Locale.ROOT)) {
            case "movie" -> MediaServerRows.movie(slug(), itemKey,
                    Payloads.text(metadata, "title"), Payloads.integer(metadata, "year"),
                    imdbId(metadata), watchedOn);
            case "episode" -> MediaServerRows.episode(slug(), itemKey,
                    Payloads.text(metadata, "grandparentTitle"),
                    Payloads.integer(metadata, "parentIndex"),
                    Payloads.integer(metadata, "index"),
                    Payloads.text(metadata, "title"), watchedOn);
            // Music and photos come through the same webhook, and a library
            // section this app does not model is not an error.
            default -> Optional.empty();
        };

        return row.map(r -> new MediaServerEvent(
                Payloads.text(root.path("Account"), "title"),
                root.path("user").isBoolean() ? root.path("user").asBoolean() : null,
                r));
    }

    /** The IMDb id among the external ids Plex resolved, where it resolved one. */
    private static String imdbId(JsonNode metadata) {
        for (JsonNode guid : metadata.path("Guid")) {
            String id = Payloads.text(guid, "id");
            if (id != null && id.startsWith("imdb://")) {
                return id.substring("imdb://".length());
            }
        }
        return null;
    }
}
