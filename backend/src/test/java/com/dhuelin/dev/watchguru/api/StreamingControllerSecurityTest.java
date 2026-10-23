package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.ApiMapper;
import com.dhuelin.dev.watchguru.config.AuthProperties;
import com.dhuelin.dev.watchguru.security.CurrentUserService;
import com.dhuelin.dev.watchguru.security.SecurityConfig;
import com.dhuelin.dev.watchguru.security.TrustedIssuers;
import com.dhuelin.dev.watchguru.security.session.AccessTokenIssuer;
import com.dhuelin.dev.watchguru.streaming.repository.LinkedStreamingAccountRepository;
import com.dhuelin.dev.watchguru.streaming.repository.StreamingServiceRepository;
import com.dhuelin.dev.watchguru.streaming.service.AvailabilityService;
import com.dhuelin.dev.watchguru.support.TestAuth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The reconcile endpoint rewrites a table every user shares. With no roles in
 * this service, "signed in" would mean anybody with a Google account, so it is
 * closed to everyone and the work happens on a schedule instead.
 */
@WebMvcTest(StreamingController.class)
@Import({SecurityConfig.class, TrustedIssuers.class, AccessTokenIssuer.class,
        StreamingControllerSecurityTest.TestAuthConfig.class})
class StreamingControllerSecurityTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class TestAuthConfig {
        @Bean
        AuthProperties authProperties() {
            return TestAuth.properties();
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private StreamingServiceRepository services;
    @MockitoBean
    private LinkedStreamingAccountRepository linkedAccounts;
    @MockitoBean
    private AvailabilityService availability;
    @MockitoBean
    private CurrentUserService currentUser;
    @MockitoBean
    private ApiMapper mapper;

    @Test
    @DisplayName("a signed-in user cannot reconcile the shared streaming-service table")
    void reconcileIsClosed() throws Exception {
        mvc.perform(post("/api/v1/streaming-services/reconcile").param("region", "XX").with(jwt()))
                .andExpect(status().isForbidden());

        verify(availability, never()).reconcileServices(any());
    }
}
