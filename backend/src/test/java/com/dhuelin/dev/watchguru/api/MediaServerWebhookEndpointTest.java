package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.config.AuthProperties;
import com.dhuelin.dev.watchguru.security.SecurityConfig;
import com.dhuelin.dev.watchguru.security.TrustedIssuers;
import com.dhuelin.dev.watchguru.security.session.AccessTokenIssuer;
import com.dhuelin.dev.watchguru.streaming.mediaserver.MediaServerWebhookService;
import com.dhuelin.dev.watchguru.streaming.mediaserver.WebhookAuthenticationException;
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
 * The webhook endpoint as a media server actually reaches it.
 *
 * <p>The point of this slice is the filter chain rather than the handler: none
 * of these servers sends a bearer token or can be taught to, so if this path
 * were not permitted the integration would fail with a 401 that no test of the
 * service below would ever see.
 */
@WebMvcTest(MediaServerWebhookController.class)
@Import({SecurityConfig.class, TrustedIssuers.class, AccessTokenIssuer.class,
        ApiExceptionHandler.class, MediaServerWebhookEndpointTest.TestAuthConfig.class})
class MediaServerWebhookEndpointTest {

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
    private MediaServerWebhookService webhooks;

    @Test
    @DisplayName("Plex's multipart delivery is accepted without any bearer token")
    void acceptsMultipartWithoutAToken() throws Exception {
        when(webhooks.receive(any(), any(), any()))
                .thenReturn(MediaServerWebhookService.Outcome.RECORDED);
        MockPart payload = new MockPart("payload", PAYLOAD.getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/v1/webhooks/{service}/{token}", "plex", "12.secret")
                        .part(payload))
                .andExpect(status().isNoContent());

        verify(webhooks).receive(eq("plex"), eq("12.secret"), eq(PAYLOAD));
    }

    @Test
    @DisplayName("Jellyfin's and Emby's JSON deliveries are accepted too")
    void acceptsJson() throws Exception {
        when(webhooks.receive(any(), any(), any()))
                .thenReturn(MediaServerWebhookService.Outcome.RECORDED);

        for (String service : new String[] {"jellyfin", "emby"}) {
            mvc.perform(post("/api/v1/webhooks/{service}/{token}", service, "12.secret")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PAYLOAD))
                    .andExpect(status().isNoContent());

            verify(webhooks).receive(eq(service), eq("12.secret"), eq(PAYLOAD));
        }
    }

    @Test
    @DisplayName("a token that opens nothing is 401, whatever the reason")
    void rejectsABadToken() throws Exception {
        when(webhooks.receive(any(), any(), any()))
                .thenThrow(new WebhookAuthenticationException("Unknown webhook token"));

        mvc.perform(post("/api/v1/webhooks/{service}/{token}", "plex", "12.wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a delivery that could not be matched is still 204: nothing to retry")
    void acceptsWhatItCouldNotMatch() throws Exception {
        when(webhooks.receive(any(), any(), any()))
                .thenReturn(MediaServerWebhookService.Outcome.UNMATCHED);

        mvc.perform(post("/api/v1/webhooks/{service}/{token}", "plex", "12.secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("something that is not a payload at all is 400")
    void rejectsRubbish() throws Exception {
        when(webhooks.receive(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("That payload could not be read"));

        mvc.perform(post("/api/v1/webhooks/{service}/{token}", "jellyfin", "12.secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a multipart delivery with no payload part is 400 rather than a 500")
    void rejectsAMissingPayloadPart() throws Exception {
        mvc.perform(multipart("/api/v1/webhooks/{service}/{token}", "plex", "12.secret")
                        .part(new MockPart("thumb", new byte[] {1, 2, 3})))
                .andExpect(status().isBadRequest());
    }
}
