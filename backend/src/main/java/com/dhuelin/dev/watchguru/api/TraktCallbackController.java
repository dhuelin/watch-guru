package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.streaming.trakt.TraktConnectionService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Where Trakt sends the user's browser back.
 *
 * <p>Unauthenticated to the filter chain, because the request arrives from a
 * browser that has just been at trakt.tv and carries no bearer token of ours.
 * Which is exactly why it no longer connects anything: it passes the code and
 * state on to the app, and the app finishes with its own session -- see
 * {@link TraktConnectionService#appCallbackUri}. A link forwarded to somebody
 * else now dead-ends on their phone instead of connecting their Trakt account
 * to the sender's library.
 *
 * <p>Answers in HTML when it has to stop, because a person is reading it. This
 * is the one endpoint in the service whose audience is a browser rather than
 * an app, and a JSON problem document is not something to leave somebody
 * looking at.
 */
@RestController
@RequestMapping("/api/v1/streaming/trakt")
@Hidden
public class TraktCallbackController {

    private final TraktConnectionService connections;

    public TraktCallbackController(TraktConnectionService connections) {
        this.connections = connections;
    }

    @GetMapping(path = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> callback(@RequestParam(required = false) String code,
                                           @RequestParam(required = false) String state,
                                           @RequestParam(required = false) String error) {
        if (error != null && !error.isBlank()) {
            // The user pressed Deny, which is an answer rather than a fault.
            return page(HttpStatus.OK, "Not connected",
                    "You did not approve the connection, so nothing has changed.");
        }
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            return page(HttpStatus.BAD_REQUEST, "Something is missing",
                    "That link is incomplete. Start again from the app.");
        }

        // A redirect rather than a page with a link: the app's browser session
        // is waiting for exactly this scheme and closes on it. Nothing here is
        // checked or spent -- that happens when the app presents it -- so a
        // stray request to this URL changes nothing.
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(connections.appCallbackUri(code, state)))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }

    /**
     * Plain text in a minimal page, and nothing echoed back.
     *
     * <p>Every message here is a constant: a callback's parameters are
     * attacker-controlled, and reflecting any of them into HTML is how this
     * endpoint would become the one place in the service with an injection.
     */
    private static ResponseEntity<String> page(HttpStatus status, String heading, String message) {
        String html = """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Watch Guru</title>
                <style>body{font:16px/1.5 system-ui,sans-serif;margin:0;padding:2rem;max-width:34rem}
                h1{font-size:1.3rem}</style>
                </head><body><h1>%s</h1><p>%s</p></body></html>
                """.formatted(heading, message);

        return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body(html);
    }

}
