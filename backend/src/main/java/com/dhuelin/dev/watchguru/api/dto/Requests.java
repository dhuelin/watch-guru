package com.dhuelin.dev.watchguru.api.dto;

import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.dhuelin.dev.watchguru.notifications.domain.DevicePlatform;
import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request bodies for the write endpoints. */
public final class Requests {

    private Requests() {
    }

    /**
     * Partial profile update; any null field is left unchanged.
     *
     * <p>Replaces the old {@code CreateUser}. Accounts are no longer created by
     * a request: the first valid token provisions one, so email is not settable
     * here -- it is asserted by the identity provider and changing it locally
     * would decouple the account from the identity that owns it.
     */
    /**
     * A provider ID token being exchanged for a session.
     *
     * @param providerToken Apple's or Google's ID token, exactly as the
     *                      platform sign-in returned it
     */
    public record ExchangeToken(
            @NotBlank @Size(max = 4096) String providerToken) {
    }

    /**
     * A refresh token being rotated or revoked.
     *
     * <p>In the body rather than the Authorization header: it is not a bearer
     * credential for the API, it is the input to one endpoint, and headers end
     * up in access logs far more often than bodies do.
     */
    public record RefreshSession(
            @NotBlank @Size(max = 512) String refreshToken) {
    }

    public record UpdateProfile(
            @Size(max = 128) String displayName,
            @Size(min = 2, max = 2) String region,
            @Size(max = 16) String language,
            @Size(max = 64) String timeZone
    ) {
    }

    /** Adds a title by its provider id, importing it into the catalog first. */
    public record AddToWatchlist(
            @NotNull TitleType titleType,
            @NotNull @Positive Long providerId,
            WatchStatus status
    ) {
        public WatchStatus statusOrDefault() {
            return status == null ? WatchStatus.WATCHLIST : status;
        }
    }

    /** Partial update; any null field is left unchanged. */
    public record UpdateWatchlistItem(
            WatchStatus status,
            @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal rating,
            String notes
    ) {
    }

    /**
     * Marks everything up to and including one episode.
     *
     * @param watchedAt when it was watched; defaults to now. A single timestamp
     *                  for the whole run is deliberate -- the alternative is
     *                  inventing dates for episodes watched at unknown times.
     */
    public record MarkWatchedUpTo(
            @NotNull Long episodeId,
            Instant watchedAt
    ) {
    }

    public record LogMovieWatched(
            @NotNull Long titleId,
            Instant watchedAt,
            Long streamingServiceId
    ) {
    }

    public record LogEpisodeWatched(
            @NotNull Long episodeId,
            Instant watchedAt,
            Long streamingServiceId
    ) {
    }

    /**
     * Registers this install for push notifications.
     *
     * <p>Sent on every launch, not only the first: APNs and FCM both reissue
     * tokens, and the app cannot tell when they have.
     *
     * @param timeZone the device's IANA zone, e.g. {@code Europe/Zurich}.
     *                 Optional, and the only route by which the server learns
     *                 it: an account starts on UTC, nothing else sets it, and
     *                 without it "do not notify anybody at 3am" means 3am in
     *                 Greenwich for every user on earth
     */
    public record RegisterDevice(
            @NotBlank @Size(max = 512) String token,
            @NotNull DevicePlatform platform,
            @Size(max = 64) String timeZone
    ) {
    }

    /** The global notification switch. */
    public record UpdateNotificationSettings(
            @NotNull Boolean enabled
    ) {
    }

    /** Whether one series may produce new-episode notifications. */
    public record UpdateSeriesNotification(
            @NotNull Boolean newEpisodes
    ) {
    }

    /**
     * A file to be read, before anything is written.
     *
     * <p>The contents rather than a multipart upload: these files are small
     * text exports, both generated clients handle JSON without extra work, and
     * a preview that is not stored server-side has nothing to stream.
     */
    public record PreviewImport(
            @NotBlank @Size(max = 5_000_000) String content
    ) {
    }

    /**
     * The rows a user agreed to after seeing the preview.
     *
     * <p>Sent back rather than held server-side between the two calls: a parked
     * import would be one more thing to expire and clean up, and the file
     * belongs to the user anyway.
     */
    public record CommitImport(
            // @Valid on the element, not only the list: without it the
            // constraints below are never checked, and a row with no title id
            // reaches the writer as a 500 rather than a 400.
            @NotNull @Size(max = 10_000) List<@Valid ImportSelection> rows
    ) {
    }

    /**
     * One accepted row.
     *
     * @param sourceRef  the reference from the preview; it is what makes a
     *                   repeated import write nothing the second time
     * @param titleId    the title to record against -- the preview's match, or
     *                   the one the user picked for an ambiguous row
     * @param episodeId  the episode, for an episode row
     * @param titleText  what the file called it, for the failure summary
     * @param watchedAt  the date from the file; today if absent
     * @param rating     the rating from the file, 0-10. Sent back rather than
     *                   remembered server-side for the same reason as the rest
     *                   of the row -- and without it, an IMDb or Letterboxd
     *                   import would drop every rating it had just shown the
     *                   user in the preview
     */
    public record ImportSelection(
            @NotBlank @Size(max = 255) String sourceRef,
            @NotNull Long titleId,
            Long episodeId,
            @Size(max = 512) String titleText,
            // Date or date-time; see LenientLocalDateDeserializer for why the
            // server has to take both from its own generated clients.
            @JsonDeserialize(using = LenientLocalDateDeserializer.class) LocalDate watchedAt,
            @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal rating
    ) {
    }

    /**
     * Connecting a Plex server.
     *
     * @param plexUsername the Plex account whose viewing should be recorded.
     *                     Optional, and it matters on a shared server: a Plex
     *                     server owner receives webhook deliveries for
     *                     everybody who watches anything on it, and without a
     *                     name to match, a housemate's evening would land in
     *                     this user's history
     */
    public record ConnectPlex(
            @Size(max = 128) String plexUsername
    ) {
    }
}
