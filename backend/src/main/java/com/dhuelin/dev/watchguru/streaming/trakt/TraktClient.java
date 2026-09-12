package com.dhuelin.dev.watchguru.streaming.trakt;

import com.dhuelin.dev.watchguru.config.TraktProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Trakt's HTTP API, in the four calls this service makes.
 *
 * <p>Everything here is a network call to somebody else's service, so every
 * failure arrives as {@link TraktException} -- one type, whether Trakt was
 * unreachable, rate limiting, or refused the credentials. Callers that need to
 * tell "reconnect" from "try later" ask {@link TraktException#isAuthFailure()},
 * which is the only distinction that changes what a user is told.
 */
@Component
public class TraktClient {

    private static final Logger log = LoggerFactory.getLogger(TraktClient.class);

    private static final ParameterizedTypeReference<List<TraktResponses.HistoryItem>> HISTORY =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient client;
    private final TraktProperties properties;

    public TraktClient(RestClient traktRestClient, TraktProperties properties) {
        this.client = traktRestClient;
        this.properties = properties;
    }

    /** Turns the code from the callback into a token pair. */
    public TraktResponses.Token exchange(String code, String redirectUri) {
        return token(Map.of(
                "code", code,
                "client_id", properties.clientId(),
                "client_secret", properties.clientSecret(),
                "redirect_uri", redirectUri,
                "grant_type", "authorization_code"));
    }

    /** Renews an access token that is expiring, using the refresh token. */
    public TraktResponses.Token refresh(String refreshToken, String redirectUri) {
        return token(Map.of(
                "refresh_token", refreshToken,
                "client_id", properties.clientId(),
                "client_secret", properties.clientSecret(),
                "redirect_uri", redirectUri,
                "grant_type", "refresh_token"));
    }

    /**
     * Tells Trakt the token is finished with.
     *
     * <p>Called on disconnect, and its failure is never fatal: the user asked
     * to disconnect, so the local end of the connection goes regardless. A
     * token left live at Trakt is untidy; refusing to disconnect because Trakt
     * was unreachable would be worse.
     */
    public void revoke(String accessToken) {
        try {
            client.post()
                    .uri("/oauth/revoke")
                    .body(Map.of(
                            "token", accessToken,
                            "client_id", properties.clientId(),
                            "client_secret", properties.clientSecret()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Could not revoke a Trakt token: {}", e.toString());
        }
    }

    /**
     * One page of watched history, newest first.
     *
     * @param startAt only viewings at or after this instant, which is how a
     *                sync continues from where the last one stopped rather
     *                than re-reading a decade every time
     */
    public TraktResponses.HistoryPage history(String accessToken, Instant startAt, int page) {
        try {
            ResponseEntity<List<TraktResponses.HistoryItem>> response = client.get()
                    .uri(uri -> {
                        uri.path("/sync/history")
                                .queryParam("page", page)
                                .queryParam("limit", properties.pageSize());
                        if (startAt != null) {
                            uri.queryParam("start_at", startAt.toString());
                        }
                        return uri.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .toEntity(HISTORY);

            return new TraktResponses.HistoryPage(
                    response.getBody() == null ? List.of() : response.getBody(),
                    page,
                    pageCount(response.getHeaders()));
        } catch (RestClientException e) {
            throw TraktException.from("reading history", e);
        }
    }

    private TraktResponses.Token token(Map<String, String> body) {
        try {
            TraktResponses.Token token = client.post()
                    .uri("/oauth/token")
                    .body(body)
                    .retrieve()
                    .body(TraktResponses.Token.class);

            if (token == null || token.accessToken() == null) {
                throw new TraktException("Trakt returned no access token", true);
            }
            return token;
        } catch (RestClientException e) {
            throw TraktException.from("exchanging a token", e);
        }
    }

    /**
     * How many pages there are, from Trakt's own header.
     *
     * <p>One page when the header is missing rather than none: a response
     * without paging headers is a single page of results, and treating it as
     * zero would silently drop everything in it.
     */
    private static int pageCount(HttpHeaders headers) {
        String value = headers.getFirst("X-Pagination-Page-Count");
        try {
            return value == null ? 1 : Math.max(Integer.parseInt(value.trim()), 1);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
