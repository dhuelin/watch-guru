package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.streaming.plex.PlexWebhookService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Where a user's Plex server posts what they watched.
 *
 * <p>Hidden from the API document on purpose. The generated Kotlin and Swift
 * clients are built from that document, and neither app will ever call this --
 * the caller is somebody's Plex server, and shipping it as a client method
 * would only invite one.
 *
 * <p>The token is in the path because Plex sends no headers of our choosing
 * and offers no signing secret: the URL is the entire credential. That has one
 * unavoidable consequence worth naming -- webhook URLs appear in access logs,
 * so the deployment's logs are as sensitive as a password file, and the token
 * can be retired from the app at any time.
 */
@RestController
@RequestMapping("/api/v1/webhooks/plex")
@Hidden
public class PlexWebhookController {

    private final PlexWebhookService webhooks;

    public PlexWebhookController(PlexWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    /**
     * How Plex itself posts: multipart, with the JSON in a {@code payload} part
     * and, for some events, a thumbnail alongside it.
     */
    @PostMapping(path = "/{token}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> receiveMultipart(@PathVariable String token,
                                                   MultipartHttpServletRequest request) {
        String payload = request.getParameter("payload");
        if (payload == null) {
            // Plex sends the part with a content type on some versions, which
            // makes it a file rather than a form field to the servlet API.
            MultipartFile file = request.getFile("payload");
            if (file == null) {
                return problem("A Plex delivery must carry a payload part.");
            }
            try {
                payload = new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return problem("That Plex payload could not be read.");
            }
        }
        return accept(token, payload);
    }

    /** The same delivery as plain JSON, which is what a relay or a test sends. */
    @PostMapping(path = "/{token}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> receiveJson(@PathVariable String token,
                                              @RequestBody String payload) {
        return accept(token, payload);
    }

    /**
     * Always 204 once the token checks out, whatever the delivery turned out
     * to be.
     *
     * <p>Plex has no retry worth the name and no dead-letter queue: a non-2xx
     * teaches nobody anything and shows the user a red webhook in their server
     * settings for something that is not their server's fault. What happened to
     * a delivery is recorded against the connection and read back through
     * {@code GET /me/streaming-accounts/plex}, which is where a person can act
     * on it.
     */
    private ResponseEntity<Object> accept(String token, String payload) {
        try {
            webhooks.receive(token, payload);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return problem(e.getMessage());
        }
    }

    private ResponseEntity<Object> problem(String detail) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail));
    }
}
