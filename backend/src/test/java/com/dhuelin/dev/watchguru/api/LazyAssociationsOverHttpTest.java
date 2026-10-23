package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * That a response can be rendered from entities loaded in a previous request.
 *
 * <p>The mapper reads a watchlist item's title and a title's genres, both lazy.
 * Every other test maps entities it has just saved, which are fully populated in
 * memory, so none of them noticed that a real request -- which loads them fresh
 * -- failed with a {@code LazyInitializationException} once the repository's
 * transaction had closed. The library, the title screen, Up Next and marking an
 * episode all returned 500. These go through the whole HTTP stack with a real
 * session token, the way an app does.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(PostgresTestConfig.class)
class LazyAssociationsOverHttpTest {

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

    private String bearer;
    private Title film;

    @BeforeEach
    void setUp() {
        long seed = System.nanoTime();
        AppUser user = users.save(new AppUser("lazy-" + seed + "@example.com", "Lazy"));
        film = titles.save(new Title(9_500_000L + seed % 100_000, TitleType.MOVIE, "Arrival " + seed));
        items.save(new WatchlistItem(user, film, WatchStatus.WATCHLIST));
        bearer = "Bearer " + tokens.issue(user, Instant.now()).token();
    }

    @Test
    @DisplayName("the library renders its titles")
    void libraryRenders() throws Exception {
        mvc.perform(get("/api/v1/me/watchlist").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title.primaryTitle").value(film.getPrimaryTitle()));
    }

    @Test
    @DisplayName("Up Next renders")
    void upNextRenders() throws Exception {
        mvc.perform(get("/api/v1/me/up-next").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }
}
