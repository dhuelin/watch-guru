package com.dhuelin.dev.watchguru.streaming.mediaserver;

import com.dhuelin.dev.watchguru.common.NotFoundException;
import com.dhuelin.dev.watchguru.streaming.domain.LinkStatus;
import com.dhuelin.dev.watchguru.streaming.domain.LinkedStreamingAccount;
import com.dhuelin.dev.watchguru.streaming.domain.StreamingService;
import com.dhuelin.dev.watchguru.streaming.repository.LinkedStreamingAccountRepository;
import com.dhuelin.dev.watchguru.streaming.repository.StreamingServiceRepository;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * Connecting and disconnecting a media server.
 *
 * <p>Connecting mints a webhook URL and nothing else. There is no login here,
 * no server token, and no server address: the user pastes the URL into their
 * own server's settings and it calls us. That direction is the whole security
 * argument -- this service never holds a credential that would let it read
 * anybody's library, so there is nothing here worth stealing beyond the ability
 * to write watch events into one account.
 */
@Service
public class MediaServerLinkService {

    private final LinkedStreamingAccountRepository accounts;
    private final StreamingServiceRepository services;
    private final MediaServers servers;
    private final Clock clock;

    public MediaServerLinkService(LinkedStreamingAccountRepository accounts,
                                  StreamingServiceRepository services,
                                  MediaServers servers,
                                  Clock clock) {
        this.accounts = accounts;
        this.services = services;
        this.servers = servers;
        this.clock = clock;
    }

    /** Raised when a connection cannot be made as asked for. */
    public static class NotAvailableException extends RuntimeException {
        public NotAvailableException(String message) {
            super(message);
        }
    }

    /**
     * Creates the link if it is missing and issues a fresh token either way.
     *
     * @param accountName whose viewing counts on that server. Optional for
     *                    Plex, which says whose account played something;
     *                    required for Jellyfin and Emby, whose webhooks are the
     *                    server's rather than one user's
     * @return the link, and the token in clear -- the only time it exists
     */
    @Transactional
    public Connection connect(AppUser user, String slug, String accountName) {
        MediaServerAdapter adapter = servers.require(slug);
        String name = accountName == null || accountName.isBlank() ? null : accountName.trim();

        if (adapter.requiresAccountName() && name == null) {
            // Refused rather than accepted-and-filtered-later: this webhook
            // fires for everybody on that server, and without a name every
            // housemate's evening would land in this user's history.
            throw new NotAvailableException("Connecting " + adapter.displayName()
                    + " needs the username whose viewing should be recorded. Its webhook fires for "
                    + "everybody on the server, and the username is what separates yours.");
        }

        StreamingService service = services.findBySlug(adapter.slug())
                .orElseThrow(() -> NotFoundException.of("StreamingService", adapter.slug()));

        LinkedStreamingAccount account = accounts
                .findByUserIdAndStreamingServiceId(user.getId(), service.getId())
                .orElseGet(() -> new LinkedStreamingAccount(user, service));

        account.setAccountLabel(name);
        account.setStatus(LinkStatus.PENDING);
        account.setSyncEnabled(true);
        account.setLastSyncError(null);
        // Saved first when new: the token names the row, so the row needs an id.
        account = accounts.save(account);

        String token = WebhookToken.issue(account.getId());
        account.setWebhookTokenHash(WebhookToken.hash(token));
        account.setWebhookTokenIssuedAt(clock.instant());
        accounts.save(account);

        return new Connection(account, token);
    }

    /**
     * Stops accepting deliveries for this user's link to one server.
     *
     * <p>The hash is cleared rather than the row deleted. The row carries when
     * the connection last worked and what went wrong, which is worth keeping;
     * the credential is not, and a URL still pasted into somebody's server
     * settings must stop working the moment they disconnect here.
     */
    @Transactional
    public void disconnect(AppUser user, String slug) {
        accounts.findByUserIdAndStreamingServiceSlug(user.getId(), servers.require(slug).slug())
                .ifPresent(account -> {
                    account.setWebhookTokenHash(null);
                    account.setWebhookTokenIssuedAt(null);
                    account.setSyncEnabled(false);
                    account.setStatus(LinkStatus.DISCONNECTED);
                    accounts.save(account);
                });
    }

    @Transactional(readOnly = true)
    public Optional<LinkedStreamingAccount> find(AppUser user, String slug) {
        return accounts.findByUserIdAndStreamingServiceSlug(user.getId(), servers.require(slug).slug());
    }

    /** A link and, once, the token that reaches it. */
    public record Connection(LinkedStreamingAccount account, String token) {
    }
}
