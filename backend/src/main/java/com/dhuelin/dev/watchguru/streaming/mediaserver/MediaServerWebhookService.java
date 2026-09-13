package com.dhuelin.dev.watchguru.streaming.mediaserver;

import com.dhuelin.dev.watchguru.imports.domain.ImportRow;
import com.dhuelin.dev.watchguru.imports.domain.MatchStatus;
import com.dhuelin.dev.watchguru.imports.domain.MatchedRow;
import com.dhuelin.dev.watchguru.imports.service.ImportMatcher;
import com.dhuelin.dev.watchguru.streaming.domain.LinkStatus;
import com.dhuelin.dev.watchguru.streaming.domain.LinkedStreamingAccount;
import com.dhuelin.dev.watchguru.streaming.domain.SyncRun;
import com.dhuelin.dev.watchguru.streaming.repository.LinkedStreamingAccountRepository;
import com.dhuelin.dev.watchguru.streaming.repository.SyncRunRepository;
import com.dhuelin.dev.watchguru.streaming.service.StreamingWatchWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What happens when somebody's media server says they finished watching
 * something.
 *
 * <p>One service for Plex, Jellyfin and Emby. Which event counts as watched and
 * how the payload is shaped live in the adapters; everything here -- the token,
 * whose viewing it is, matching, writing, and the record of what happened -- is
 * the same whichever server called, because it is the same claim.
 *
 * <p>Not transactional as a whole, on purpose. The write runs in a transaction
 * of its own inside {@link StreamingWatchWriter}, and a duplicate delivery ends
 * there in a unique-index violation; if this method held the enclosing
 * transaction, that violation would mark it rollback-only and take the
 * sync-run record with it -- losing the record of what happened at exactly the
 * moment it became interesting.
 */
@Service
public class MediaServerWebhookService {

    private static final Logger log = LoggerFactory.getLogger(MediaServerWebhookService.class);

    /**
     * Lookups against the metadata provider per delivery.
     *
     * <p>One: a delivery is a single title, and starting a series that this
     * catalogue has never held should pull it in rather than report it as
     * unknown. The budget exists at all because an unbounded one here would let
     * a busy server drive the provider's rate limit.
     */
    private static final int PROVIDER_LOOKUPS = 1;

    private final LinkedStreamingAccountRepository accounts;
    private final SyncRunRepository syncRuns;
    private final MediaServers servers;
    private final ImportMatcher matcher;
    private final StreamingWatchWriter writer;
    private final Clock clock;

    public MediaServerWebhookService(LinkedStreamingAccountRepository accounts,
                                     SyncRunRepository syncRuns,
                                     MediaServers servers,
                                     ImportMatcher matcher,
                                     StreamingWatchWriter writer,
                                     Clock clock) {
        this.accounts = accounts;
        this.syncRuns = syncRuns;
        this.servers = servers;
        this.matcher = matcher;
        this.writer = writer;
        this.clock = clock;
    }

    /** What one delivery came to. */
    public enum Outcome {
        /** A watch event was written. */
        RECORDED,
        /** Already recorded: a replayed or repeated delivery. */
        DUPLICATE,
        /** Not a watch, not a film or episode, or not this user's viewing. */
        IGNORED,
        /** A viewing this catalogue could not place. Reported on the link. */
        UNMATCHED
    }

    /**
     * @param slug    which server called, from the webhook URL
     * @param token   the secret from the webhook URL
     * @param payload the JSON that server posted
     * @throws WebhookAuthenticationException when the token opens nothing
     * @throws IllegalArgumentException       when the payload is not readable JSON
     */
    public Outcome receive(String slug, String token, String payload) {
        MediaServerAdapter adapter = servers.require(slug);
        LinkedStreamingAccount account = authenticate(adapter, token);

        LocalDate today = LocalDate.now(clock.withZone(account.getUser().zone()));
        Optional<MediaServerEvent> event = adapter.read(payload, today);
        if (event.isEmpty()) {
            // A pause, a music track, or a payload missing the fields that name
            // what was played. Nothing to record and nothing to report.
            return Outcome.IGNORED;
        }
        if (!isThisUsers(account, adapter, event.get())) {
            // A server owner receives events for everyone on their server.
            // Somebody else's evening is not this user's history, and it is not
            // an error either -- so no sync run and no error on the link.
            log.debug("Ignoring a {} delivery for account {} on link {}",
                    adapter.slug(), event.get().accountName(), account.getId());
            return Outcome.IGNORED;
        }

        SyncRun run = syncRuns.save(new SyncRun(account));
        ImportRow row = event.get().row();
        try {
            return record(account, row, run);
        } catch (RuntimeException e) {
            run.fail(e.getMessage());
            syncRuns.save(run);
            noteOnLink(account, adapter.displayName() + " sent \"" + row.titleText()
                    + "\", and recording it failed: " + e.getMessage(), LinkStatus.ERROR);
            log.warn("A {} delivery failed for link {}: {}", adapter.slug(), account.getId(), e.toString());
            throw e;
        }
    }

    private Outcome record(LinkedStreamingAccount account, ImportRow row, SyncRun run) {
        List<MatchedRow> matched = matcher.match(List.of(row), PROVIDER_LOOKUPS);
        MatchedRow match = matched.getFirst();

        if (match.status() != MatchStatus.MATCHED || match.titleId() == null) {
            // The acceptance criterion this satisfies: a title a server reports
            // and the catalogue does not hold is reported, not dropped. The
            // note is the matcher's own, which says which of the several
            // reasons it was.
            run.setItemsFailed(1);
            run.setErrorMessage(match.note());
            run.finish();
            syncRuns.save(run);
            noteOnLink(account, match.note(), LinkStatus.CONNECTED);
            return Outcome.UNMATCHED;
        }

        Instant watchedAt = clock.instant();
        try {
            boolean written = writer.write(account.getUser(), match,
                    account.getStreamingService().getId(), watchedAt);
            run.setItemsImported(written ? 1 : 0);
            run.setItemsFailed(written ? 0 : 1);
            if (!written) {
                run.setErrorMessage("Matched " + match.titleName()
                        + ", but the episode this delivery names is not in the catalogue.");
            }
            run.finish();
            syncRuns.save(run);
            noteOnLink(account, written ? null : run.getErrorMessage(), LinkStatus.CONNECTED);
            return written ? Outcome.RECORDED : Outcome.UNMATCHED;
        } catch (DataIntegrityViolationException e) {
            // The unique index on (user, origin, origin_ref) fired: this
            // viewing is already recorded. Servers retry deliveries they think
            // failed, and a proxy in front of this service may repeat one.
            run.setItemsSkipped(1);
            run.finish();
            syncRuns.save(run);
            noteOnLink(account, null, LinkStatus.CONNECTED);
            return Outcome.DUPLICATE;
        }
    }

    /**
     * Whose viewing this is.
     *
     * <p>With a username configured, it must match: on a shared server that
     * name is the only thing separating this user's history from their
     * housemate's. Without one -- which only Plex allows -- the delivery must at
     * least be for the account that owns the webhook, which is what Plex's
     * {@code user} flag says.
     */
    private boolean isThisUsers(LinkedStreamingAccount account,
                                MediaServerAdapter adapter,
                                MediaServerEvent event) {
        String expected = account.getAccountLabel();
        if (expected == null || expected.isBlank()) {
            // A link made before the adapter required a name, or Plex without
            // one. Either way the flag is all there is, and a server that does
            // not send it cannot be trusted to be this user's.
            return !adapter.requiresAccountName() && Boolean.TRUE.equals(event.webhookOwners());
        }
        String actual = event.accountName();
        return actual != null
                && actual.trim().toLowerCase(Locale.ROOT).equals(expected.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * The link's own status line: when it last heard from the server, and what
     * went wrong if anything did.
     *
     * <p>An integration that silently stops is worse than one never offered, so
     * every accepted delivery moves {@code lastSyncAt} even when it could not be
     * matched -- "connected, and it could not place The Bear" is a different
     * thing to tell somebody than "connected".
     */
    private void noteOnLink(LinkedStreamingAccount account, String error, LinkStatus status) {
        account.setLastSyncAt(clock.instant());
        account.setLastSyncError(error);
        account.setStatus(status);
        accounts.save(account);
    }

    /**
     * The link a presented token opens, if it opens one on this server.
     *
     * <p>The service is checked as well as the token: a token issued for a
     * user's Plex link must not accept Jellyfin deliveries, or the account
     * filter would be applied against the wrong server's idea of a username.
     */
    private LinkedStreamingAccount authenticate(MediaServerAdapter adapter, String token) {
        LinkedStreamingAccount account = WebhookToken.accountId(token)
                .flatMap(accounts::findWithUserAndServiceById)
                .orElseThrow(() -> new WebhookAuthenticationException("Unknown webhook token"));

        if (!WebhookToken.matches(token, account.getWebhookTokenHash())) {
            throw new WebhookAuthenticationException("Unknown webhook token");
        }
        if (!adapter.slug().equals(account.getStreamingService().getSlug())) {
            throw new WebhookAuthenticationException("That token is not for this service");
        }
        if (!account.isSyncEnabled() || account.getStatus() == LinkStatus.DISCONNECTED) {
            throw new WebhookAuthenticationException("This connection is disconnected");
        }
        return account;
    }
}
