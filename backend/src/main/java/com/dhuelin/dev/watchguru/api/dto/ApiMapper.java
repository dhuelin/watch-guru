package com.dhuelin.dev.watchguru.api.dto;

import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.provider.model.ProviderSearchPage;
import com.dhuelin.dev.watchguru.streaming.domain.LinkedStreamingAccount;
import com.dhuelin.dev.watchguru.streaming.domain.StreamingService;
import com.dhuelin.dev.watchguru.streaming.domain.TitleAvailability;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.domain.WatchEvent;
import com.dhuelin.dev.watchguru.tracking.domain.WatchlistItem;
import org.springframework.stereotype.Component;

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

    public Responses.TitleResponse toTitle(Title title, List<TitleAvailability> availability) {
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
                offers);
    }

    public Responses.WatchlistItemResponse toWatchlistItem(WatchlistItem item, List<TitleAvailability> availability) {
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
                toTitle(item.getTitle(), availability));
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
