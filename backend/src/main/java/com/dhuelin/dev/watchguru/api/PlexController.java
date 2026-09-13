package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.ApiMapper;
import com.dhuelin.dev.watchguru.api.dto.Requests;
import com.dhuelin.dev.watchguru.api.dto.Responses;
import com.dhuelin.dev.watchguru.config.PublicUrlProperties;
import com.dhuelin.dev.watchguru.security.CurrentUserService;
import com.dhuelin.dev.watchguru.streaming.domain.LinkedStreamingAccount;
import com.dhuelin.dev.watchguru.streaming.plex.PlexLinkService;
import com.dhuelin.dev.watchguru.streaming.repository.SyncRunRepository;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Optional;

/**
 * Connecting a Plex server, so watching something there records it here (#39).
 *
 * <p>Three calls and no OAuth dance: this service issues a webhook URL, the
 * user pastes it into their own Plex settings, and their server does the
 * talking. Nothing here can read a Plex library, which is the point -- the
 * connection carries exactly one capability, and it points inward.
 */
@RestController
@RequestMapping("/api/v1/me/streaming-accounts/plex")
public class PlexController {

    private static final String WEBHOOK_PATH = "/api/v1/webhooks/plex/";

    private final PlexLinkService links;
    private final SyncRunRepository syncRuns;
    private final CurrentUserService currentUser;
    private final ApiMapper mapper;
    private final PublicUrlProperties urls;

    public PlexController(PlexLinkService links,
                          SyncRunRepository syncRuns,
                          CurrentUserService currentUser,
                          ApiMapper mapper,
                          PublicUrlProperties urls) {
        this.links = links;
        this.syncRuns = syncRuns;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.urls = urls;
    }

    /**
     * Issues the webhook URL. Calling it again replaces the previous one.
     *
     * <p>POST rather than PUT, and it is not idempotent on purpose: each call
     * mints a new secret and retires the old, which is also how a user who
     * pasted their URL somewhere public gets out of it.
     */
    @PostMapping
    @Operation(operationId = "connectPlex")
    public Responses.PlexConnectionResponse connect(@Valid @RequestBody(required = false)
                                                    Requests.ConnectPlex request) {
        PlexLinkService.Connection connection = links.connect(
                currentUser.require(), request == null ? null : request.plexUsername());

        return new Responses.PlexConnectionResponse(
                mapper.toLinkedAccount(connection.account()),
                baseUrl() + WEBHOOK_PATH + connection.token(),
                "In Plex: Settings, Webhooks, Add Webhook, and paste this URL. It is shown once, "
                        + "so copy it now. Plex Pass is required for webhooks.");
    }

    /** Whether the connection is live, and what it has done lately. */
    @GetMapping
    @Operation(operationId = "getPlexStatus")
    public Responses.PlexStatusResponse status() {
        Optional<LinkedStreamingAccount> account = links.find(currentUser.require());

        List<Responses.SyncRunResponse> runs = account
                .map(a -> syncRuns.findTop10ByLinkedAccountIdOrderByStartedAtDesc(a.getId()).stream()
                        .map(mapper::toSyncRun)
                        .toList())
                .orElseGet(List::of);

        return new Responses.PlexStatusResponse(
                account.map(a -> a.getWebhookTokenHash() != null).orElse(false),
                account.map(mapper::toLinkedAccount).orElse(null),
                runs);
    }

    /** Retires the webhook URL. Deliveries from then on are rejected. */
    @DeleteMapping
    @Operation(operationId = "disconnectPlex")
    public ResponseEntity<Void> disconnect() {
        links.disconnect(currentUser.require());
        return ResponseEntity.noContent().build();
    }

    /**
     * Where the user's Plex server should send its deliveries.
     *
     * <p>The configured address wins. Deriving it from the request works in
     * development and is wrong behind a proxy that rewrites the host -- and a
     * webhook URL that is wrong in a way nobody notices is the worst kind: Plex
     * reports no error for a URL that resolves to nothing.
     */
    private String baseUrl() {
        String configured = urls.trimmed();
        return configured != null
                ? configured
                : ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
