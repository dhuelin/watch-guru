package com.dhuelin.dev.watchguru.streaming.mediaserver;

import java.time.LocalDate;
import java.util.Optional;

/**
 * One media server's idea of "somebody watched something".
 *
 * <p>Three implementations, and the differences between them are entirely in
 * here: what the payload looks like, which event means watched rather than
 * started, and whether the server says whose account played it. Everything
 * after -- the token, the account filter, matching, writing, the sync-run
 * record -- is shared.
 */
public interface MediaServerAdapter {

    /** Matches {@code streaming_service.slug} and the webhook URL's path. */
    String slug();

    /** What to call it when talking to a person. */
    String displayName();

    /**
     * Whether connecting must be given the account name whose viewing counts.
     *
     * <p>True where the server's webhook is configured once for the whole
     * machine and fires for everybody on it, and the payload carries no flag
     * saying whose account this webhook belongs to. Without a name to match,
     * every housemate's evening would land in this user's library -- so the
     * connection refuses to be made rather than quietly doing that.
     */
    boolean requiresAccountName();

    /**
     * Reads one delivery.
     *
     * @param watchedOn today in the user's own zone, which is half of a
     *                  viewing's identity
     * @return the event, or empty when this delivery is not a claim that a film
     *         or an episode was watched -- a pause, a music track, a payload
     *         missing the fields that name what was played
     * @throws IllegalArgumentException when the payload is not readable at all
     */
    Optional<MediaServerEvent> read(String payload, LocalDate watchedOn);
}
