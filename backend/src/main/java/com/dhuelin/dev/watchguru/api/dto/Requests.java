package com.dhuelin.dev.watchguru.api.dto;

import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.notifications.domain.DevicePlatform;
import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

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
     */
    public record RegisterDevice(
            @NotBlank @Size(max = 512) String token,
            @NotNull DevicePlatform platform
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
}
