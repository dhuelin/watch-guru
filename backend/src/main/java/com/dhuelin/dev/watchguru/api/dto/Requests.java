package com.dhuelin.dev.watchguru.api.dto;

import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
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
}
