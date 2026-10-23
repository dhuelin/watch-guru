package com.dhuelin.dev.watchguru.security.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

/**
 * Deletes refresh tokens that can no longer authenticate anything.
 *
 * <p>Every refresh writes a row, so without this the table grows by one row per
 * active user every fifteen minutes, forever. Tokens are kept for a week past
 * expiry rather than dropped at once, for the reason
 * {@link RefreshTokenRepository#deleteExpiredBefore} gives: reuse detection has
 * to be able to find a token that died a moment ago.
 */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    static final Duration GRACE = Duration.ofDays(7);

    private final RefreshTokenRepository refreshTokens;
    private final SessionService sessions;
    private final Clock clock;

    public RefreshTokenCleanupJob(RefreshTokenRepository refreshTokens, SessionService sessions, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Scheduled(cron = "${watch-guru.auth.session.cleanup-cron:0 15 4 * * *}")
    @Transactional
    public int run() {
        int deleted = refreshTokens.deleteExpiredBefore(clock.instant().minus(GRACE));
        if (deleted > 0) {
            log.info("Deleted {} expired refresh token(s)", deleted);
        }
        // Sign-in nonces too: each is only needed while the provider token it
        // came from could still be presented.
        sessions.purgeUsedNonces();
        return deleted;
    }
}
