package com.dhuelin.dev.watchguru.streaming.mediaserver;

import com.dhuelin.dev.watchguru.imports.domain.ImportRow;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

/**
 * Jellyfin's Webhook plugin.
 *
 * <p>Two differences from Plex decide the shape of this. Jellyfin has no
 * "scrobble at ninety per cent" event, so the claim that something was watched
 * is a {@code PlaybackStop} that also says {@code PlayedToCompletion} -- a stop
 * without it is somebody giving up halfway, which is the opposite of watched.
 *
 * <p>And the webhook is configured once for the whole server by its
 * administrator, firing for every user on it, with nothing in the payload
 * saying whose webhook it is. {@code NotificationUsername} is the only thing
 * separating this user's viewing from everybody else's, so a connection here
 * insists on one.
 *
 * <p>The payload is a template the server owner can edit, which is why every
 * field is read leniently: the plugin's own default quotes numbers and
 * booleans, and a field that has moved between releases must read as "not this
 * kind of event" rather than as a failure.
 */
@Component
public class JellyfinAdapter implements MediaServerAdapter {

    private final JsonMapper json;

    public JellyfinAdapter(JsonMapper json) {
        this.json = json;
    }

    @Override
    public String slug() {
        return "jellyfin";
    }

    @Override
    public String displayName() {
        return "Jellyfin";
    }

    @Override
    public boolean requiresAccountName() {
        return true;
    }

    @Override
    public Optional<MediaServerEvent> read(String payload, LocalDate watchedOn) {
        JsonNode root = Payloads.parse(json, payload);

        if (!"PlaybackStop".equalsIgnoreCase(Payloads.text(root, "NotificationType"))
                || !Payloads.bool(root, "PlayedToCompletion")) {
            return Optional.empty();
        }
        String type = Payloads.text(root, "ItemType");
        if (type == null) {
            return Optional.empty();
        }
        String itemKey = Payloads.text(root, "ItemId");

        Optional<ImportRow> row = switch (type.toLowerCase(Locale.ROOT)) {
            case "movie" -> MediaServerRows.movie(slug(), itemKey,
                    Payloads.text(root, "Name"), Payloads.integer(root, "Year"),
                    Payloads.text(root, "Provider_imdb"), watchedOn);
            case "episode" -> MediaServerRows.episode(slug(), itemKey,
                    Payloads.text(root, "SeriesName"),
                    Payloads.integer(root, "SeasonNumber"),
                    Payloads.integer(root, "EpisodeNumber"),
                    Payloads.text(root, "Name"), watchedOn);
            default -> Optional.empty();
        };

        // No webhook-owner flag exists here, hence null and hence the insisted
        // username: without it there would be nothing to filter on at all.
        return row.map(r -> new MediaServerEvent(
                Payloads.text(root, "NotificationUsername"), null, r));
    }
}
