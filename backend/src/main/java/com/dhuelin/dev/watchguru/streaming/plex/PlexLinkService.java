package com.dhuelin.dev.watchguru.streaming.plex;

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
 * Connecting and disconnecting a Plex server.
 *
 * <p>Connecting mints a webhook URL and nothing else. There is no Plex login
 * here, no Plex token, and no server address: the user pastes the URL into
 * their own Plex settings and their server calls us. That direction is the
 * whole security argument -- this service never holds a credential that would
 * let it read anybody's Plex library, so there is nothing here worth stealing
 * beyond the ability to write watch events into one account.
 */
@Service
public class PlexLinkService {

    /** Slug of the row seeded by V8; the link hangs off it. */
    public static final String PLEX_SLUG = "plex";

    private final LinkedStreamingAccountRepository accounts;
    private final StreamingServiceRepository services;
    private final Clock clock;

    public PlexLinkService(LinkedStreamingAccountRepository accounts,
                           StreamingServiceRepository services,
                           Clock clock) {
        this.accounts = accounts;
        this.services = services;
        this.clock = clock;
    }

    /**
     * Creates the link if it is missing and issues a fresh token either way.
     *
     * @param plexUsername the Plex account whose viewing counts, or null to
     *                     accept whatever the server says is this webhook's own
     *                     account. On a shared server the name is what keeps a
     *                     housemate's evening out of this user's history
     * @return the link, and the token in clear -- the only time it exists
     */
    @Transactional
    public Connection connect(AppUser user, String plexUsername) {
        StreamingService plex = services.findBySlug(PLEX_SLUG)
                .orElseThrow(() -> NotFoundException.of("StreamingService", PLEX_SLUG));

        LinkedStreamingAccount account = accounts
                .findByUserIdAndStreamingServiceId(user.getId(), plex.getId())
                .orElseGet(() -> new LinkedStreamingAccount(user, plex));

        account.setAccountLabel(plexUsername == null || plexUsername.isBlank()
                ? null : plexUsername.trim());
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
     * Stops accepting deliveries for this user's Plex link.
     *
     * <p>The hash is cleared rather than the row deleted. The row carries when
     * the connection last worked and what went wrong, which is worth keeping;
     * the credential is not, and a URL still pasted into somebody's Plex
     * settings must stop working the moment they disconnect here.
     */
    @Transactional
    public void disconnect(AppUser user) {
        accounts.findByUserIdAndStreamingServiceSlug(user.getId(), PLEX_SLUG).ifPresent(account -> {
            account.setWebhookTokenHash(null);
            account.setWebhookTokenIssuedAt(null);
            account.setSyncEnabled(false);
            account.setStatus(LinkStatus.DISCONNECTED);
            accounts.save(account);
        });
    }

    @Transactional(readOnly = true)
    public Optional<LinkedStreamingAccount> find(AppUser user) {
        return accounts.findByUserIdAndStreamingServiceSlug(user.getId(), PLEX_SLUG);
    }

    /** A link and, once, the token that reaches it. */
    public record Connection(LinkedStreamingAccount account, String token) {
    }
}
