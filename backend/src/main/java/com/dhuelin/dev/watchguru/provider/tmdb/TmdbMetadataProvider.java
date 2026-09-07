package com.dhuelin.dev.watchguru.provider.tmdb;

import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.config.TmdbProperties;
import com.dhuelin.dev.watchguru.provider.MetadataProvider;
import com.dhuelin.dev.watchguru.provider.MetadataProviderException;
import com.dhuelin.dev.watchguru.provider.MetadataProviderNotConfiguredException;
import com.dhuelin.dev.watchguru.provider.model.ProviderEpisode;
import com.dhuelin.dev.watchguru.provider.model.ProviderGenre;
import com.dhuelin.dev.watchguru.provider.model.ProviderOffer;
import com.dhuelin.dev.watchguru.provider.model.ProviderSearchPage;
import com.dhuelin.dev.watchguru.provider.model.ProviderSeasonDetail;
import com.dhuelin.dev.watchguru.provider.model.ProviderSeasonSummary;
import com.dhuelin.dev.watchguru.provider.model.ProviderTitleDetail;
import com.dhuelin.dev.watchguru.provider.model.ProviderTitleSummary;
import com.dhuelin.dev.watchguru.provider.model.ProviderWatchService;
import com.dhuelin.dev.watchguru.streaming.domain.OfferType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** {@link MetadataProvider} backed by the TMDB v3 REST API. */
@Component
public class TmdbMetadataProvider implements MetadataProvider {

    private static final Logger log = LoggerFactory.getLogger(TmdbMetadataProvider.class);

    private final RestClient client;
    private final TmdbProperties properties;

    public TmdbMetadataProvider(RestClient tmdbRestClient, TmdbProperties properties) {
        this.client = tmdbRestClient;
        this.properties = properties;
    }

    @Override
    public String sourceName() {
        return "tmdb";
    }

    @Override
    public ProviderSearchPage search(String query, int page, String language) {
        requireToken();
        TmdbResponses.MultiSearch response = get(uriBuilder -> uriBuilder
                        .path("/search/multi")
                        .queryParam("query", query)
                        .queryParam("page", Math.max(page, 1))
                        .queryParam("language", language(language))
                        .queryParam("include_adult", false)
                        .build(),
                TmdbResponses.MultiSearch.class,
                "search");

        if (response == null || response.results() == null) {
            return new ProviderSearchPage(List.of(), page, 0, 0);
        }

        // /search/multi also returns people; only movies and series are catalogued.
        List<ProviderTitleSummary> summaries = response.results().stream()
                .map(this::toSummary)
                .filter(Objects::nonNull)
                .toList();

        return new ProviderSearchPage(summaries, response.page(), response.totalPages(), response.totalResults());
    }

    @Override
    public ProviderTitleDetail fetchDetail(TitleType titleType, long providerId, String language) {
        requireToken();
        TmdbResponses.TitleDetail detail = get(uriBuilder -> uriBuilder
                        .path("/{type}/{id}")
                        .queryParam("language", language(language))
                        // TV detail carries imdb_id only under external_ids.
                        .queryParam("append_to_response", "external_ids")
                        .build(titleType.tmdbPath(), providerId),
                TmdbResponses.TitleDetail.class,
                "detail");

        if (detail == null) {
            throw new MetadataProviderException(
                    "TMDB returned no detail for " + titleType + " " + providerId);
        }
        return toDetail(titleType, detail);
    }

    @Override
    public ProviderSeasonDetail fetchSeason(long seriesProviderId, int seasonNumber, String language) {
        requireToken();
        TmdbResponses.SeasonDetail season = get(uriBuilder -> uriBuilder
                        .path("/tv/{id}/season/{seasonNumber}")
                        .queryParam("language", language(language))
                        .build(seriesProviderId, seasonNumber),
                TmdbResponses.SeasonDetail.class,
                "season");

        if (season == null) {
            throw new MetadataProviderException(
                    "TMDB returned no season " + seasonNumber + " for series " + seriesProviderId);
        }

        ProviderSeasonSummary summary = new ProviderSeasonSummary(
                season.id(),
                season.seasonNumber() != null ? season.seasonNumber() : seasonNumber,
                season.name(),
                season.overview(),
                parseDate(season.airDate()),
                season.episodes() == null ? 0 : season.episodes().size(),
                season.posterPath());

        List<ProviderEpisode> episodes = season.episodes() == null ? List.of()
                : season.episodes().stream()
                .map(e -> new ProviderEpisode(
                        e.id(),
                        e.seasonNumber() != null ? e.seasonNumber() : summary.seasonNumber(),
                        e.episodeNumber(),
                        e.name(),
                        e.overview(),
                        parseDate(e.airDate()),
                        e.runtime(),
                        e.stillPath(),
                        e.voteAverage()))
                .filter(e -> e.episodeNumber() != null)
                .toList();

        return new ProviderSeasonDetail(summary, episodes);
    }

    @Override
    public List<ProviderOffer> fetchOffers(TitleType titleType, long providerId, String region) {
        requireToken();
        String resolvedRegion = region(region);

        TmdbResponses.WatchProviders response = get(uriBuilder -> uriBuilder
                        .path("/{type}/{id}/watch/providers")
                        .build(titleType.tmdbPath(), providerId),
                TmdbResponses.WatchProviders.class,
                "watch providers");

        if (response == null || response.results() == null) {
            return List.of();
        }
        TmdbResponses.RegionOffers offers = response.results().get(resolvedRegion);
        if (offers == null) {
            return List.of();
        }

        List<ProviderOffer> result = new ArrayList<>();
        collectOffers(result, offers.flatrate(), OfferType.FLATRATE, offers.link());
        collectOffers(result, offers.rent(), OfferType.RENT, offers.link());
        collectOffers(result, offers.buy(), OfferType.BUY, offers.link());
        collectOffers(result, offers.ads(), OfferType.ADS, offers.link());
        collectOffers(result, offers.free(), OfferType.FREE, offers.link());
        return result;
    }

    @Override
    public List<ProviderWatchService> listWatchServices(String region) {
        requireToken();
        String resolvedRegion = region(region);

        // The movie and TV provider lists overlap heavily but are not identical,
        // so both are merged and de-duplicated by provider id.
        Map<Long, ProviderWatchService> merged = new java.util.LinkedHashMap<>();
        for (String type : List.of("movie", "tv")) {
            TmdbResponses.WatchProviderList response = get(uriBuilder -> uriBuilder
                            .path("/watch/providers/{type}")
                            .queryParam("watch_region", resolvedRegion)
                            .build(type),
                    TmdbResponses.WatchProviderList.class,
                    "watch provider list");
            if (response == null || response.results() == null) {
                continue;
            }
            for (TmdbResponses.WatchProvider provider : response.results()) {
                if (provider.providerId() == null) {
                    continue;
                }
                merged.putIfAbsent(provider.providerId(), new ProviderWatchService(
                        provider.providerId(), provider.providerName(), provider.logoPath()));
            }
        }
        return List.copyOf(merged.values());
    }

    // ---------- mapping ----------

    private ProviderTitleSummary toSummary(TmdbResponses.MultiSearchResult result) {
        TitleType type = mediaType(result.mediaType());
        if (type == null || result.id() == null) {
            return null;
        }
        boolean movie = type == TitleType.MOVIE;
        return new ProviderTitleSummary(
                result.id(),
                type,
                movie ? result.title() : result.name(),
                movie ? result.originalTitle() : result.originalName(),
                parseDate(movie ? result.releaseDate() : result.firstAirDate()),
                result.overview(),
                result.posterPath(),
                result.backdropPath(),
                result.originalLanguage(),
                result.voteAverage(),
                result.voteCount(),
                result.popularity(),
                Boolean.TRUE.equals(result.adult()),
                result.genreIds() == null ? List.of() : result.genreIds());
    }

    private ProviderTitleDetail toDetail(TitleType titleType, TmdbResponses.TitleDetail detail) {
        boolean movie = titleType == TitleType.MOVIE;

        ProviderTitleSummary summary = new ProviderTitleSummary(
                detail.id(),
                titleType,
                movie ? detail.title() : detail.name(),
                movie ? detail.originalTitle() : detail.originalName(),
                parseDate(movie ? detail.releaseDate() : detail.firstAirDate()),
                detail.overview(),
                detail.posterPath(),
                detail.backdropPath(),
                detail.originalLanguage(),
                detail.voteAverage(),
                detail.voteCount(),
                detail.popularity(),
                Boolean.TRUE.equals(detail.adult()),
                List.of());

        List<ProviderGenre> genres = detail.genres() == null ? List.of()
                : detail.genres().stream()
                .filter(g -> g.id() != null)
                .map(g -> new ProviderGenre(g.id(), g.name()))
                .toList();

        List<ProviderSeasonSummary> seasons = detail.seasons() == null ? List.of()
                : detail.seasons().stream()
                .filter(s -> s.seasonNumber() != null)
                .map(s -> new ProviderSeasonSummary(
                        s.id(), s.seasonNumber(), s.name(), s.overview(),
                        parseDate(s.airDate()), s.episodeCount(), s.posterPath()))
                .toList();

        return new ProviderTitleDetail(
                summary,
                resolveImdbId(detail),
                detail.tagline(),
                detail.homepage(),
                detail.status(),
                resolveRuntime(movie, detail),
                detail.numberOfSeasons(),
                detail.numberOfEpisodes(),
                parseDate(detail.lastAirDate()),
                genres,
                seasons);
    }

    /** Movies expose {@code imdb_id} directly; series only through {@code external_ids}. */
    private static String resolveImdbId(TmdbResponses.TitleDetail detail) {
        if (detail.imdbId() != null && !detail.imdbId().isBlank()) {
            return detail.imdbId();
        }
        if (detail.externalIds() != null
                && detail.externalIds().imdbId() != null
                && !detail.externalIds().imdbId().isBlank()) {
            return detail.externalIds().imdbId();
        }
        return null;
    }

    /** For a series, the first entry of {@code episode_run_time} is the typical length. */
    private static Integer resolveRuntime(boolean movie, TmdbResponses.TitleDetail detail) {
        if (movie) {
            return detail.runtime();
        }
        List<Integer> runTimes = detail.episodeRunTime();
        return runTimes == null || runTimes.isEmpty() ? null : runTimes.getFirst();
    }

    private static void collectOffers(List<ProviderOffer> target,
                                      List<TmdbResponses.WatchProvider> providers,
                                      OfferType offerType,
                                      String link) {
        if (providers == null) {
            return;
        }
        for (TmdbResponses.WatchProvider provider : providers) {
            if (provider.providerId() == null) {
                continue;
            }
            target.add(new ProviderOffer(
                    provider.providerId(), provider.providerName(), provider.logoPath(), offerType, link));
        }
    }

    private static TitleType mediaType(String mediaType) {
        if (mediaType == null) {
            return null;
        }
        return switch (mediaType) {
            case "movie" -> TitleType.MOVIE;
            case "tv" -> TitleType.TV_SERIES;
            default -> null;
        };
    }

    /** TMDB sends {@code ""} rather than omitting unknown dates. */
    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            log.debug("Ignoring unparseable TMDB date '{}'", value);
            return null;
        }
    }

    // ---------- plumbing ----------

    private <T> T get(java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uriFunction,
                      Class<T> responseType,
                      String operation) {
        try {
            return client.get().uri(uriFunction).retrieve().body(responseType);
        } catch (RestClientException e) {
            throw new MetadataProviderException("TMDB " + operation + " request failed", e);
        }
    }

    private void requireToken() {
        if (!properties.isConfigured()) {
            throw new MetadataProviderNotConfiguredException(
                    "TMDB API token is not configured; set the TMDB_API_TOKEN environment variable");
        }
    }

    private String language(String language) {
        return language == null || language.isBlank() ? properties.defaultLanguage() : language;
    }

    private String region(String region) {
        return region == null || region.isBlank() ? properties.defaultRegion() : region.toUpperCase();
    }

    /** Absolute URL for a TMDB image path at the requested size. */
    public String imageUrl(String path, String size) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return properties.imageBaseUrl() + "/" + size + path;
    }
}
