package com.dhuelin.dev.watchguru.security.oauth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Issues and redeems the {@code state} of an OAuth authorisation.
 *
 * <p>Three properties, each of which matters: it is unguessable, it is
 * single-use, and it expires. Unguessable stops somebody forging a callback
 * for a user they have never met; single-use stops a captured redirect being
 * replayed; and an expiry means a flow abandoned in a browser tab does not
 * stay redeemable for ever.
 */
@Service
public class OAuthStateService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int STATE_BYTES = 32;

    private final OAuthStateRepository states;
    private final Clock clock;

    public OAuthStateService(OAuthStateRepository states, Clock clock) {
        this.states = states;
        this.clock = clock;
    }

    /** @return the state to send to the provider; only its hash is kept */
    @Transactional
    public String issue(Long userId, String provider, Duration ttl) {
        byte[] material = new byte[STATE_BYTES];
        RANDOM.nextBytes(material);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(material);

        states.save(new OAuthState(hash(state), userId, provider,
                clock.instant(), clock.instant().plus(ttl)));
        return state;
    }

    /**
     * @return the user who started this flow, or empty when the state is
     *         unknown, expired, already used, or was issued for a different
     *         provider
     */
    @Transactional
    public Optional<Long> redeem(String state, String provider) {
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }
        Optional<OAuthState> found = states.findById(hash(state));
        // Deleted whatever happens next: a state presented once is spent, and
        // leaving an expired or mismatched row behind would let it be tried
        // again with a different provider name.
        found.ifPresent(states::delete);

        return found
                .filter(row -> row.getProvider().equals(provider))
                .filter(row -> row.getExpiresAt().isAfter(clock.instant()))
                .map(OAuthState::getUserId);
    }

    /** Clears out flows nobody finished. */
    @Transactional
    public int purgeExpired() {
        return states.deleteExpired(clock.instant());
    }

    private static String hash(String state) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(state.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
