package com.dhuelin.dev.watchguru.streaming.plex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * The credential in a webhook URL: how one is minted, and how one presented
 * later is checked.
 *
 * <p>A webhook has no bearer token and no signature -- Plex posts to whatever
 * URL it was given -- so the URL itself is the credential. That makes two
 * things non-negotiable. The secret is 256 bits from {@link SecureRandom}, so
 * guessing one is not a strategy; and only its SHA-256 hash is stored, so a
 * leaked database dump yields no working URL.
 *
 * <p>The token is {@code <account id>.<secret>} rather than a bare secret.
 * Looking a hash up directly would work, but hashing on every request and
 * scanning for a match invites a timing oracle; naming the row makes the
 * lookup a primary-key read and leaves a single constant-time comparison as
 * the only thing that decides the answer.
 *
 * <p>No plain hash-of-password concern applies here: the secret is full-entropy
 * random rather than user-chosen, so a single SHA-256 is not a shortcut past
 * anything. A password would need a slow KDF; this does not.
 */
public final class WebhookToken {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SECRET_BYTES = 32;

    private WebhookToken() {
    }

    /** A fresh token for the given link. Returned once and never recoverable. */
    public static String issue(long accountId) {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return accountId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    /** The hash to store for a token, which is the only part that is kept. */
    public static String hash(String token) {
        return HexFormat.of().formatHex(sha256(secretOf(token)));
    }

    /** The account a presented token claims to belong to, if it is well formed. */
    public static Optional<Long> accountId(String token) {
        int dot = token == null ? -1 : token.indexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(token.substring(0, dot)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Whether a presented token matches a stored hash.
     *
     * <p>{@link MessageDigest#isEqual} rather than {@code String.equals}: the
     * comparison runs on attacker-supplied input, and one that returns early on
     * the first wrong character leaks how much of a guess was right.
     */
    public static boolean matches(String presented, String storedHash) {
        if (presented == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(presented).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    private static String secretOf(String token) {
        int dot = token.indexOf('.');
        return dot < 0 ? token : token.substring(dot + 1);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // Every JVM ships SHA-256; this cannot happen outside a broken runtime.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
