package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.catalog.service.CatalogService;
import com.dhuelin.dev.watchguru.provider.model.ProviderSearchPage;
import com.dhuelin.dev.watchguru.provider.model.ProviderTitleSummary;
import com.dhuelin.dev.watchguru.security.session.AccessTokenIssuer;
import com.dhuelin.dev.watchguru.support.PostgresTestConfig;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;
import com.dhuelin.dev.watchguru.tracking.domain.WatchlistItem;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchlistItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the library tells the apps about itself: that a rating can be taken
 * back, and that search knows what is already tracked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(PostgresTestConfig.class)
class LibraryStateOverHttpTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AccessTokenIssuer tokens;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private TitleRepository titles;
    @Autowired
    private WatchlistItemRepository items;

    @MockitoBean
    private CatalogService catalog;

    private String bearer;
    private Title film;
    private WatchlistItem item;

    @BeforeEach
    void setUp() {
        long seed = System.nanoTime();
        AppUser user = users.save(new AppUser("state-" + seed + "@example.com", "State"));
        film = titles.save(new Title(9_600_000L + seed % 100_000, TitleType.MOVIE, "Arrival " + seed));
        WatchlistItem rated = new WatchlistItem(user, film, WatchStatus.COMPLETED);
        rated.setUserRating(new BigDecimal("8"));
        item = items.save(rated);
        bearer = "Bearer " + tokens.issue(user, Instant.now()).token();
    }

    private ProviderTitleSummary hit(long providerId, TitleType type) {
        return new ProviderTitleSummary(providerId, type, "Hit " + providerId, null, null, null,
                null, null, null, null, null, null, false, List.of());
    }

    @Test
    @DisplayName("clearRating removes a rating")
    void clearsRating() throws Exception {
        mvc.perform(patch("/api/v1/me/watchlist/" + item.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearRating\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userRating").doesNotExist());

        assertThat(items.findById(item.getId()).orElseThrow().getUserRating()).isNull();
    }

    @Test
    @DisplayName("a null rating still leaves the rating alone")
    void nullRatingIsUnchanged() throws Exception {
        mvc.perform(patch("/api/v1/me/watchlist/" + item.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":null,\"status\":\"WATCHING\"}"))
                .andExpect(status().isOk());

        assertThat(items.findById(item.getId()).orElseThrow().getUserRating()).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("rating and clearing in one request is refused")
    void ratingAndClearIsRefused() throws Exception {
        mvc.perform(patch("/api/v1/me/watchlist/" + item.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":6,\"clearRating\":true}"))
                .andExpect(status().isBadRequest());

        assertThat(items.findById(item.getId()).orElseThrow().getUserRating()).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("search marks a title already in the library, and only that one")
    void searchMarksTrackedTitles() throws Exception {
        // The same TMDB id as a series is a different title, and untracked.
        when(catalog.search(anyString(), anyInt(), any())).thenReturn(new ProviderSearchPage(
                List.of(hit(film.getTmdbId(), TitleType.MOVIE),
                        hit(film.getTmdbId(), TitleType.TV_SERIES),
                        hit(1L, TitleType.MOVIE)),
                1, 1, 3));

        mvc.perform(get("/api/v1/titles/search").param("query", "arrival")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].library.itemId").value(item.getId()))
                .andExpect(jsonPath("$.results[0].library.status").value("COMPLETED"))
                .andExpect(jsonPath("$.results[1].library").doesNotExist())
                .andExpect(jsonPath("$.results[2].library").doesNotExist());
    }

    @Test
    @DisplayName("trending marks tracked titles too")
    void trendingMarksTrackedTitles() throws Exception {
        when(catalog.trending(anyInt(), any())).thenReturn(new ProviderSearchPage(
                List.of(hit(film.getTmdbId(), TitleType.MOVIE)), 1, 1, 1));

        mvc.perform(get("/api/v1/titles/trending").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].library.itemId").value(item.getId()));
    }
}
