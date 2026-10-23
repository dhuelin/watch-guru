package com.dhuelin.dev.watchguru.security.session;

import com.dhuelin.dev.watchguru.security.CurrentUserService;
import com.dhuelin.dev.watchguru.security.TrustedIssuers;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Exchanges a provider ID token for a session this service controls, and keeps
 * that session alive.
 *
 * <p>The reason this exists at all: Apple's and Google's tokens expire in about
 * an hour and a mobile app cannot renew one without putting a sheet in front of
 * the user. It is also the only way to revoke anything -- nobody can revoke
 * Google's token but Google.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    /**
     * 256 bits from the system CSPRNG. Long enough that guessing is not a
     * threat model, which is also why the stored hash is a plain SHA-256 rather
     * than a slow password hash: there is no low-entropy secret to protect.
     */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    /**
     * How long a nonce is remembered when its token states no expiry. Apple's
     * and Google's always do; this is only so a token without one is not
     * remembered for ever, nor forgotten at once.
     */
    private static final java.time.Duration NONCE_FALLBACK_TTL = java.time.Duration.ofDays(1);

    private final TrustedIssuers trustedIssuers;
    private final CurrentUserService users;
    private final RefreshTokenRepository refreshTokens;
    private final AccessTokenIssuer accessTokens;
    private final AuthNonceRepository nonces;

    public SessionService(TrustedIssuers trustedIssuers,
                          CurrentUserService users,
                          RefreshTokenRepository refreshTokens,
                          AccessTokenIssuer accessTokens,
                          AuthNonceRepository nonces) {
        this.trustedIssuers = trustedIssuers;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
        this.nonces = nonces;
    }

    /**
     * Turns a provider ID token into a session.
     *
     * <p>The provider token is verified by the same decoders the resource
     * server uses -- signature, issuer, expiry and audience -- and then never
     * stored. Provisioning a first-time user is the same code path as before,
     * so nothing about account creation or linking changes here.
     */
    @Transactional
    public Session exchange(String providerToken, String nonce) {
        Jwt jwt = trustedIssuers.verify(providerToken);
        Instant now = Instant.now();
        claimNonce(jwt, nonce, now);
        AppUser user = users.resolve(jwt);
        return start(user, now);
    }

    /**
     * Makes the provider token single-use, and usable only by whoever asked
     * for it.
     *
     * <p>The app makes up a random nonce, hands its SHA-256 to Apple or Google,
     * and gets back a token carrying that hash. It then presents the token with
     * the nonce itself. Two things follow. A token lifted from somewhere --
     * a log, a proxy, a crash report -- is useless without the nonce, which
     * never left the device until this call. And because the hash is recorded
     * here on first use, a token exchanged once cannot be exchanged again for
     * as long as it remains valid, even by somebody holding both halves.
     *
     * <p>Failures are reported as a bad token, through the same flat 401 as
     * every other verification failure: which check failed is not something to
     * tell a caller probing this endpoint.
     */
    private void claimNonce(Jwt jwt, String nonce, Instant now) {
        String claimed = jwt.getClaimAsString("nonce");
        if (claimed == null || nonce == null
                || !MessageDigest.isEqual(hash(nonce).getBytes(StandardCharsets.US_ASCII),
                        claimed.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
            throw new BadJwtException("The token's nonce does not match the one presented");
        }
        Instant expiresAt = jwt.getExpiresAt() != null ? jwt.getExpiresAt() : now.plus(NONCE_FALLBACK_TTL);
        if (nonces.claim(hash(claimed), now, expiresAt) == 0) {
            log.warn("A provider token was presented for a second time (subject {})", jwt.getSubject());
            throw new BadJwtException("That token has already been used");
        }
    }

    /** Clears out nonces whose tokens have expired, and so can no longer be replayed. */
    @Transactional
    public int purgeUsedNonces() {
        return nonces.deleteExpiredBefore(Instant.now());
    }

    /** Issues the first token pair of a new session family. */
    @Transactional
    public Session start(AppUser user, Instant now) {
        return issue(user, UUID.randomUUID(), now);
    }

    /**
     * Rotates a refresh token.
     *
     * <p>Every successful refresh burns the presented token and issues a
     * successor. Presenting a token that has already been exchanged means two
     * parties hold it, and there is no way to tell which is the legitimate one,
     * so the entire family is revoked and both are signed out. That is the
     * intended outcome: a stolen token is worth at most one refresh, and its
     * use is what reveals the theft.
     *
     * <p>{@code noRollbackFor} is load-bearing, not tidiness. Reuse detection
     * revokes the family and then throws, and a rollback would undo the
     * revocation on its way out -- leaving the detection silently doing nothing
     * while every test that only checks for the exception still passed. Caught
     * by {@code SessionRotationTest.reuseRevokesTheFamily}, which asserts the
     * sibling token dies too.
     */
    @Transactional(noRollbackFor = InvalidSessionException.class)
    public Session refresh(String presented) {
        Instant now = Instant.now();
        Optional<RefreshToken> found = refreshTokens.findByTokenHashForUpdate(hash(presented));

        if (found.isEmpty()) {
            // Unknown token. Nothing to revoke, and nothing to say beyond no.
            throw new InvalidSessionException("That session is no longer valid. Please sign in again.");
        }

        RefreshToken token = found.get();

        if (token.getUsedAt() != null) {
            int revoked = refreshTokens.revokeFamily(token.getFamilyId(), now, "reuse_detected");
            log.warn("Refresh token reuse detected for user {}; revoked {} token(s) in family {}",
                    token.getUser().getId(), revoked, token.getFamilyId());
            throw new InvalidSessionException("That session is no longer valid. Please sign in again.");
        }
        if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw new InvalidSessionException("That session is no longer valid. Please sign in again.");
        }

        token.setUsedAt(now);
        refreshTokens.save(token);

        return issue(token.getUser(), token.getFamilyId(), now);
    }

    /**
     * Ends a session.
     *
     * <p>Revokes the whole family rather than the presented token alone:
     * signing out should end the session, not just invalidate the one token the
     * client happened to be holding.
     *
     * <p>Deliberately silent about an unknown token. Sign-out is not a place to
     * tell a caller whether a token exists, and a client whose token has
     * already expired should still see a clean sign-out.
     */
    @Transactional
    public void logout(String presented) {
        refreshTokens.findByTokenHashForUpdate(hash(presented)).ifPresent(token ->
                refreshTokens.revokeFamily(token.getFamilyId(), Instant.now(), "signed_out"));
    }

    /** Revokes every session a user has. Called when an account is deleted. */
    @Transactional
    public void revokeAllFor(Long userId, String reason) {
        int revoked = refreshTokens.revokeAllForUser(userId, Instant.now(), reason);
        if (revoked > 0) {
            log.info("Revoked {} session(s) for user {}: {}", revoked, userId, reason);
        }
    }

    private Session issue(AppUser user, UUID familyId, Instant now) {
        AccessTokenIssuer.Issued access = accessTokens.issue(user, now);

        String refresh = ENCODER.encodeToString(randomBytes());
        Instant refreshExpiresAt = now.plus(accessTokens.refreshTtl());
        refreshTokens.save(new RefreshToken(hash(refresh), user, familyId, now, refreshExpiresAt));

        return new Session(access.token(), access.expiresAt(), refresh, refreshExpiresAt);
    }

    private static byte[] randomBytes() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Required of every Java platform implementation.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** A freshly issued token pair. */
    public record Session(String accessToken,
                          Instant accessTokenExpiresAt,
                          String refreshToken,
                          Instant refreshTokenExpiresAt) {
    }

    /** A refresh or logout that cannot be honoured. Surfaces as 401. */
    public static class InvalidSessionException extends RuntimeException {
        public InvalidSessionException(String message) {
            super(message);
        }
    }
}
