package com.dhuelin.dev.watchguru.catalog.service;

import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.EpisodeRepository;
import com.dhuelin.dev.watchguru.catalog.repository.GenreRepository;
import com.dhuelin.dev.watchguru.catalog.repository.SeasonRepository;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.config.CatalogProperties;
import com.dhuelin.dev.watchguru.config.TmdbProperties;
import com.dhuelin.dev.watchguru.provider.MetadataProvider;
import com.dhuelin.dev.watchguru.provider.MetadataProviderException;
import com.dhuelin.dev.watchguru.provider.UpstreamUnavailableException;
import com.dhuelin.dev.watchguru.provider.model.ProviderSearchPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How the catalog behaves when TMDB is slow, rate limiting, or down.
 *
 * <p>The user's watchlist, progress and history are entirely local. None of it
 * should become unreadable because a third party is having a bad day.
 */
class CatalogServiceResilienceTest {

    private MetadataProvider provider;
    private TitleRepository titles;
    private CatalogService catalog;

    @BeforeEach
    void setUp() {
        provider = mock(MetadataProvider.class);
        titles = mock(TitleRepository.class);

        TmdbProperties tmdb = new TmdbProperties(
                "https://api.themoviedb.org/3", "https://image.tmdb.org/t/p", "token",
                "en-US", "US", Duration.ofSeconds(5), Duration.ofSeconds(10),
                new TmdbProperties.Retry(3, Duration.ofMillis(250), Duration.ofSeconds(4), Duration.ofSeconds(10)),
                new TmdbProperties.CircuitBreaker(5, Duration.ofSeconds(30)),
                new TmdbProperties.Search(Duration.ofSeconds(60), 1000, 2));

        catalog = new CatalogService(
                titles,
                mock(GenreRepository.class),
                mock(SeasonRepository.class),
                mock(EpisodeRepository.class),
                provider,
                new CatalogProperties(Duration.ofDays(7), Duration.ofDays(1)),
                tmdb);
    }

    @Test
    @DisplayName("a one-character query never reaches the provider")
    void shortQueriesAreNotSentUpstream() {
        ProviderSearchPage page = catalog.search("b", 1, "en-US");

        // Search-as-you-type sends "b", "br", "bre" on the way to a real query.
        // Each would match a large slice of the catalog and tell nobody anything.
        assertThat(page.results()).isEmpty();
        verify(provider, never()).search(anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("a blank query never reaches the provider")
    void blankQueriesAreNotSentUpstream() {
        assertThat(catalog.search("   ", 1, "en-US").results()).isEmpty();
        verify(provider, never()).search(anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("a real query is trimmed and forwarded")
    void realQueriesAreForwardedTrimmed() {
        when(provider.search(anyString(), anyInt(), any()))
                .thenReturn(new ProviderSearchPage(List.of(), 1, 1, 0));

        catalog.search("  breaking bad  ", 1, "en-US");

        verify(provider).search("breaking bad", 1, "en-US");
    }

    @Test
    @DisplayName("a stale title is served when the provider cannot be refreshed")
    void staleTitleIsServedWhenProviderFails() {
        Title stale = new Title(1396L, TitleType.TV_SERIES, "Breaking Bad");
        stale.setDetailFetchedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        when(titles.findByTmdbIdAndTitleType(1396L, TitleType.TV_SERIES)).thenReturn(Optional.of(stale));
        when(provider.fetchDetail(any(), anyLong(), any()))
                .thenThrow(new UpstreamUnavailableException("circuit open"));

        Title result = catalog.importTitle(TitleType.TV_SERIES, 1396L, "en-US");

        // A month-old runtime is worth far more than an error page, given the
        // user's own progress data is local and perfectly current.
        assertThat(result).isSameAs(stale);
    }

    @Test
    @DisplayName("a title we have never seen still fails when the provider is down")
    void unknownTitleStillFails() {
        when(titles.findByTmdbIdAndTitleType(anyLong(), any())).thenReturn(Optional.empty());
        when(provider.fetchDetail(any(), anyLong(), any()))
                .thenThrow(new MetadataProviderException("upstream down"));

        // There is nothing stale to fall back to, so pretending otherwise would
        // mean inventing a title.
        assertThatThrownBy(() -> catalog.importTitle(TitleType.MOVIE, 999L, "en-US"))
                .isInstanceOf(MetadataProviderException.class);
    }

    @Test
    @DisplayName("a fresh title is served without calling the provider at all")
    void freshTitleSkipsTheProvider() {
        Title fresh = new Title(1396L, TitleType.TV_SERIES, "Breaking Bad");
        fresh.setDetailFetchedAt(Instant.now());
        when(titles.findByTmdbIdAndTitleType(1396L, TitleType.TV_SERIES)).thenReturn(Optional.of(fresh));

        catalog.importTitle(TitleType.TV_SERIES, 1396L, "en-US");

        verify(provider, never()).fetchDetail(any(), anyLong(), any());
    }
}
