package com.dhuelin.dev.watchguru.streaming.plex;

import com.dhuelin.dev.watchguru.imports.domain.ImportRow;
import com.dhuelin.dev.watchguru.imports.domain.MatchStatus;
import com.dhuelin.dev.watchguru.imports.domain.MatchedRow;
import com.dhuelin.dev.watchguru.imports.service.ImportMatcher;
import com.dhuelin.dev.watchguru.streaming.domain.LinkStatus;
import com.dhuelin.dev.watchguru.streaming.domain.LinkedStreamingAccount;
import com.dhuelin.dev.watchguru.streaming.domain.SyncRun;
import com.dhuelin.dev.watchguru.streaming.repository.LinkedStreamingAccountRepository;
import com.dhuelin.dev.watchguru.streaming.repository.SyncRunRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
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
 * What happens when a Plex server says somebody finished watching something.
 *
 * <p>Plex posts every playback event to the URL it was given: play, pause,
 * resume, stop, rate, and -- at roughly ninety per cent of the runtime --
 * scrobble. Only the last of those is a claim that something was watched, and
 * only it is acted on. Acting on {@code media.play} would mark an episode
 * watched for anybody who opened it and changed their mind a minute later.
 *
 * <p>Not transactional as a whole, on purpose. The write runs in a transaction
 * of its own inside {@link PlexWatchWriter}, and a duplicate delivery ends
 * there in a unique-index violation; if this method held the enclosing
 * transaction, that violation would mark it rollback-only and take the
 * sync-run record with it -- losing the record of what happened at exactly the
 * moment it became interesting.
 */
@Service
public class PlexWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PlexWebhookService.class);

    /**
     * Lookups against the metadata provider per delivery.
     *
     * <p>One: a scrobble is a single title, and starting a series on Plex that
     * this catalogue has never held should pull it in rather than report it as
     * unknown. The budget exists at all because the matcher takes one, and an
     * unbounded one here would let a busy server drive the provider's rate
     * limit.
     */
    private static final int PROVIDER_LOOKUPS = 1;

    private final LinkedStreamingAccountRepository accounts;
    private final SyncRunRepository syncRuns;
    private final ImportMatcher matcher;
    private final PlexWatchWriter writer;
    private final JsonMapper json;
    private final Clock clock;

    public PlexWebhookService(LinkedStreamingAccountRepository accounts,
                              SyncRunRepository syncRuns,
                              ImportMatcher matcher,
                              PlexWatchWriter writer,
                              JsonMapper json,
                              Clock clock) {
        this.accounts = accounts;
        this.syncRuns = syncRuns;
        this.matcher = matcher;
        this.writer = writer;
        this.json = json;
        this.clock = clock;
    }

    /** What one delivery came to. */
    public enum Outcome {
        /** A watch event was written. */
        RECORDED,
        /** Already recorded: a replayed or repeated delivery. */
        DUPLICATE,
        /** Not a scrobble, not a film or episode, or not this user's viewing. */
        IGNORED,
        /** A scrobble this catalogue could not place. Reported on the link. */
        UNMATCHED
    }

    /**
     * @param token   the secret from the webhook URL
     * @param payload the JSON Plex posted
     * @throws WebhookAuthenticationException when the token opens nothing
     * @throws IllegalArgumentException       when the payload is not readable JSON
     */
    public Outcome receive(String token, String payload) {
        LinkedStreamingAccount account = authenticate(token);
        PlexWebhookPayload event = parse(payload);

        if (!event.isScrobble()) {
            return Outcome.IGNORED;
        }
        if (!isThisUsers(account, event)) {
            // A server owner receives events for everyone on their server.
            // Somebody else's evening is not this user's history, and it is not
            // an error either -- so no sync run and no error on the link.
            log.debug("Ignoring Plex scrobble for account {} on link {}",
                    event.account() == null ? null : event.account().title(), account.getId());
            return Outcome.IGNORED;
        }

        LocalDate today = LocalDate.now(clock.withZone(account.getUser().zone()));
        Optional<ImportRow> row = PlexScrobble.asRow(event, today);
        if (row.isEmpty()) {
            // Music, photos, or a scrobble missing the fields that name what
            // was watched. Nothing to record and nothing to report.
            return Outcome.IGNORED;
        }

        SyncRun run = syncRuns.save(new SyncRun(account));
        try {
            return record(account, row.get(), run);
        } catch (RuntimeException e) {
            run.fail(e.getMessage());
            syncRuns.save(run);
            noteOnLink(account, "Plex sent \"" + row.get().titleText() + "\", and recording it failed: "
                    + e.getMessage(), LinkStatus.ERROR);
            log.warn("Plex delivery failed for link {}: {}", account.getId(), e.toString());
            throw e;
        }
    }

    private Outcome record(LinkedStreamingAccount account, ImportRow row, SyncRun run) {
        List<MatchedRow> matched = matcher.match(List.of(row), PROVIDER_LOOKUPS);
        MatchedRow match = matched.getFirst();

        if (match.status() != MatchStatus.MATCHED || match.titleId() == null) {
            // The acceptance criterion this satisfies: a title Plex reports and
            // the catalogue does not hold is reported, not dropped. The note is
            // the matcher's own, which says which of the several reasons it was.
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
                        + ", but the episode this scrobble names is not in the catalogue.");
            }
            run.finish();
            syncRuns.save(run);
            noteOnLink(account, written ? null : run.getErrorMessage(), LinkStatus.CONNECTED);
            return written ? Outcome.RECORDED : Outcome.UNMATCHED;
        } catch (DataIntegrityViolationException e) {
            // The unique index on (user, origin, origin_ref) fired: this
            // viewing is already recorded. Plex retries a delivery it thinks
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
     * <p>With a Plex username configured, it must match: on a shared server
     * that name is the only thing separating this user's history from their
     * housemate's. Without one, the delivery must at least be for the account
     * that owns the webhook, which is what Plex's {@code user} flag says.
     */
    private boolean isThisUsers(LinkedStreamingAccount account, PlexWebhookPayload event) {
        String expected = account.getAccountLabel();
        if (expected == null || expected.isBlank()) {
            return event.isWebhookOwners();
        }
        String actual = event.account() == null ? null : event.account().title();
        return actual != null
                && actual.trim().toLowerCase(Locale.ROOT).equals(expected.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * The link's own status line: when it last heard from Plex, and what went
     * wrong if anything did.
     *
     * <p>An integration that silently stops is worse than one never offered,
     * so every accepted delivery moves {@code lastSyncAt} even when it could
     * not be matched -- "connected, and it could not place The Bear" is a
     * different thing to tell somebody than "connected".
     */
    private void noteOnLink(LinkedStreamingAccount account, String error, LinkStatus status) {
        account.setLastSyncAt(clock.instant());
        account.setLastSyncError(error);
        account.setStatus(status);
        accounts.save(account);
    }

    private LinkedStreamingAccount authenticate(String token) {
        LinkedStreamingAccount account = WebhookToken.accountId(token)
                .flatMap(accounts::findWithUserAndServiceById)
                .orElseThrow(() -> new WebhookAuthenticationException("Unknown webhook token"));

        if (!WebhookToken.matches(token, account.getWebhookTokenHash())) {
            throw new WebhookAuthenticationException("Unknown webhook token");
        }
        if (!account.isSyncEnabled() || account.getStatus() == LinkStatus.DISCONNECTED) {
            throw new WebhookAuthenticationException("This connection is disconnected");
        }
        return account;
    }

    private PlexWebhookPayload parse(String payload) {
        try {
            return json.readValue(payload, PlexWebhookPayload.class);
        } catch (JacksonException e) {
            // Not a server error: something posted to this URL that Plex did
            // not write. The caller gets 400 and nothing is recorded.
            throw new IllegalArgumentException("Plex payload could not be read: " + e.getMessage());
        }
    }
}
