package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.ApiMapper;
import com.dhuelin.dev.watchguru.api.dto.Responses;
import com.dhuelin.dev.watchguru.security.CurrentUserService;
import com.dhuelin.dev.watchguru.streaming.domain.LinkedStreamingAccount;
import com.dhuelin.dev.watchguru.streaming.repository.SyncRunRepository;
import com.dhuelin.dev.watchguru.streaming.trakt.TraktConnectionService;
import com.dhuelin.dev.watchguru.streaming.trakt.TraktSyncService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Optional;

/**
 * Connecting Trakt (#39).
 *
 * <p>The opposite shape to Plex, and worth the contrast: there, the user's own
 * server calls us and we hold nothing of theirs; here they authorise us to read
 * their history, and this service holds an access token on their behalf. That
 * token is sealed with a key from the deployment's configuration and never
 * reaches either app -- which is also why the OAuth exchange happens on the
 * server: a client secret shipped in two app binaries is not a secret.
 *
 * <p>Trakt is the integration worth having, because other people solved the
 * hard part: anybody scrobbling from Plex, Kodi or Infuse already has their
 * whole viewing there, so one connection picks up every source they use.
 */
@RestController
@RequestMapping("/api/v1/me/streaming-accounts/trakt")
public class TraktController {

    private final TraktConnectionService connections;
    private final TraktSyncService sync;
    private final SyncRunRepository syncRuns;
    private final CurrentUserService currentUser;
    private final ApiMapper mapper;

    public TraktController(TraktConnectionService connections,
                           TraktSyncService sync,
                           SyncRunRepository syncRuns,
                           CurrentUserService currentUser,
                           ApiMapper mapper) {
        this.connections = connections;
        this.sync = sync;
        this.syncRuns = syncRuns;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    /**
     * Starts an authorisation and returns where to send the user.
     *
     * <p>The app opens this URL in a browser rather than a web view, so the
     * user can see the address bar says trakt.tv before typing a password
     * there. Nothing is connected until they come back through the callback.
     */
    @PostMapping("/authorize")
    @Operation(operationId = "authorizeTrakt")
    public Responses.TraktAuthorizationResponse authorize() {
        String url = connections.authorizeUrl(currentUser.require(), requestBaseUrl());
        return new Responses.TraktAuthorizationResponse(url,
                "Open this in a browser and approve. Come back to the app when Trakt says you can "
                        + "close the page.");
    }

    /** Whether Trakt is connected, and what the last few syncs did. */
    @GetMapping
    @Operation(operationId = "getTraktStatus")
    public Responses.TraktStatusResponse status() {
        Optional<LinkedStreamingAccount> account = connections.find(currentUser.require());

        List<Responses.SyncRunResponse> runs = account
                .map(a -> syncRuns.findTop10ByLinkedAccountIdOrderByStartedAtDesc(a.getId()).stream()
                        .map(mapper::toSyncRun)
                        .toList())
                .orElseGet(List::of);

        return new Responses.TraktStatusResponse(
                account.map(a -> a.getCredentialRef() != null).orElse(false),
                account.map(mapper::toLinkedAccount).orElse(null),
                runs);
    }

    /**
     * Reads everything watched since the last sync, now.
     *
     * <p>The scheduled job does this every couple of hours; this is the button
     * for somebody who has just watched something and wants to see it appear.
     */
    @PostMapping("/sync")
    @Operation(operationId = "syncTrakt")
    public Responses.SyncResultResponse syncNow() {
        LinkedStreamingAccount account = connections.find(currentUser.require())
                .orElseThrow(() -> new TraktConnectionService.NotAvailableException(
                        "Trakt is not connected."));

        return sync.sync(account)
                .map(result -> new Responses.SyncResultResponse(
                        result.imported(), result.skipped(), result.failed(), result.problems()))
                // Empty means the connection could not be used at all; the
                // reason is on the link, which the status call returns.
                .orElseGet(() -> new Responses.SyncResultResponse(0, 0, 0, List.of()));
    }

    /** Revokes the token at Trakt and forgets it here. */
    @DeleteMapping
    @Operation(operationId = "disconnectTrakt")
    public ResponseEntity<Void> disconnect() {
        connections.disconnect(currentUser.require());
        return ResponseEntity.noContent().build();
    }

    private String requestBaseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
