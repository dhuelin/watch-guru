package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.config.AuthProperties;
import com.dhuelin.dev.watchguru.security.SecurityConfig;
import com.dhuelin.dev.watchguru.security.TrustedIssuers;
import com.dhuelin.dev.watchguru.security.session.AccessTokenIssuer;
import com.dhuelin.dev.watchguru.streaming.plex.PlexWebhookService;
import com.dhuelin.dev.watchguru.streaming.plex.WebhookAuthenticationException;
import com.dhuelin.dev.watchguru.support.TestAuth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The webhook endpoint as a Plex server actually reaches it.
 *
 * <p>The point of this slice is the filter chain rather than the handler: a
 * Plex server sends no bearer token and cannot be taught to, so if this path
 * were not permitted the integration would fail with a 401 that no test of the
 * service below would ever see.
 */
@WebMvcTest(PlexWebhookController.class)
@Import({SecurityConfig.class, TrustedIssuers.class, AccessTokenIssuer.class,
        ApiExceptionHandler.class, PlexWebhookEndpointTest.TestAuthConfig.class})
class PlexWebhookEndpointTest {

    private static final String PAYLOAD = """
            {"event": "media.scrobble", "user": true,
             "Metadata": {"type": "movie", "title": "Heat", "year": 1995, "ratingKey": "9"}}
            """;

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
    private PlexWebhookService webhooks;

    @Test
    @DisplayName("Plex's own multipart delivery is accepted without any bearer token")
    void acceptsMultipartWithoutAToken() throws Exception {
        when(webhooks.receive(any(), any())).thenReturn(PlexWebhookService.Outcome.RECORDED);
        MockPart payload = new MockPart("payload", PAYLOAD.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/v1/webhooks/plex/{token}", "12.secret").part(payload))
                .andExpect(status().isNoContent());

        verify(webhooks).receive(eq("12.secret"), eq(PAYLOAD));
    }

    @Test
    @DisplayName("the same delivery as plain JSON is accepted too")
    void acceptsJson() throws Exception {
        when(webhooks.receive(any(), any())).thenReturn(PlexWebhookService.Outcome.RECORDED);

        mvc.perform(post("/api/v1/webhooks/plex/{token}", "12.secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isNoContent());

        verify(webhooks).receive(eq("12.secret"), eq(PAYLOAD));
    }

    @Test
    @DisplayName("a token that opens nothing is 401, whatever the reason")
    void rejectsABadToken() throws Exception {
        when(webhooks.receive(any(), any()))
                .thenThrow(new WebhookAuthenticationException("Unknown webhook token"));

        mvc.perform(post("/api/v1/webhooks/plex/{token}", "12.wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a scrobble that could not be matched is still a 204: nothing for Plex to retry")
    void acceptsWhatItCouldNotMatch() throws Exception {
        when(webhooks.receive(any(), any())).thenReturn(PlexWebhookService.Outcome.UNMATCHED);

        mvc.perform(post("/api/v1/webhooks/plex/{token}", "12.secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("something that is not a Plex payload is 400")
    void rejectsRubbish() throws Exception {
        when(webhooks.receive(any(), any()))
                .thenThrow(new IllegalArgumentException("Plex payload could not be read"));

        mvc.perform(post("/api/v1/webhooks/plex/{token}", "12.secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a multipart delivery with no payload part is 400 rather than a 500")
    void rejectsAMissingPayloadPart() throws Exception {
        mvc.perform(multipart("/api/v1/webhooks/plex/{token}", "12.secret")
                        .part(new MockPart("thumb", new byte[] {1, 2, 3})))
                .andExpect(status().isBadRequest());
    }
}
