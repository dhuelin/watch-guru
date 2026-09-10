package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.ApiMapper;
import com.dhuelin.dev.watchguru.catalog.repository.EpisodeRepository;
import com.dhuelin.dev.watchguru.config.AuthProperties;
import com.dhuelin.dev.watchguru.security.CurrentUserService;
import com.dhuelin.dev.watchguru.security.SecurityConfig;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.service.WatchlistService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import com.dhuelin.dev.watchguru.security.TrustedIssuers;
import com.dhuelin.dev.watchguru.security.session.AccessTokenIssuer;
import com.dhuelin.dev.watchguru.support.TestAuth;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * That the watchlist endpoints are actually behind authentication, and that the
 * user they act on comes from the token.
 *
 * <p>Before this change the user id was a path variable, so changing a number in
 * the URL read somebody else's watchlist. The second test below is the one that
 * would have caught that.
 */
@WebMvcTest(WatchlistController.class)
@Import({SecurityConfig.class, TrustedIssuers.class, AccessTokenIssuer.class,
        WatchlistControllerSecurityTest.TestAuthConfig.class})
class WatchlistControllerSecurityTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class TestAuthConfig {
        @Bean
        AuthProperties authProperties() {
            // A real issuer URI so the config validates; the decoder resolves
            // discovery lazily and jwt() bypasses it, so nothing is fetched.
            return TestAuth.properties();
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WatchlistService watchlist;
    @MockitoBean
    private EpisodeRepository episodes;
    @MockitoBean
    private CurrentUserService currentUser;
    @MockitoBean
    private ApiMapper mapper;

    private static AppUser userWithId(long id) {
        AppUser user = new AppUser("viewer" + id + "@example.com", "Viewer " + id);
        user.setId(id);
        return user;
    }

    @Test
    @DisplayName("no token means 401, not an empty list")
    void anonymousIsUnauthorised() throws Exception {
        mvc.perform(get("/api/v1/me/watchlist"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the watchlist read is scoped to the token's user, and the URL cannot say otherwise")
    void watchlistIsScopedToTheTokenUser() throws Exception {
        Page<com.dhuelin.dev.watchguru.tracking.domain.WatchlistItem> empty =
                new PageImpl<>(List.of());
        when(currentUser.require()).thenReturn(userWithId(42L));
        when(watchlist.list(any(), any(), any())).thenReturn(empty);

        // Note there is no user id in this URL to tamper with -- that is the
        // point. The query string below is an attempt to smuggle one in.
        mvc.perform(get("/api/v1/me/watchlist?userId=99").with(jwt()))
                .andExpect(status().isOk());

        // The service is called with 42 (from the token), never 99.
        verify(watchlist).list(eq(42L), any(), any());
    }

    @Test
    @DisplayName("a token identifying a different user reads that user's list, not a fixed one")
    void differentTokenResolvesToDifferentUser() throws Exception {
        when(currentUser.require()).thenReturn(userWithId(7L));
        when(watchlist.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/me/watchlist").with(jwt()))
                .andExpect(status().isOk());

        verify(watchlist).list(eq(7L), any(), any());
    }
}
