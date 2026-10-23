package com.dhuelin.dev.watchguru.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The body cap, on both of the ways a body can be too big: by saying so up
 * front, and by not saying and simply carrying on.
 */
class RequestBodyLimitFilterTest {

    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter();

    /** A request with no declared length, the way a chunked upload arrives. */
    private static MockHttpServletRequest chunked(String uri, byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.setContentType("application/json");
        request.setContent(body);
        return request;
    }

    @Test
    @DisplayName("a declared length over the webhook limit is refused before anything reads it")
    void declaredOversizeIsRefused() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/webhooks/jellyfin/1.x");
        request.setContentType("application/json");
        request.setContent(new byte[(int) RequestBodyLimitFilter.WEBHOOK_LIMIT + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> reached.set(true));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(reached).isFalse();
    }

    @Test
    @DisplayName("an undeclared body fails the read once it passes the limit")
    void undeclaredOversizeFailsTheRead() throws Exception {
        MockHttpServletRequest request = chunked("/api/v1/webhooks/jellyfin/1.x",
                new byte[(int) RequestBodyLimitFilter.WEBHOOK_LIMIT + 1]);
        FilterChain readAll = (req, res) -> {
            try (InputStream in = ((HttpServletRequest) req).getInputStream()) {
                in.readAllBytes();
            }
        };

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), readAll))
                .isInstanceOf(RequestBodyLimitFilter.BodyTooLargeException.class);
    }

    @Test
    @DisplayName("a body within the limit reads through untouched")
    void withinLimitPasses() throws Exception {
        byte[] body = "{\"ok\":true}".getBytes();
        MockHttpServletRequest request = chunked("/api/v1/me/watchlist", body);
        byte[][] seen = new byte[1][];

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> seen[0] = ((HttpServletRequest) req).getInputStream().readAllBytes());

        assertThat(seen[0]).isEqualTo(body);
    }

    @Test
    @DisplayName("imports get room for a whole exported file; everything else gets a megabyte")
    void limitsByPath() {
        assertThat(RequestBodyLimitFilter.limitFor("/api/v1/me/imports/preview"))
                .isEqualTo(RequestBodyLimitFilter.IMPORT_LIMIT);
        assertThat(RequestBodyLimitFilter.limitFor("/api/v1/webhooks/plex/1.x"))
                .isEqualTo(RequestBodyLimitFilter.WEBHOOK_LIMIT);
        assertThat(RequestBodyLimitFilter.limitFor("/api/v1/me/watchlist"))
                .isEqualTo(RequestBodyLimitFilter.DEFAULT_LIMIT);
    }

    @Test
    @DisplayName("multipart is left to the container's own multipart limits")
    void multipartIsNotWrapped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/webhooks/plex/1.x");
        request.setContentType("multipart/form-data; boundary=x");
        request.setContent(new byte[(int) RequestBodyLimitFilter.WEBHOOK_LIMIT + 1]);
        AtomicBoolean sameRequest = new AtomicBoolean();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> sameRequest.set(req == request));

        assertThat(sameRequest).isTrue();
    }
}
