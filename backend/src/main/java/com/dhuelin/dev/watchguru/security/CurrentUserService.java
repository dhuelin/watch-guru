package com.dhuelin.dev.watchguru.security;

import com.dhuelin.dev.watchguru.config.AuthProperties;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves the authenticated token to the {@link AppUser} it represents,
 * provisioning that user on first sign-in.
 *
 * <p>This class is the only place a request turns into a user identity. Nothing
 * above it accepts a caller-supplied user id, which is the whole point of the
 * change: the previous API took the user id from the URL and trusted it.
 */
@Service
public class CurrentUserService {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserService.class);

    /** Longest email the schema will hold. */
    private static final int MAX_EMAIL_LENGTH = 320;
    private static final int MAX_DISPLAY_NAME_LENGTH = 128;

    private final AppUserRepository users;
    private final AuthProperties authProperties;

    public CurrentUserService(AppUserRepository users, AuthProperties authProperties) {
        this.users = users;
        this.authProperties = authProperties;
    }

    /**
     * The user behind the current request.
     *
     * @throws IllegalStateException if called outside an authenticated request;
     *                               every route that reaches this is behind
     *                               {@code authenticated()}, so that would be a
     *                               configuration bug rather than a client error
     */
    @Transactional
    public AppUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new IllegalStateException(
                    "No authenticated JWT on the request; a secured route is misconfigured");
        }
        return resolve(token.getToken());
    }

    /** Resolves a validated token to a user, creating one on first sign-in. */
    @Transactional
    public AppUser resolve(Jwt jwt) {
        String namespacedSubject = namespacedSubject(jwt);

        Optional<AppUser> existing = users.findByAuthSubject(namespacedSubject);
        if (existing.isPresent()) {
            AppUser user = existing.get();
            user.setLastLoginAt(Instant.now());
            return user;
        }

        return provision(jwt, namespacedSubject);
    }

    /**
     * Subject key for a token.
     *
     * <p>Namespaced by issuer because {@code sub} is only unique within the
     * provider that issued it. Two providers each handing out short opaque ids
     * could otherwise collide, and a collision here means one user reading
     * another's watch history.
     */
    static String namespacedSubject(Jwt jwt) {
        String issuer = jwt.getIssuer() == null ? "" : jwt.getIssuer().toString();
        return issuer + "|" + jwt.getSubject();
    }

    private AppUser provision(Jwt jwt, String namespacedSubject) {
        String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
        String email = normalisedEmail(jwt);
        boolean emailVerified = emailVerified(jwt);

        // A second provider may adopt an existing account only when both sides
        // of the address are verified and the incoming issuer is trusted to
        // verify. Anything less would let a provider that does not check
        // addresses hand out access to accounts it never owned.
        if (email != null && emailVerified && trustsEmailVerification(issuer)) {
            Optional<AppUser> byEmail = users.findByEmail(email);
            if (byEmail.isPresent()) {
                AppUser user = byEmail.get();
                if (user.getAuthSubject() == null) {
                    // Pre-authentication row: claim it.
                    log.info("Claiming pre-auth account {} for issuer {}", user.getId(), issuer);
                    return link(user, namespacedSubject, issuer, emailVerified);
                }
                if (!user.isEmailVerified()) {
                    throw new AccountConflictException(
                            "An account already exists for this email address but its address was "
                                    + "never verified. Sign in with the provider you originally used.");
                }
                log.info("Linking issuer {} to existing account {} on verified email", issuer, user.getId());
                return link(user, namespacedSubject, issuer, true);
            }
        }

        if (email != null && users.findByEmail(email).isPresent()) {
            // The address is taken and this token did not earn the right to it.
            throw new AccountConflictException(
                    "An account already exists for this email address. Sign in with the provider "
                            + "you originally used.");
        }

        AppUser user = new AppUser(
                email != null ? email : placeholderEmail(namespacedSubject),
                displayName(jwt, email));
        user.setAuthSubject(namespacedSubject);
        user.setAuthIssuer(issuer);
        user.setEmailVerified(email != null && emailVerified);
        user.setLastLoginAt(Instant.now());

        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two requests from the same new user can race here -- a phone that
            // fires several calls the moment sign-in completes does it reliably.
            // The unique index is what actually arbitrates; the loser re-reads.
            log.debug("Concurrent provisioning for subject {}, re-reading", namespacedSubject);
            return users.findByAuthSubject(namespacedSubject)
                    .orElseThrow(() -> e);
        }
    }

    private AppUser link(AppUser user, String namespacedSubject, String issuer, boolean emailVerified) {
        user.setAuthSubject(namespacedSubject);
        user.setAuthIssuer(issuer);
        user.setEmailVerified(emailVerified);
        user.setLastLoginAt(Instant.now());
        return users.save(user);
    }

    private boolean trustsEmailVerification(String issuer) {
        if (issuer == null) {
            return false;
        }
        return authProperties.issuers().stream()
                .anyMatch(candidate -> issuer.equals(candidate.uri()) && candidate.trustEmailVerification());
    }

    /**
     * The {@code email} claim, lowercased, or null when absent.
     *
     * <p>Apple returns the address only on the first authorisation for a given
     * app, and it may be a private relay address. Both are fine: the relay
     * address is stable per user per app, and a missing address falls back to a
     * placeholder rather than failing the sign-in.
     */
    private static String normalisedEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalised = email.trim().toLowerCase(Locale.ROOT);
        return normalised.length() > MAX_EMAIL_LENGTH
                ? normalised.substring(0, MAX_EMAIL_LENGTH)
                : normalised;
    }

    /**
     * Reads {@code email_verified}, which is a boolean in the specification and
     * a string in practice -- Apple has historically sent {@code "true"}.
     * Treating the string form as unverified would silently break linking for
     * every Apple user, so both are accepted.
     */
    private static boolean emailVerified(Jwt jwt) {
        Object claim = jwt.getClaim("email_verified");
        return switch (claim) {
            case Boolean b -> b;
            case String s -> Boolean.parseBoolean(s);
            case null, default -> false;
        };
    }

    private static String displayName(Jwt jwt, String email) {
        String name = jwt.getClaimAsString("name");
        if (name == null || name.isBlank()) {
            // Apple sends the name only in the initial authorisation response,
            // not in the token, so most Apple sign-ins land here. The apps are
            // expected to PATCH a real name straight after first sign-in.
            name = email != null ? email.substring(0, email.indexOf('@') < 0 ? email.length() : email.indexOf('@'))
                    : "Watch Guru user";
        }
        name = name.trim();
        return name.length() > MAX_DISPLAY_NAME_LENGTH ? name.substring(0, MAX_DISPLAY_NAME_LENGTH) : name;
    }

    /**
     * Stand-in address for a token that carried none.
     *
     * <p>The column is unique and not-null, so something has to go here. Two
     * requirements: it must not collide (a collision is a failed sign-in for a
     * real person), and it must not be deliverable. A SHA-256 of the namespaced
     * subject gives the first; the reserved {@code .invalid} TLD, which is
     * guaranteed never to resolve, gives the second.
     */
    static String placeholderEmail(String namespacedSubject) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every Java platform implementation.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        byte[] hash = digest.digest(namespacedSubject.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(32);
        for (int i = 0; i < 16; i++) {
            hex.append(String.format("%02x", hash[i]));
        }
        return hex + "@users.noreply.watch-guru.invalid";
    }

    /** Deletes the current user and, by cascade, everything they own. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteCurrentUser() {
        AppUser user = require();
        log.info("Deleting account {} at the user's request", user.getId());
        users.deleteAppUserById(user.getId());
    }
}
