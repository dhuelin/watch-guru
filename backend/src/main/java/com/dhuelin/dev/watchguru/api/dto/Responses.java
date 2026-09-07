package com.dhuelin.dev.watchguru.api.dto;

import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.streaming.domain.LinkStatus;
import com.dhuelin.dev.watchguru.streaming.domain.OfferType;
import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Response bodies. Records are kept flat so the JSON is easy to consume. */
public final class Responses {

    private Responses() {
    }

    public record UserResponse(
            Long id, String email, String displayName, String region, String language, String timeZone
    ) {
    }

    /** A provider search hit, not yet in the local catalog. */
    public record SearchHit(
            Long providerId,
            TitleType titleType,
            String title,
            String originalTitle,
            LocalDate releaseDate,
            String overview,
            String posterUrl,
            BigDecimal providerRating,
            Integer providerVoteCount
    ) {
    }

    public record SearchResponse(List<SearchHit> results, int page, int totalPages, long totalResults) {
    }

    public record GenreResponse(Long id, String name) {
    }

    public record AvailabilityResponse(
            Long serviceId, String serviceName, String logoUrl, OfferType offerType, String link, Instant fetchedAt
    ) {
    }

    public record TitleResponse(
            Long id,
            Long providerId,
            String imdbId,
            TitleType titleType,
            String primaryTitle,
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
            List<GenreResponse> genres,
            List<AvailabilityResponse> availability
    ) {
    }

    public record WatchlistItemResponse(
            Long id,
            WatchStatus status,
            boolean favorite,
            int priority,
            BigDecimal userRating,
            String notes,
            Instant addedAt,
            Instant startedAt,
            Instant completedAt,
            TitleResponse title
    ) {
    }

    public record WatchEventResponse(
            Long id,
            Long titleId,
            String primaryTitle,
            Long episodeId,
            String episodeCode,
            String episodeName,
            Instant watchedAt,
            Integer minutesWatched,
            boolean rewatch,
            String streamingServiceName
    ) {
    }

    public record StreamingServiceResponse(
            Long id, String slug, String name, String logoUrl, boolean supportsSync
    ) {
    }

    /** Surface for the planned Netflix/Disney+ integrations. */
    public record LinkedAccountResponse(
            Long id,
            StreamingServiceResponse service,
            String accountLabel,
            LinkStatus status,
            boolean syncEnabled,
            Instant lastSyncAt,
            String lastSyncError
    ) {
    }
}
