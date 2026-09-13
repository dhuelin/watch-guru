package com.dhuelin.dev.watchguru.streaming.mediaserver;

import com.dhuelin.dev.watchguru.imports.domain.ImportRow;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

/**
 * Emby's built-in webhooks.
 *
 * <p>The same reasoning as Jellyfin, which is no coincidence -- Jellyfin is a
 * fork of Emby, and the two payloads are the same idea under different names.
 * Watched means {@code playback.stop} with {@code PlayedToCompletion}; the
 * webhook is the server's rather than one user's, so the account name is
 * required.
 *
 * <p>Where they differ is nesting: Emby puts the item, the user and the
 * playback information in objects rather than flattening everything into one
 * level, and its ids live under {@code ProviderIds} in the shape Emby's API
 * uses elsewhere.
 */
@Component
public class EmbyAdapter implements MediaServerAdapter {

    private final JsonMapper json;

    public EmbyAdapter(JsonMapper json) {
        this.json = json;
    }

    @Override
    public String slug() {
        return "emby";
    }

    @Override
    public String displayName() {
        return "Emby";
    }

    @Override
    public boolean requiresAccountName() {
        return true;
    }

    @Override
    public Optional<MediaServerEvent> read(String payload, LocalDate watchedOn) {
        JsonNode root = Payloads.parse(json, payload);

        String event = Payloads.text(root, "Event");
        if (event == null || !event.toLowerCase(Locale.ROOT).startsWith("playback.stop")
                || !Payloads.bool(root.path("PlaybackInfo"), "PlayedToCompletion")) {
            return Optional.empty();
        }

        JsonNode item = root.path("Item");
        String type = Payloads.text(item, "Type");
        if (type == null) {
            return Optional.empty();
        }
        String itemKey = Payloads.text(item, "Id");

        Optional<ImportRow> row = switch (type.toLowerCase(Locale.ROOT)) {
            case "movie" -> MediaServerRows.movie(slug(), itemKey,
                    Payloads.text(item, "Name"), Payloads.integer(item, "ProductionYear"),
                    Payloads.text(item.path("ProviderIds"), "Imdb"), watchedOn);
            case "episode" -> MediaServerRows.episode(slug(), itemKey,
                    Payloads.text(item, "SeriesName"),
                    Payloads.integer(item, "ParentIndexNumber"),
                    Payloads.integer(item, "IndexNumber"),
                    Payloads.text(item, "Name"), watchedOn);
            default -> Optional.empty();
        };

        return row.map(r -> new MediaServerEvent(
                Payloads.text(root.path("User"), "Name"), null, r));
    }
}
