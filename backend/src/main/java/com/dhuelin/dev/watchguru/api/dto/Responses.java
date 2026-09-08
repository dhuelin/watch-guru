package com.dhuelin.dev.watchguru.api.dto;

import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.streaming.domain.LinkStatus;
import com.dhuelin.dev.watchguru.streaming.domain.OfferType;
import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Response bodies. Records are kept flat so the JSON is easy to consume.
 *
 * <p>Fields the server always populates carry {@link Schema} {@code REQUIRED}.
 * This is not decoration: both mobile clients are generated from the resulting
 * document, and an unmarked field becomes optional in Swift and Kotlin. Left
 * unmarked, every screen would null-check an id or a title that cannot actually
 * be null, and the fields that genuinely are optional -- a tagline, an IMDb
 * rating, the next episode of a finished series -- would be indistinguishable
 * from the ones that are not.
 */
public final class Responses {

    private Responses() {
    }

    public record UserResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String region,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String language,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String timeZone
    ) {
    }

    /** A provider search hit, not yet in the local catalog. */
    public record SearchHit(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long providerId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TitleType titleType,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
            String originalTitle,
            LocalDate releaseDate,
            String overview,
            String posterUrl,
            BigDecimal providerRating,
            Integer providerVoteCount
    ) {
    }

    public record SearchResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<SearchHit> results,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalResults) {
    }

    public record GenreResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {
    }

    public record AvailabilityResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long serviceId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String serviceName,
            String logoUrl,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OfferType offerType,
            String link,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant fetchedAt
    ) {
    }

    public record TitleResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long providerId,
            String imdbId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TitleType titleType,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String primaryTitle,
            String originalTitle,
            String tagline,
            String overview,
            String posterUrl,
            String backdropUrl,
            LocalDate releaseDate,
            LocalDate lastAirDate,
            String productionStatus,
            Integer runtimeMinutes,
            Integer numberOfSeasons,
            Integer numberOfEpisodes,
            BigDecimal providerRating,
            Integer providerVoteCount,
            BigDecimal imdbRating,
            String imdbUrl,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<GenreResponse> genres,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AvailabilityResponse> availability
    ) {
    }

    public record WatchlistItemResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WatchStatus status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean favorite,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int priority,
            BigDecimal userRating,
            String notes,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant addedAt,
            Instant startedAt,
            Instant completedAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TitleResponse title
    ) {
    }

    public record WatchEventResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long titleId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String primaryTitle,
            Long episodeId,
            String episodeCode,
            String episodeName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant watchedAt,
            Integer minutesWatched,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean rewatch,
            String streamingServiceName
    ) {
    }

    public record StreamingServiceResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String slug,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
            String logoUrl,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean supportsSync
    ) {
    }

    /** Surface for the planned Netflix/Disney+ integrations. */
    public record LinkedAccountResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) StreamingServiceResponse service,
            String accountLabel,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LinkStatus status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean syncEnabled,
            Instant lastSyncAt,
            String lastSyncError
    ) {
    }
}
