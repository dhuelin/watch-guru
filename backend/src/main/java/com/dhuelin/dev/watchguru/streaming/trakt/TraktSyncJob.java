package com.dhuelin.dev.watchguru.streaming.trakt;

import com.dhuelin.dev.watchguru.config.TraktProperties;
import com.dhuelin.dev.watchguru.security.oauth.OAuthStateService;
import com.dhuelin.dev.watchguru.streaming.domain.LinkStatus;
import com.dhuelin.dev.watchguru.streaming.domain.LinkedStreamingAccount;
import com.dhuelin.dev.watchguru.streaming.repository.LinkedStreamingAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pulls every connected Trakt account on a schedule.
 *
 * <p>A separate bean from {@link TraktSyncService} for the same mechanical
 * reason as the notification job: the per-account work manages its own
 * transactions, and a loop inside that bean would bypass the proxy that makes
 * them so.
 *
 * <p>Every two hours rather than every few minutes. Trakt history is somebody
 * else's scrobbles arriving at somebody else's pace, and a tracker that is two
 * hours behind is indistinguishable from a live one for anybody not staring at
 * it -- while polling every user every minute would spend a shared rate limit
 * on nothing.
 */
@Component
public class TraktSyncJob {

    private static final Logger log = LoggerFactory.getLogger(TraktSyncJob.class);

    private final LinkedStreamingAccountRepository accounts;
    private final TraktSyncService sync;
    private final OAuthStateService oauthStates;
    private final TraktProperties properties;

    public TraktSyncJob(LinkedStreamingAccountRepository accounts,
                        TraktSyncService sync,
                        OAuthStateService oauthStates,
                        TraktProperties properties) {
        this.accounts = accounts;
        this.sync = sync;
        this.oauthStates = oauthStates;
        this.properties = properties;
    }

    @Scheduled(cron = "${watch-guru.trakt.cron:0 20 */2 * * *}")
    public void run() {
        syncAll();
        // Nothing else clears these, and an abandoned authorisation leaves a
        // row behind. Here rather than in its own job because it is two orders
        // of magnitude cheaper than what this job already does.
        oauthStates.purgeExpired();
    }

    /** @return how many viewings were recorded across every connection */
    public int syncAll() {
        if (!properties.isConfigured()) {
            return 0;
        }
        // CONNECTED and ERROR both: a connection that failed last time is
        // exactly the one worth trying again, and only a disconnected or
        // revoked link is genuinely finished.
        List<LinkedStreamingAccount> connected = accounts.findSyncable(
                TraktConnectionService.TRAKT_SLUG, List.of(LinkStatus.CONNECTED, LinkStatus.ERROR));

        int imported = 0;
        for (LinkedStreamingAccount account : connected) {
            try {
                imported += sync.sync(account).map(TraktSyncService.Result::imported).orElse(0);
            } catch (RuntimeException e) {
                // One account's failure must not cost everybody else their
                // history, so the loop carries on and the failure is recorded
                // against that connection rather than thrown at the scheduler.
                log.warn("Trakt sync failed for link {}: {}", account.getId(), e.toString());
            }
        }
        if (imported > 0) {
            log.info("Trakt sync recorded {} viewing(s) across {} connection(s)",
                    imported, connected.size());
        }
        return imported;
    }
}
