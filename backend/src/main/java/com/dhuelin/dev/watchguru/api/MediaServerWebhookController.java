package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.streaming.mediaserver.MediaServerWebhookService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
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
 * Where a user's media server posts what they watched.
 *
 * <p>Hidden from the API document on purpose. The generated Kotlin and Swift
 * clients are built from that document, and neither app will ever call this --
 * the caller is somebody's Plex, Jellyfin or Emby server, and shipping it as a
 * client method would only invite one.
 *
 * <p>The token is in the path because none of these servers sends headers of
 * our choosing or offers a signing secret: the URL is the entire credential.
 * That has one unavoidable consequence worth naming -- webhook URLs appear in
 * access logs, so the deployment's logs are as sensitive as a password file,
 * and the token can be retired from the app at any time.
 */
@RestController
@RequestMapping("/api/v1/webhooks/{service}")
@Hidden
public class MediaServerWebhookController {

    private final MediaServerWebhookService webhooks;

    public MediaServerWebhookController(MediaServerWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    /**
     * How Plex posts: multipart, with the JSON in a {@code payload} part and,
     * for some events, a thumbnail alongside it.
     */
    @PostMapping(path = "/{token}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> receiveMultipart(@PathVariable String service,
                                                   @PathVariable String token,
                                                   MultipartHttpServletRequest request) {
        String payload = request.getParameter("payload");
        if (payload == null) {
            // Some versions send the part with a content type, which makes it a
            // file rather than a form field to the servlet API.
            MultipartFile file = request.getFile("payload");
            if (file == null) {
                return problem("A multipart delivery must carry a payload part.");
            }
            try {
                payload = new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return problem("That payload could not be read.");
            }
        }
        return accept(service, token, payload);
    }

    /** How Jellyfin and Emby post, and what a relay or a test sends. */
    @PostMapping(path = "/{token}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> receiveJson(@PathVariable String service,
                                              @PathVariable String token,
                                              @RequestBody String payload) {
        return accept(service, token, payload);
    }

    /**
     * Always 204 once the token checks out, whatever the delivery turned out
     * to be.
     *
     * <p>None of these servers has a retry worth the name or a dead-letter
     * queue: a non-2xx teaches nobody anything and shows the user a red webhook
     * in their server settings for something that is not their server's fault.
     * What happened to a delivery is recorded against the connection and read
     * back through {@code GET /me/streaming-accounts/servers/{service}}, which
     * is where a person can act on it.
     */
    private ResponseEntity<Object> accept(String service, String token, String payload) {
        try {
            webhooks.receive(service, token, payload);
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
