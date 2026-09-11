package com.dhuelin.dev.watchguru.api.dto;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.catalog.domain.Season;
import com.dhuelin.dev.watchguru.tracking.service.EpisodeListService;
import com.dhuelin.dev.watchguru.tracking.service.SeriesProgressCounts;
import com.dhuelin.dev.watchguru.tracking.service.UpNextService;
import com.dhuelin.dev.watchguru.tracking.service.WatchlistService;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.provider.model.ProviderSearchPage;
import com.dhuelin.dev.watchguru.streaming.domain.LinkedStreamingAccount;
import com.dhuelin.dev.watchguru.streaming.domain.StreamingService;
import com.dhuelin.dev.watchguru.streaming.domain.TitleAvailability;
import com.dhuelin.dev.watchguru.streaming.service.AvailabilityService;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.domain.WatchEvent;
import com.dhuelin.dev.watchguru.tracking.domain.WatchlistItem;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Entity-to-response conversion, kept out of the controllers. */
@Component
public class ApiMapper {

    private static final String IMDB_TITLE_URL = "https://www.imdb.com/title/";

    private final ImageUrls images;

    public ApiMapper(ImageUrls images) {
        this.images = images;
    }

    public Responses.UserResponse toUser(AppUser user) {
        return new Responses.UserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRegion(), user.getLanguage(), user.getTimeZone());
    }

    public Responses.SearchResponse toSearch(ProviderSearchPage page) {
        List<Responses.SearchHit> hits = page.results().stream()
                .map(s -> new Responses.SearchHit(
                        s.providerId(), s.titleType(), s.title(), s.originalTitle(),
                        s.releaseDate(), s.overview(), images.poster(s.posterPath()),
                        s.voteAverage(), s.voteCount()))
                .toList();
        return new Responses.SearchResponse(hits, page.page(), page.totalPages(), page.totalResults());
    }

    public Responses.TitleResponse toTitle(Title title, AvailabilityService.Offers availability) {
        return toTitle(title, availability.offers(), availability.checkedAt());
    }

    /**
     * @param checkedAt when the offers were confirmed against the provider, or
     *                  null if never; see
     *                  {@code TitleResponse.availabilityCheckedAt}. Callers
     *                  with no availability at all pass null, because "we did
     *                  not look" is exactly what they mean
     */
    public Responses.TitleResponse toTitle(Title title, List<TitleAvailability> availability, Instant checkedAt) {
        List<Responses.GenreResponse> genres = title.getGenres().stream()
                .map(g -> new Responses.GenreResponse(g.getId(), g.getName()))
                .toList();

        List<Responses.AvailabilityResponse> offers = availability == null ? List.of()
                : availability.stream()
                .map(a -> new Responses.AvailabilityResponse(
                        a.getStreamingService().getId(),
                        a.getStreamingService().getName(),
                        images.logo(a.getStreamingService().getLogoPath()),
                        a.getOfferType(),
                        a.getLink(),
                        a.getFetchedAt()))
                .toList();

        return new Responses.TitleResponse(
                title.getId(),
                title.getTmdbId(),
                title.getImdbId(),
                title.getTitleType(),
                title.getPrimaryTitle(),
                title.getOriginalTitle(),
                title.getTagline(),
                title.getOverview(),
                images.poster(title.getPosterPath()),
                images.backdrop(title.getBackdropPath()),
                title.primaryReleaseDate(),
                title.getLastAirDate(),
                title.getProductionStatus(),
                title.getRuntimeMinutes(),
                title.getNumberOfSeasons(),
                title.getNumberOfEpisodes(),
                title.getTmdbVoteAverage(),
                title.getTmdbVoteCount(),
                title.getImdbRating(),
                title.getImdbId() == null ? null : IMDB_TITLE_URL + title.getImdbId(),
                genres,
                offers,
                checkedAt);
    }

    private static Instant oldestFetch(List<TitleAvailability> availability) {
        return availability == null ? null : availability.stream()
                .map(TitleAvailability::getFetchedAt)
                .filter(java.util.Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
    }

    public Responses.WatchlistItemResponse toWatchlistItem(WatchlistItem item, List<TitleAvailability> availability) {
        return toWatchlistItem(item, availability, null);
    }

    /**
     * @param progress counts for a series, or null for a film or when the
     *                 caller has not gathered them
     */
    public Responses.WatchlistItemResponse toWatchlistItem(WatchlistItem item,
                                                           List<TitleAvailability> availability,
                                                           Responses.SeriesProgress progress) {
        return new Responses.WatchlistItemResponse(
                item.getId(),
                item.getStatus(),
                item.isFavorite(),
                item.getPriority(),
                item.getUserRating(),
                item.getNotes(),
                item.getAddedAt(),
                item.getStartedAt(),
                item.getCompletedAt(),
                // A watchlist row carries whatever offers were passed in and
                // no record of when they were fetched, so the oldest of them
                // is the honest answer, and no offers means no claim at all.
                toTitle(item.getTitle(), availability, oldestFetch(availability)),
                progress);
    }

    public Responses.SeriesProgress toSeriesProgress(SeriesProgressCounts counts) {
        return new Responses.SeriesProgress(
                (int) counts.watchedEpisodes(),
                (int) counts.airedEpisodes(),
                counts.percentComplete());
    }

    public Responses.EpisodeResponse toEpisode(Episode episode, boolean watched, int watchCount) {
        return new Responses.EpisodeResponse(
                episode.getId(),
                episode.getSeasonNumber(),
                episode.getEpisodeNumber(),
                episode.code(),
                episode.getName(),
                episode.getOverview(),
                episode.getAirDate(),
                episode.getRuntimeMinutes(),
                images.still(episode.getStillPath()),
                watched,
                watchCount,
                episode.hasAired());
    }

    public Responses.SeasonResponse toSeason(EpisodeListService.SeasonWithEpisodes season) {
        List<Responses.EpisodeResponse> episodes = season.episodes().stream()
                .map(episode -> toEpisode(episode, season.isWatched(episode), season.watchCount(episode)))
                .toList();

        Season source = season.season();
        return new Responses.SeasonResponse(
                source == null ? null : source.getId(),
                season.seasonNumber(),
                source == null ? null : source.getName(),
                source == null ? null : source.getOverview(),
                source == null ? null : source.getAirDate(),
                source == null ? null : images.poster(source.getPosterPath()),
                season.airedEpisodes(),
                season.watchedEpisodes(),
                episodes);
    }

    public Responses.SeasonsResponse toSeasons(EpisodeListService.SeasonListing listing) {
        return new Responses.SeasonsResponse(
                listing.title().getId(),
                listing.title().getPrimaryTitle(),
                listing.seasons().stream().map(this::toSeason).toList());
    }

    public Responses.UpNextResponse toUpNext(UpNextService.UpNext entry) {
        return new Responses.UpNextResponse(
                entry.title().getId(),
                entry.title().getPrimaryTitle(),
                images.poster(entry.title().getPosterPath()),
                entry.nextEpisode().getId(),
                entry.nextEpisode().code(),
                entry.nextEpisode().getName(),
                images.still(entry.nextEpisode().getStillPath()),
                entry.watchedEpisodes(),
                entry.airedEpisodes(),
                entry.lastWatchedAt());
    }

    public Responses.BulkMarkResponse toBulkMark(WatchlistService.BulkMarkResult result) {
        return new Responses.BulkMarkResponse(
                result.titleId(),
                result.newlyMarked(),
                result.alreadyWatched(),
                result.watchedEpisodes(),
                result.airedEpisodes());
    }

    public Responses.WatchEventResponse toWatchEvent(WatchEvent event) {
        return new Responses.WatchEventResponse(
                event.getId(),
                event.getTitle().getId(),
                event.getTitle().getPrimaryTitle(),
                event.getEpisode() == null ? null : event.getEpisode().getId(),
                event.getEpisode() == null ? null : event.getEpisode().code(),
                event.getEpisode() == null ? null : event.getEpisode().getName(),
                event.getWatchedAt(),
                event.getMinutesWatched(),
                event.isRewatch(),
                event.getStreamingService() == null ? null : event.getStreamingService().getName());
    }

    public Responses.StreamingServiceResponse toService(StreamingService service) {
        return new Responses.StreamingServiceResponse(
                service.getId(), service.getSlug(), service.getName(),
                images.logo(service.getLogoPath()), service.isSupportsSync());
    }

    public Responses.LinkedAccountResponse toLinkedAccount(LinkedStreamingAccount account) {
        return new Responses.LinkedAccountResponse(
                account.getId(),
                toService(account.getStreamingService()),
                account.getAccountLabel(),
                account.getStatus(),
                account.isSyncEnabled(),
                account.getLastSyncAt(),
                account.getLastSyncError());
    }
}
