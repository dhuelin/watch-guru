package com.dhuelin.dev.watchguru.catalog.service;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.catalog.domain.Genre;
import com.dhuelin.dev.watchguru.catalog.domain.Season;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.EpisodeRepository;
import com.dhuelin.dev.watchguru.catalog.repository.GenreRepository;
import com.dhuelin.dev.watchguru.catalog.repository.SeasonRepository;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.config.CacheConfig;
import com.dhuelin.dev.watchguru.config.CatalogProperties;
import com.dhuelin.dev.watchguru.config.TmdbProperties;
import com.dhuelin.dev.watchguru.provider.MetadataProvider;
import com.dhuelin.dev.watchguru.provider.MetadataProviderException;
import com.dhuelin.dev.watchguru.provider.model.ProviderEpisode;
import com.dhuelin.dev.watchguru.provider.model.ProviderSearchPage;
import com.dhuelin.dev.watchguru.provider.model.ProviderSeasonDetail;
import com.dhuelin.dev.watchguru.provider.model.ProviderSeasonSummary;
import com.dhuelin.dev.watchguru.provider.model.ProviderTitleDetail;
import com.dhuelin.dev.watchguru.provider.model.ProviderTitleSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Local mirror of the metadata provider's catalog.
 *
 * <p>Titles are cached on first use and refreshed once their TTL expires, so
 * routine watchlist browsing does not hit the provider and the app keeps
 * working when the provider is unreachable. Search stubs are stored with a null
 * {@code detailFetchedAt} and filled in when a title is actually opened.
 */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final TitleRepository titles;
    private final GenreRepository genres;
    private final SeasonRepository seasons;
    private final EpisodeRepository episodes;
    private final MetadataProvider provider;
    private final CatalogProperties catalogProperties;
    private final TmdbProperties tmdbProperties;

    public CatalogService(TitleRepository titles,
                          GenreRepository genres,
                          SeasonRepository seasons,
                          EpisodeRepository episodes,
                          MetadataProvider provider,
                          CatalogProperties catalogProperties,
                          TmdbProperties tmdbProperties) {
        this.titles = titles;
        this.genres = genres;
        this.seasons = seasons;
        this.episodes = episodes;
        this.provider = provider;
        this.catalogProperties = catalogProperties;
        this.tmdbProperties = tmdbProperties;
    }

    /**
     * Provider search, cached briefly. Results are not persisted until a title
     * is imported.
     *
     * <p>Two protections sit in front of the provider here. Queries shorter than
     * the configured minimum never leave the building: search-as-you-type sends
     * "b", "br", "bre" on the way to "breaking bad", and the first of those
     * matches most of the catalog while telling the user nothing. And identical
     * queries within the cache TTL are answered locally, which is what collapses
     * the burst a debounced search box still produces.
     *
     * <p>The key is normalised so "Breaking Bad" and "breaking bad " share an
     * entry rather than each costing a call.
     */
    @Cacheable(cacheNames = CacheConfig.SEARCH_CACHE,
            key = "T(java.util.Objects).toString(#query).trim().toLowerCase() + '|' + #page + '|' "
                    + "+ T(java.util.Objects).toString(#language)")
    public ProviderSearchPage search(String query, int page, String language) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() < tmdbProperties.search().minQueryLength()) {
            return new ProviderSearchPage(List.of(), Math.max(page, 1), 0, 0);
        }
        return provider.search(trimmed, page, language);
    }

    /**
     * Returns the local title for a provider id, importing or refreshing it as
     * needed. Series also get their season and episode lists populated.
     */
    @Transactional
    public Title importTitle(TitleType titleType, long providerId, String language) {
        Title title = titles.findByTmdbIdAndTitleType(providerId, titleType).orElse(null);

        if (title != null && !title.needsDetailRefresh(catalogProperties.detailTtl())) {
            return title;
        }

        ProviderTitleDetail detail;
        try {
            detail = provider.fetchDetail(titleType, providerId, language);
        } catch (MetadataProviderException e) {
            if (title != null) {
                // A stale title is worth far more than an error page. The
                // user's own watchlist, progress and history are local and
                // entirely unaffected by TMDB being down; failing here would
                // take all of that offline to avoid showing a runtime that
                // might be a week out of date.
                log.warn("Serving stale metadata for {} {} after provider failure: {}",
                        titleType, providerId, e.getMessage());
                return title;
            }
            throw e;
        }
        if (title == null) {
            title = new Title(providerId, titleType, detail.summary().title());
        }
        applyDetail(title, detail);
        title = titles.save(title);

        if (titleType == TitleType.TV_SERIES) {
            syncSeasons(title, detail.seasons(), language);
        }
        return title;
    }

    /** Creates a lightweight row from a search result without a detail fetch. */
    @Transactional
    public Title saveStub(ProviderTitleSummary summary) {
        Title title = titles.findByTmdbIdAndTitleType(summary.providerId(), summary.titleType())
                .orElseGet(() -> new Title(summary.providerId(), summary.titleType(), summary.title()));
        applySummary(title, summary);
        title.setSummaryFetchedAt(Instant.now());
        return titles.save(title);
    }

    private void applySummary(Title title, ProviderTitleSummary summary) {
        title.setPrimaryTitle(summary.title());
        title.setOriginalTitle(summary.originalTitle());
        title.setOverview(summary.overview());
        title.setPosterPath(summary.posterPath());
        title.setBackdropPath(summary.backdropPath());
        title.setOriginalLanguage(summary.originalLanguage());
        title.setTmdbVoteAverage(summary.voteAverage());
        title.setTmdbVoteCount(summary.voteCount());
        title.setTmdbPopularity(summary.popularity());
        title.setAdult(summary.adult());

        if (summary.titleType() == TitleType.MOVIE) {
            title.setReleaseDate(summary.releaseDate());
        } else {
            title.setFirstAirDate(summary.releaseDate());
        }
    }

    private void applyDetail(Title title, ProviderTitleDetail detail) {
        applySummary(title, detail.summary());
        title.setImdbId(detail.imdbId());
        title.setTagline(detail.tagline());
        title.setHomepage(detail.homepage());
        title.setProductionStatus(detail.productionStatus());
        title.setRuntimeMinutes(detail.runtimeMinutes());
        title.setNumberOfSeasons(detail.numberOfSeasons());
        title.setNumberOfEpisodes(detail.numberOfEpisodes());
        title.setLastAirDate(detail.lastAirDate());
        title.setDetailFetchedAt(Instant.now());

        title.getGenres().clear();
        detail.genres().forEach(g -> title.getGenres().add(
                genres.findByTmdbId(g.providerId())
                        .orElseGet(() -> genres.save(new Genre(g.providerId(), g.name())))));
    }

    /**
     * Brings the season and episode rows for a series in line with the provider.
     *
     * <p>Season 0 holds specials on TMDB and is imported like any other season,
     * but callers that measure progress should exclude it.
     */
    private void syncSeasons(Title title, List<ProviderSeasonSummary> seasonSummaries, String language) {
        for (ProviderSeasonSummary summary : seasonSummaries) {
            Season season = seasons.findByTitleIdAndSeasonNumber(title.getId(), summary.seasonNumber())
                    .orElseGet(() -> new Season(title, summary.seasonNumber()));
            season.setTmdbId(summary.providerId());
            season.setName(summary.name());
            season.setOverview(summary.overview());
            season.setAirDate(summary.airDate());
            season.setEpisodeCount(summary.episodeCount());
            season.setPosterPath(summary.posterPath());
            Season saved = seasons.save(season);

            try {
                syncEpisodes(title, saved, language);
            } catch (RuntimeException e) {
                // One unavailable season should not abort the whole import.
                log.warn("Could not import episodes for {} season {}: {}",
                        title.getPrimaryTitle(), summary.seasonNumber(), e.getMessage());
            }
        }
    }

    private void syncEpisodes(Title title, Season season, String language) {
        ProviderSeasonDetail detail = provider.fetchSeason(title.getTmdbId(), season.getSeasonNumber(), language);
        for (ProviderEpisode source : detail.episodes()) {
            Episode episode = episodes.findBySeasonIdAndEpisodeNumber(season.getId(), source.episodeNumber())
                    .orElseGet(() -> new Episode(season, source.episodeNumber()));
            episode.setTmdbId(source.providerId());
            episode.setName(source.name());
            episode.setOverview(source.overview());
            episode.setAirDate(source.airDate());
            episode.setRuntimeMinutes(source.runtimeMinutes());
            episode.setStillPath(source.stillPath());
            episode.setTmdbVoteAverage(source.voteAverage());
            episodes.save(episode);
        }
    }
}
