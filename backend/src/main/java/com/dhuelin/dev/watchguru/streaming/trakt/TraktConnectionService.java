package com.dhuelin.dev.watchguru.streaming.trakt;

import com.dhuelin.dev.watchguru.common.NotFoundException;
import com.dhuelin.dev.watchguru.config.PublicUrlProperties;
import com.dhuelin.dev.watchguru.config.TraktProperties;
import com.dhuelin.dev.watchguru.security.credentials.EncryptedCredentialStore;
import com.dhuelin.dev.watchguru.security.oauth.OAuthStateService;
import com.dhuelin.dev.watchguru.streaming.domain.LinkStatus;
import com.dhuelin.dev.watchguru.streaming.domain.LinkedStreamingAccount;
import com.dhuelin.dev.watchguru.streaming.domain.StreamingService;
import com.dhuelin.dev.watchguru.streaming.repository.LinkedStreamingAccountRepository;
import com.dhuelin.dev.watchguru.streaming.repository.StreamingServiceRepository;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Authorising Trakt, and keeping that authorisation usable.
 *
 * <p>The OAuth exchange happens here and not on the phone, which is the whole
 * reason for the round trip through this service: the client secret is part of
 * the exchange, and a secret shipped inside two app binaries is not a secret.
 * The apps only ever see a URL to open and, later, whether it worked.
 */
@Service
public class TraktConnectionService {

    private static final Logger log = LoggerFactory.getLogger(TraktConnectionService.class);

    public static final String TRAKT_SLUG = "trakt";
    public static final String CALLBACK_PATH = "/api/v1/streaming/trakt/callback";

    private final LinkedStreamingAccountRepository accounts;
    private final StreamingServiceRepository services;
    private final AppUserRepository users;
    private final OAuthStateService oauthStates;
    private final EncryptedCredentialStore credentials;
    private final TraktClient trakt;
    private final TraktProperties properties;
    private final PublicUrlProperties urls;
    private final JsonMapper json;
    private final Clock clock;

    public TraktConnectionService(LinkedStreamingAccountRepository accounts,
                                  StreamingServiceRepository services,
                                  AppUserRepository users,
                                  OAuthStateService oauthStates,
                                  EncryptedCredentialStore credentials,
                                  TraktClient trakt,
                                  TraktProperties properties,
                                  PublicUrlProperties urls,
                                  JsonMapper json,
                                  Clock clock) {
        this.accounts = accounts;
        this.services = services;
        this.users = users;
        this.oauthStates = oauthStates;
        this.credentials = credentials;
        this.trakt = trakt;
        this.properties = properties;
        this.urls = urls;
        this.json = json;
        this.clock = clock;
    }

    /** Raised before an authorisation starts, never after. */
    public static class NotAvailableException extends RuntimeException {
        public NotAvailableException(String message) {
            super(message);
        }
    }

    /**
     * Where to send the user to authorise.
     *
     * <p>Refuses up front when this deployment has no Trakt application or no
     * key to seal the token with. Discovering either after the user has
     * granted access means they authorised something that then threw the token
     * away, and the only visible symptom is a connection that never works.
     */
    @Transactional
    public String authorizeUrl(AppUser user, String requestBaseUrl) {
        if (!properties.isConfigured()) {
            throw new NotAvailableException(
                    "This server has no Trakt application configured, so Trakt cannot be connected here.");
        }
        if (!credentials.isConfigured()) {
            throw new NotAvailableException(
                    "This server cannot store third-party credentials safely, so Trakt cannot be "
                            + "connected here.");
        }

        String state = oauthStates.issue(user.getId(), TRAKT_SLUG, properties.stateTtl());
        return UriComponentsBuilder.fromUriString(properties.authorizeUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", redirectUri(requestBaseUrl))
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    /**
     * Finishes the flow the browser came back from.
     *
     * @return the connected account
     * @throws NotAvailableException when the state does not name a user -- an
     *         unknown, expired, replayed or forged callback, all of which are
     *         one answer to whoever is asking
     */
    @Transactional
    public LinkedStreamingAccount complete(String state, String code, String requestBaseUrl) {
        Long userId = oauthStates.redeem(state, TRAKT_SLUG)
                .orElseThrow(() -> new NotAvailableException(
                        "That authorisation link is not valid any more. Start again from the app."));

        AppUser user = users.findById(userId)
                .orElseThrow(() -> NotFoundException.of("User", userId));
        StreamingService service = services.findBySlug(TRAKT_SLUG)
                .orElseThrow(() -> NotFoundException.of("StreamingService", TRAKT_SLUG));

        TraktResponses.Token token = trakt.exchange(code, redirectUri(requestBaseUrl));
        TraktTokens tokens = TraktTokens.from(token, clock.instant());

        LinkedStreamingAccount account = accounts
                .findByUserIdAndStreamingServiceId(user.getId(), service.getId())
                .orElseGet(() -> new LinkedStreamingAccount(user, service));

        String ref = account.getCredentialRef();
        if (ref == null) {
            account.setCredentialRef(credentials.store(write(tokens)));
        } else {
            credentials.replace(ref, write(tokens));
        }
        account.setStatus(LinkStatus.CONNECTED);
        account.setSyncEnabled(true);
        account.setLastSyncError(null);
        // Deliberately not cleared: a reconnection of an existing link keeps
        // its cursor, so re-authorising after an expired token does not
        // re-read the whole history it already has.
        return accounts.save(account);
    }

    /**
     * Forgets the connection, telling Trakt first.
     *
     * <p>Order matters: the token is revoked at Trakt while it is still
     * readable, and only then deleted here. The other way round would leave a
     * live token at Trakt that nobody can revoke any more.
     */
    @Transactional
    public void disconnect(AppUser user) {
        accounts.findByUserIdAndStreamingServiceSlug(user.getId(), TRAKT_SLUG).ifPresent(account -> {
            tokensFor(account).ifPresent(tokens -> trakt.revoke(tokens.accessToken()));

            credentials.delete(account.getCredentialRef());
            account.setCredentialRef(null);
            account.setSyncEnabled(false);
            account.setStatus(LinkStatus.DISCONNECTED);
            accounts.save(account);
        });
    }

    @Transactional(readOnly = true)
    public Optional<LinkedStreamingAccount> find(AppUser user) {
        return accounts.findByUserIdAndStreamingServiceSlug(user.getId(), TRAKT_SLUG);
    }

    /**
     * A usable access token, renewing it when it is close to expiry.
     *
     * <p>Renewal writes the new pair back before the caller uses it: Trakt
     * issues a new refresh token each time, and a refresh whose result was not
     * stored leaves the connection holding a refresh token that has already
     * been spent.
     */
    @Transactional
    public Optional<String> accessTokenFor(LinkedStreamingAccount account) {
        Optional<TraktTokens> stored = tokensFor(account);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        TraktTokens tokens = stored.get();
        if (!tokens.needsRenewal(clock.instant())) {
            return Optional.of(tokens.accessToken());
        }
        if (tokens.refreshToken() == null) {
            return Optional.empty();
        }

        TraktTokens renewed = TraktTokens.from(
                trakt.refresh(tokens.refreshToken(), redirectUri(null)), clock.instant());
        credentials.replace(account.getCredentialRef(), write(renewed));
        return Optional.of(renewed.accessToken());
    }

    private Optional<TraktTokens> tokensFor(LinkedStreamingAccount account) {
        return credentials.read(account.getCredentialRef()).flatMap(this::read);
    }

    /**
     * Where Trakt sends the user back.
     *
     * <p>The configured value wins, because it has to match the application
     * registration character for character; only then the public base URL, and
     * only then the request's own. An exchange sent with a different redirect
     * than the authorisation is refused by Trakt, so this must produce the same
     * answer in both halves of the flow -- which is why the request is the last
     * resort rather than the first.
     */
    private String redirectUri(String requestBaseUrl) {
        if (properties.redirectUri() != null && !properties.redirectUri().isBlank()) {
            return properties.redirectUri().trim();
        }
        String base = urls.trimmed() != null ? urls.trimmed() : requestBaseUrl;
        if (base == null) {
            throw new NotAvailableException(
                    "This server does not know its own public address, so Trakt cannot send anybody "
                            + "back to it. Set WATCH_GURU_PUBLIC_BASE_URL.");
        }
        return base + CALLBACK_PATH;
    }

    private String write(TraktTokens tokens) {
        return json.writeValueAsString(tokens);
    }

    private Optional<TraktTokens> read(String value) {
        try {
            return Optional.of(json.readValue(value, TraktTokens.class));
        } catch (JacksonException e) {
            // Sealed by an older shape of this record, or corrupt. Either way
            // the connection is finished; the user reconnects.
            log.warn("Stored Trakt tokens could not be read: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
