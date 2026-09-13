package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.ApiMapper;
import com.dhuelin.dev.watchguru.api.dto.Requests;
import com.dhuelin.dev.watchguru.api.dto.Responses;
import com.dhuelin.dev.watchguru.config.PublicUrlProperties;
import com.dhuelin.dev.watchguru.security.CurrentUserService;
import com.dhuelin.dev.watchguru.streaming.domain.LinkedStreamingAccount;
import com.dhuelin.dev.watchguru.streaming.mediaserver.MediaServerAdapter;
import com.dhuelin.dev.watchguru.streaming.mediaserver.MediaServerLinkService;
import com.dhuelin.dev.watchguru.streaming.mediaserver.MediaServers;
import com.dhuelin.dev.watchguru.streaming.repository.SyncRunRepository;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Optional;

/**
 * Connecting a media server -- Plex, Jellyfin or Emby -- so watching something
 * there records it here (#39).
 *
 * <p>One set of endpoints for all three, taking the service in the path,
 * because the connection is identical in every respect a caller can see: this
 * service issues a webhook URL, the user pastes it into their own server, and
 * the server does the talking. Nothing here can read anybody's library, which
 * is the point -- the connection carries exactly one capability, and it points
 * inward.
 */
@RestController
@RequestMapping("/api/v1/me/streaming-accounts/servers/{service}")
public class MediaServerController {

    private static final String WEBHOOK_PATH = "/api/v1/webhooks/";

    private final MediaServerLinkService links;
    private final MediaServers servers;
    private final SyncRunRepository syncRuns;
    private final CurrentUserService currentUser;
    private final ApiMapper mapper;
    private final PublicUrlProperties urls;

    public MediaServerController(MediaServerLinkService links,
                                 MediaServers servers,
                                 SyncRunRepository syncRuns,
                                 CurrentUserService currentUser,
                                 ApiMapper mapper,
                                 PublicUrlProperties urls) {
        this.links = links;
        this.servers = servers;
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
    @Operation(operationId = "connectMediaServer")
    public Responses.MediaServerConnectionResponse connect(
            @PathVariable String service,
            @Valid @RequestBody(required = false) Requests.ConnectMediaServer request) {

        MediaServerAdapter adapter = servers.require(service);
        MediaServerLinkService.Connection connection = links.connect(
                currentUser.require(), adapter.slug(),
                request == null ? null : request.accountName());

        return new Responses.MediaServerConnectionResponse(
                adapter.slug(),
                adapter.displayName(),
                mapper.toLinkedAccount(connection.account()),
                baseUrl() + WEBHOOK_PATH + adapter.slug() + "/" + connection.token(),
                setUpHint(adapter));
    }

    /** Whether the connection is live, and what it has done lately. */
    @GetMapping
    @Operation(operationId = "getMediaServerStatus")
    public Responses.MediaServerStatusResponse status(@PathVariable String service) {
        MediaServerAdapter adapter = servers.require(service);
        Optional<LinkedStreamingAccount> account = links.find(currentUser.require(), adapter.slug());

        List<Responses.SyncRunResponse> runs = account
                .map(a -> syncRuns.findTop10ByLinkedAccountIdOrderByStartedAtDesc(a.getId()).stream()
                        .map(mapper::toSyncRun)
                        .toList())
                .orElseGet(List::of);

        return new Responses.MediaServerStatusResponse(
                adapter.slug(),
                adapter.displayName(),
                adapter.requiresAccountName(),
                account.map(a -> a.getWebhookTokenHash() != null).orElse(false),
                account.map(mapper::toLinkedAccount).orElse(null),
                runs);
    }

    /** Retires the webhook URL. Deliveries from then on are rejected. */
    @DeleteMapping
    @Operation(operationId = "disconnectMediaServer")
    public ResponseEntity<Void> disconnect(@PathVariable String service) {
        links.disconnect(currentUser.require(), servers.require(service).slug());
        return ResponseEntity.noContent().build();
    }

    /** Where to paste it, in that server's own words. */
    private static String setUpHint(MediaServerAdapter adapter) {
        return switch (adapter.slug()) {
            case "plex" -> "In Plex: Settings, Webhooks, Add Webhook, and paste this URL. It is "
                    + "shown once, so copy it now. Plex Pass is required for webhooks.";
            case "jellyfin" -> "In Jellyfin: Dashboard, Plugins, Webhook, Add Generic Destination, "
                    + "and paste this URL. Tick Playback Stop as the notification type. It is shown "
                    + "once, so copy it now.";
            case "emby" -> "In Emby: Settings, Notifications, Add Notification, Webhooks, and paste "
                    + "this URL. It is shown once, so copy it now.";
            default -> "Paste this URL into your server's webhook settings. It is shown once, so "
                    + "copy it now.";
        };
    }

    /**
     * Where the user's server should send its deliveries.
     *
     * <p>The configured address wins. Deriving it from the request works in
     * development and is wrong behind a proxy that rewrites the host -- and a
     * webhook URL that is wrong in a way nobody notices is the worst kind: none
     * of these servers reports an error for a URL that resolves to nothing.
     */
    private String baseUrl() {
        String configured = urls.trimmed();
        return configured != null
                ? configured
                : ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
