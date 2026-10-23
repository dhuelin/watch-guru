package com.dhuelin.dev.watchguru.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Caps how much of a request body anybody may make the server read.
 *
 * <p>{@code spring.servlet.multipart.*} bounds multipart bodies only. A JSON
 * body is read whole into memory by the message converters before any handler
 * looks at it -- and on the webhook path, before the token in the URL is even
 * checked. Without this, one unauthenticated sender streaming zeros at
 * {@code /api/v1/webhooks/...} could exhaust the heap of a small VM, and
 * {@code -XX:+ExitOnOutOfMemoryError} would turn that into an outage.
 *
 * <p>Runs ahead of everything, Spring Security included, so a body is refused
 * before any work is done on it. A declared {@code Content-Length} over the
 * limit is refused outright; a chunked body that declares nothing is counted as
 * it is read and fails the read once it passes the limit, which
 * {@code ApiExceptionHandler} turns into the same 413.
 *
 * <p>Multipart is left to the servlet container's own limits, which already
 * apply to it and which Plex's thumbnails need to be larger than the JSON cap.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    /** A media server's event payload is a few kilobytes. */
    static final long WEBHOOK_LIMIT = 256 * 1024;

    /**
     * An import preview carries a whole exported file, validated at up to five
     * million characters; JSON escaping can make that larger on the wire.
     */
    static final long IMPORT_LIMIT = 12L * 1024 * 1024;

    /** Everything else is a small command or a page of ids. */
    static final long DEFAULT_LIMIT = 1024 * 1024;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (isMultipart(request)) {
            chain.doFilter(request, response);
            return;
        }

        long limit = limitFor(request.getRequestURI());
        if (request.getContentLengthLong() > limit) {
            response.sendError(HttpStatus.CONTENT_TOO_LARGE.value(), "Request body too large");
            return;
        }
        chain.doFilter(new LimitedRequest(request, limit), response);
    }

    static long limitFor(String path) {
        if (path.startsWith("/api/v1/webhooks/")) {
            return WEBHOOK_LIMIT;
        }
        if (path.startsWith("/api/v1/me/imports")) {
            return IMPORT_LIMIT;
        }
        return DEFAULT_LIMIT;
    }

    private static boolean isMultipart(HttpServletRequest request) {
        String type = request.getContentType();
        return type != null && type.toLowerCase().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    /** Thrown from a read that would pass the limit. */
    public static class BodyTooLargeException extends IOException {
        BodyTooLargeException(long limit) {
            super("Request body exceeds " + limit + " bytes");
        }
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long limit;
        private ServletInputStream stream;

        LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedInputStream(super.getInputStream(), limit);
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding() != null ? getCharacterEncoding() : "UTF-8";
            return new BufferedReader(new InputStreamReader(getInputStream(), encoding));
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long limit;
        private long read;

        LimitedInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = delegate.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) throws BodyTooLargeException {
            read += n;
            if (read > limit) {
                throw new BodyTooLargeException(limit);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
