package com.dhuelin.dev.watchguru.streaming.plex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A Plex webhook delivery, in the fields this service reads.
 *
 * <p>Plex sends considerably more than this -- player addresses, library
 * section ids, thumbnails, a whole server description -- and adds fields
 * between releases. Everything unknown is ignored rather than rejected: a new
 * field in a Plex update must not turn every scrobble into an error.
 *
 * @param event   what happened. Only {@code media.scrobble} records a watch;
 *                see {@link #isScrobble()}
 * @param user    whether the account that played it is the one that owns this
 *                webhook. A server owner also receives events for other people
 *                on their server, and a housemate's viewing is not the user's
 *                history
 * @param owner   whether that account owns the server. Read for diagnostics
 *                only; it does not decide anything
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlexWebhookPayload(
        String event,
        Boolean user,
        Boolean owner,
        @JsonProperty("Account") Account account,
        @JsonProperty("Server") Server server,
        @JsonProperty("Metadata") Metadata metadata
) {

    /** Plex fires this one at roughly 90% of the runtime: watched, not started. */
    public boolean isScrobble() {
        return "media.scrobble".equals(event);
    }

    /**
     * Whether the account that played it owns this webhook.
     *
     * <p>Boxed in the record and answered as a plain false here: an absent
     * flag must not throw. Plex sends it on every delivery today, and a
     * payload that omits it tomorrow -- or one posted by something that is not
     * Plex -- is simply not evidence that this is the user's own viewing.
     */
    public boolean isWebhookOwners() {
        return Boolean.TRUE.equals(user);
    }

    /** The Plex account that played it. {@code title} is the Plex username. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(Long id, String title) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Server(String title, String uuid) {
    }

    /**
     * What was played.
     *
     * @param type            {@code movie}, {@code episode}, or something this
     *                        does not track -- music and photos come through
     *                        the same webhook
     * @param title           the film's name, or the episode's
     * @param grandparentTitle the series' name, for an episode
     * @param index           the episode number, for an episode
     * @param parentIndex     the season number, for an episode
     * @param year            the release year -- of the film, or, for an
     *                        episode, of that episode
     * @param ratingKey       the item's id on that server, used to tell a
     *                        replayed delivery from a new viewing
     * @param guid            Plex's own identifier, kept for diagnostics
     * @param guids           external ids Plex resolved: {@code imdb://tt...},
     *                        {@code tmdb://...}, {@code tvdb://...}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(
            String type,
            String title,
            String grandparentTitle,
            Integer index,
            Integer parentIndex,
            Integer year,
            String ratingKey,
            String guid,
            @JsonProperty("Guid") List<Guid> guids
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Guid(String id) {
        }
    }
}
