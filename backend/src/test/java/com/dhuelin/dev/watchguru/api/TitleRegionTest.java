package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.ApiMapper;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.catalog.service.CatalogService;
import com.dhuelin.dev.watchguru.config.AuthProperties;
import com.dhuelin.dev.watchguru.config.TmdbProperties;
import com.dhuelin.dev.watchguru.security.CurrentUserService;
import com.dhuelin.dev.watchguru.security.SecurityConfig;
import com.dhuelin.dev.watchguru.security.TrustedIssuers;
import com.dhuelin.dev.watchguru.security.session.AccessTokenIssuer;
import com.dhuelin.dev.watchguru.streaming.service.AvailabilityService;
import com.dhuelin.dev.watchguru.support.TestAuth;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.service.EpisodeListService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Which country's streaming offers a title detail is answered with.
 *
 * <p>This used to fall straight from "no query parameter" to the configured
 * default, which meant everyone outside that one country was told where to
 * watch something on a service that does not carry it there. The user's own
 * region sits between the two, and these tests pin that order.
 */
@WebMvcTest(TitleController.class)
@Import({SecurityConfig.class, TrustedIssuers.class, AccessTokenIssuer.class,
        TitleRegionTest.TestAuthConfig.class})
class TitleRegionTest {

    private static final String DEFAULT_REGION = "US";

    @TestConfiguration(proxyBeanMethods = false)
    static class TestAuthConfig {
        @Bean
        AuthProperties authProperties() {
            return TestAuth.properties();
        }

        @Bean
        TmdbProperties tmdbProperties() {
            return new TmdbProperties(
                    "https://api.themoviedb.org/3",
                    "https://image.tmdb.org/t/p",
                    "test-token",
                    "en-US",
                    DEFAULT_REGION,
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(5),
                    new TmdbProperties.Retry(3, Duration.ofMillis(250), Duration.ofSeconds(4), Duration.ofSeconds(10)),
                    new TmdbProperties.CircuitBreaker(5, Duration.ofSeconds(30)),
                    new TmdbProperties.Search(Duration.ofSeconds(60), 1000, 2));
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CatalogService catalog;
    @MockitoBean
    private EpisodeListService episodeList;
    @MockitoBean
    private CurrentUserService currentUser;
    @MockitoBean
    private AvailabilityService availability;
    @MockitoBean
    private TitleRepository titles;
    @MockitoBean
    private ApiMapper mapper;

    private Title theTitle() {
        Title title = new Title(1396L, TitleType.TV_SERIES, "Breaking Bad");
        title.setId(7L);
        when(titles.findById(7L)).thenReturn(Optional.of(title));
        when(availability.offersFor(any(), any())).thenReturn(List.of());
        return title;
    }

    private AppUser userInRegion(String region) {
        AppUser user = new AppUser("viewer@example.com", "Viewer");
        user.setId(42L);
        user.setRegion(region);
        when(currentUser.require()).thenReturn(user);
        return user;
    }

    @Test
    @DisplayName("an explicit region wins over the profile, and is normalised to upper case")
    void explicitRegionWins() throws Exception {
        Title title = theTitle();
        userInRegion("CH");

        mvc.perform(get("/api/v1/titles/7?region=de").with(jwt()))
                .andExpect(status().isOk());

        verify(availability).offersFor(eq(title), eq("DE"));
    }

    @Test
    @DisplayName("without a region parameter the signed-in user's profile decides")
    void profileRegionIsUsed() throws Exception {
        Title title = theTitle();
        userInRegion("CH");

        mvc.perform(get("/api/v1/titles/7").with(jwt()))
                .andExpect(status().isOk());

        // The bug this pins: "US" here meant Swiss users saw American offers.
        verify(availability).offersFor(eq(title), eq("CH"));
    }

    @Test
    @DisplayName("a lower-case profile region is normalised, not passed through")
    void profileRegionIsNormalised() throws Exception {
        Title title = theTitle();
        userInRegion("gb");

        mvc.perform(get("/api/v1/titles/7").with(jwt()))
                .andExpect(status().isOk());

        verify(availability).offersFor(eq(title), eq("GB"));
    }

    @Test
    @DisplayName("only a user with no region of their own falls back to the configured default")
    void blankProfileRegionFallsBackToDefault() throws Exception {
        Title title = theTitle();
        userInRegion("  ");

        mvc.perform(get("/api/v1/titles/7").with(jwt()))
                .andExpect(status().isOk());

        verify(availability).offersFor(eq(title), eq(DEFAULT_REGION));
    }
}
