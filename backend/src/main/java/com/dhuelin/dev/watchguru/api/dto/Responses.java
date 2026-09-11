package com.dhuelin.dev.watchguru.api.dto;

import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.imports.domain.ImportSource;
import com.dhuelin.dev.watchguru.imports.domain.MatchStatus;
import com.dhuelin.dev.watchguru.streaming.domain.LinkStatus;
import com.dhuelin.dev.watchguru.streaming.domain.OfferType;
import com.dhuelin.dev.watchguru.streaming.domain.SyncStatus;
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

    /**
     * A freshly issued session.
     *
     * <p>The refresh token is returned exactly once, here. It is not stored in
     * a form that can be given back, so a client that loses it has to sign in
     * again -- which is the correct outcome, not a gap.
     *
     * @param expiresIn seconds until the access token expires, rather than an
     *                  absolute time: a client whose clock is wrong would
     *                  otherwise refresh constantly or not at all
     */
    public record SessionResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tokenType,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long expiresIn,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String refreshToken,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant refreshTokenExpiresAt
    ) {
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
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AvailabilityResponse> availability,

            /*
             * When the availability above was confirmed with the provider, or
             * null if it never has been.
             *
             * A timestamp rather than a boolean, for two reasons. An empty
             * list with a timestamp means the title is on no service in that
             * country, which is worth saying, while an empty list without one
             * means the provider could not be reached -- a different claim
             * that must not be shown as the first. And availability is cached
             * for a day, so the apps need the age anyway; the offers carry
             * their own timestamps, but a confirmed-empty result has no offers
             * to carry one.
             *
             * Optional, deliberately: a required field added to a response
             * both apps cache offline would make every snapshot written by a
             * previous version undecodable, and an undecodable snapshot is
             * deleted -- costing an offline user their library on upgrade.
             */
            Instant availabilityCheckedAt
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
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TitleResponse title,
            /**
             * Progress for a series, null for a film.
             *
             * <p>Carried on the list response on purpose. Without it a library
             * row cannot show a progress bar without one extra request each,
             * which is an N+1 on the screen users open most.
             */
            SeriesProgress progress
    ) {
    }

    /** The counts a library row needs, and nothing more. */
    public record SeriesProgress(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int watchedEpisodes,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int airedEpisodes,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int percentComplete
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

    /**
     * One episode, with whether the signed-in user has seen it.
     *
     * <p>Watched state is part of this rather than a separate call because the
     * episode list is useless without it: the screen exists to show which
     * episodes are ticked, and fetching that separately would mean two requests
     * to draw one list.
     */
    public record EpisodeResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer seasonNumber,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer episodeNumber,
            /** "S01E02" style label, so clients do not each format their own. */
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
            String name,
            String overview,
            LocalDate airDate,
            Integer runtimeMinutes,
            String stillUrl,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean watched,
            /** Greater than one only for a rewatch. */
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int watchCount,
            /**
             * False for an episode that has not aired. Clients must not offer
             * to mark these, and they are excluded from progress.
             */
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean aired
    ) {
    }

    public record SeasonResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer seasonNumber,
            String name,
            String overview,
            LocalDate airDate,
            String posterUrl,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int airedEpisodes,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int watchedEpisodes,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<EpisodeResponse> episodes
    ) {
    }

    /**
     * Every season of a series with its episodes.
     *
     * <p>Season 0 -- specials -- is included but flagged, so a client can show
     * it while leaving it out of progress. Excluding it entirely would hide
     * episodes people have genuinely watched.
     */
    public record SeasonsResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long titleId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String primaryTitle,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<SeasonResponse> seasons
    ) {
    }

    /**
     * One in-progress series and the episode to watch next.
     *
     * <p>The shape the Home screen needs, in one row per series.
     */
    public record UpNextResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long titleId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String primaryTitle,
            String posterUrl,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long nextEpisodeId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nextEpisodeCode,
            String nextEpisodeName,
            String nextEpisodeStillUrl,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int watchedEpisodes,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int airedEpisodes,
            /** When this series was last watched; drives the ordering. */
            Instant lastWatchedAt
    ) {
    }

    /** What a bulk mark actually changed. */
    public record BulkMarkResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long titleId,
            /** Episodes newly marked. Excludes ones already watched. */
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int newlyMarked,
            /** Already watched, so left alone rather than counted as a rewatch. */
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int alreadyWatched,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int watchedEpisodes,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int airedEpisodes
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

    /**
     * What one series' notification setting is.
     *
     * <p>Only series the user has said something explicit about are listed.
     * Absence means notify, so a client renders every followed series as on
     * unless it appears here.
     */
    public record SeriesNotificationResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long titleId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String titleName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean newEpisodes
    ) {
    }

    /** The notification settings screen, in one response. */
    public record NotificationSettingsResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean enabled,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<SeriesNotificationResponse> series,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int registeredDevices
    ) {
    }

    /** One title the user could mean, where the file was ambiguous. */
    public record ImportCandidateResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long titleId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String titleName,
            Integer year
    ) {
    }

    /**
     * One row of the file, and what the catalogue made of it.
     *
     * <p>Unmatched rows are in here too. A row that vanished quietly is a row
     * nobody knows to re-enter.
     */
    public record ImportRowResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sourceRef,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String titleText,
            Integer year,
            String imdbId,
            Integer seasonNumber,
            Integer episodeNumber,
            String episodeName,
            LocalDate watchedAt,
            BigDecimal rating,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) MatchStatus status,
            Long titleId,
            String titleName,
            Long episodeId,
            String episodeCode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ImportCandidateResponse> candidates,
            String note
    ) {
    }

    /**
     * What a file would do, before it does anything.
     *
     * @param problems       lines that could not be read at all
     * @param warnings       where this source does not mean quite what it looks
     *                       like -- an IMDb rating date is not a watch date
     * @param alreadyImported rows this user has imported before, which a second
     *                       run will skip rather than duplicate
     */
    public record ImportPreviewResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ImportSource source,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ImportRowResponse> rows,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> problems,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> warnings,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int alreadyImported
    ) {
    }

    /** What a commit actually did. */
    public record ImportResultResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int imported,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int skipped,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int failed,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> problems
    ) {
    }

    /**
     * A Plex connection, and the webhook URL that feeds it.
     *
     * <p>{@code webhookUrl} carries the secret and is returned exactly once,
     * when the connection is made. It is stored only as a hash, so this
     * service cannot show it again -- a user who loses it reconnects, which
     * issues a new one and retires the old.
     *
     * @param setUpHint what the user has to do with the URL, in one line: the
     *                  app can show it verbatim next to a copy button
     */
    public record PlexConnectionResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LinkedAccountResponse account,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String webhookUrl,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String setUpHint
    ) {
    }

    /**
     * Whether a connection is working, and what it has done lately.
     *
     * @param connected  whether a webhook URL is live for this user
     * @param recentRuns newest first. An integration that silently stops is
     *                   worse than one never offered, so the last few
     *                   deliveries are visible rather than inferred from
     *                   whether anything showed up in the library
     */
    public record PlexStatusResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean connected,
            LinkedAccountResponse account,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<SyncRunResponse> recentRuns
    ) {
    }

    /** One delivery from a linked service, and what it came to. */
    public record SyncRunResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startedAt,
            Instant finishedAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SyncStatus status,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int itemsImported,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int itemsSkipped,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int itemsFailed,
            String errorMessage
    ) {
    }
}
