package com.dhuelin.dev.watchguru.streaming.trakt;

import com.dhuelin.dev.watchguru.config.TraktProperties;
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
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads what somebody watched from Trakt and records it here.
 *
 * <p>Trakt is the highest-value of the three integrations for a reason that has
 * nothing to do with Trakt itself: other people have already solved the hard
 * part. Somebody scrobbling from Plex, Kodi or Infuse already has their whole
 * viewing in Trakt, so one connection here picks up every source they use.
 *
 * <p>Not transactional as a whole, for the reason {@code PlexWebhookService}
 * gives: each write runs in its own transaction, a duplicate ends in a
 * unique-index violation, and an enclosing transaction would be marked
 * rollback-only by it -- taking the sync-run record down at the moment it
 * became interesting.
 */
@Service
public class TraktSyncService {

    private static final Logger log = LoggerFactory.getLogger(TraktSyncService.class);

    /**
     * Metadata-provider lookups one run may spend.
     *
     * <p>A first sync is somebody's whole history, and a decade of viewing
     * contains hundreds of titles this catalogue has never held. Unbounded,
     * one connection could spend an API key's daily budget in a minute; at
     * this budget the rest come back as unmatched and the next run picks them
     * up, because the cursor only advances past what was read.
     */
    private static final int PROVIDER_LOOKUPS_PER_RUN = 25;

    private final LinkedStreamingAccountRepository accounts;
    private final SyncRunRepository syncRuns;
    private final TraktConnectionService connections;
    private final TraktClient trakt;
    private final ImportMatcher matcher;
    private final StreamingWatchWriter writer;
    private final TraktProperties properties;
    private final Clock clock;

    public TraktSyncService(LinkedStreamingAccountRepository accounts,
                            SyncRunRepository syncRuns,
                            TraktConnectionService connections,
                            TraktClient trakt,
                            ImportMatcher matcher,
                            StreamingWatchWriter writer,
                            TraktProperties properties,
                            Clock clock) {
        this.accounts = accounts;
        this.syncRuns = syncRuns;
        this.connections = connections;
        this.trakt = trakt;
        this.matcher = matcher;
        this.writer = writer;
        this.properties = properties;
        this.clock = clock;
    }

    /** What one run did. */
    public record Result(int imported, int skipped, int failed, List<String> problems) {
    }

    /**
     * Reads everything watched since this connection's cursor.
     *
     * @return what the run did, or empty when the account cannot be synced --
     *         disconnected, or holding a token this deployment can no longer
     *         open
     */
    public Optional<Result> sync(LinkedStreamingAccount account) {
        if (!account.isSyncable()) {
            return Optional.empty();
        }
        String token = accessToken(account).orElse(null);
        if (token == null) {
            // The refresh token is spent, revoked, or sealed under a key this
            // deployment no longer holds. All of them mean the same to the
            // user: authorise again.
            markBroken(account, "Trakt access has expired. Connect Trakt again to resume syncing.");
            return Optional.empty();
        }

        SyncRun run = syncRuns.save(new SyncRun(account));
        try {
            Result result = read(account, token, run);
            run.finish();
            syncRuns.save(run);
            return Optional.of(result);
        } catch (TraktException e) {
            run.fail(e.getMessage());
            syncRuns.save(run);
            markBroken(account, e.isAuthFailure()
                    ? "Trakt access has expired. Connect Trakt again to resume syncing."
                    : "Trakt could not be reached. The next sync will try again.");
            log.warn("Trakt sync failed for link {}: {}", account.getId(), e.toString());
            return Optional.empty();
        }
    }

    private Result read(LinkedStreamingAccount account, String token, SyncRun run) {
        AppUser user = account.getUser();
        Instant since = cursorOf(account);
        List<String> problems = new ArrayList<>();
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        Instant newest = since;

        for (int page = 1; page <= properties.maxPages(); page++) {
            TraktResponses.HistoryPage historyPage = trakt.history(token, since, page);
            if (historyPage.items().isEmpty()) {
                break;
            }

            for (TraktResponses.HistoryItem item : historyPage.items()) {
                Optional<ImportRow> row = TraktHistory.asRow(item, user.zone());
                if (row.isEmpty()) {
                    skipped++;
                    continue;
                }
                switch (record(account, row.get(), item.watchedAt())) {
                    case IMPORTED -> imported++;
                    case DUPLICATE -> skipped++;
                    case UNMATCHED -> {
                        failed++;
                        if (problems.size() < 10) {
                            problems.add(row.get().titleText());
                        }
                    }
                }
                if (newest == null || item.watchedAt().isAfter(newest)) {
                    newest = item.watchedAt();
                }
            }

            if (!historyPage.hasMore()) {
                break;
            }
        }

        run.setItemsImported(imported);
        run.setItemsSkipped(skipped);
        run.setItemsFailed(failed);
        if (!problems.isEmpty()) {
            run.setErrorMessage(failed + " item(s) could not be matched, including "
                    + String.join(", ", problems) + ".");
        }
        advance(account, newest, run.getErrorMessage());
        return new Result(imported, skipped, failed, problems);
    }

    private enum Outcome { IMPORTED, DUPLICATE, UNMATCHED }

    private Outcome record(LinkedStreamingAccount account, ImportRow row, Instant watchedAt) {
        List<MatchedRow> matched = matcher.match(List.of(row), PROVIDER_LOOKUPS_PER_RUN);
        MatchedRow match = matched.getFirst();
        if (match.status() != MatchStatus.MATCHED || match.titleId() == null) {
            return Outcome.UNMATCHED;
        }
        try {
            boolean written = writer.write(account.getUser(), match,
                    account.getStreamingService().getId(), watchedAt);
            return written ? Outcome.IMPORTED : Outcome.UNMATCHED;
        } catch (DataIntegrityViolationException e) {
            // Already recorded: the unique index on (user, origin, origin_ref)
            // fired, which is how re-reading an overlapping window costs
            // nothing. Trakt's history id makes this exact rather than a guess.
            return Outcome.DUPLICATE;
        }
    }

    /**
     * Where the next run starts.
     *
     * <p>A minute before the newest item already read, not the instant itself.
     * Trakt's {@code start_at} is inclusive, and two viewings can share a
     * second; overlapping slightly costs a handful of duplicate ids that the
     * unique index discards, while starting exactly at the newest risks
     * skipping whatever shared its timestamp.
     */
    private void advance(LinkedStreamingAccount account, Instant newest, String error) {
        if (newest != null) {
            account.setLastSyncCursor(newest.minusSeconds(60).toString());
        }
        account.setLastSyncAt(clock.instant());
        account.setLastSyncError(error);
        account.setStatus(LinkStatus.CONNECTED);
        accounts.save(account);
    }

    private Instant cursorOf(LinkedStreamingAccount account) {
        String cursor = account.getLastSyncCursor();
        if (cursor == null || cursor.isBlank()) {
            // No cursor: the whole history, bounded by maxPages, continued by
            // the next run. A first connection should bring somebody's library
            // with it rather than starting from today.
            return null;
        }
        try {
            return Instant.parse(cursor);
        } catch (RuntimeException e) {
            log.warn("Link {} has an unreadable sync cursor '{}'; reading from the start",
                    account.getId(), cursor);
            return null;
        }
    }

    private Optional<String> accessToken(LinkedStreamingAccount account) {
        try {
            return connections.accessTokenFor(account);
        } catch (TraktException e) {
            log.warn("Could not renew Trakt access for link {}: {}", account.getId(), e.toString());
            return Optional.empty();
        }
    }

    private void markBroken(LinkedStreamingAccount account, String message) {
        account.setStatus(LinkStatus.ERROR);
        account.setLastSyncError(message);
        account.setLastSyncAt(clock.instant());
        accounts.save(account);
    }
}
