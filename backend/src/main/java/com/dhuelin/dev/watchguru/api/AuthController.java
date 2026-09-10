package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.Requests;
import com.dhuelin.dev.watchguru.api.dto.Responses;
import com.dhuelin.dev.watchguru.security.session.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/**
 * Turning a provider sign-in into a session, and keeping it alive.
 *
 * <p>These are the only routes that do not require a bearer token, because they
 * are how a bearer token is obtained. Each verifies its own credential from the
 * request body.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Exchanging a provider sign-in for a session")
public class AuthController {

    private final SessionService sessions;

    public AuthController(SessionService sessions) {
        this.sessions = sessions;
    }

    /**
     * Exchanges an Apple or Google ID token for a session.
     *
     * <p>Also the account-creation route: a token from a trusted issuer that
     * has never been seen before provisions a user. There is no separate
     * sign-up call and never was.
     */
    @PostMapping("/session")
    @Operation(operationId = "createSession",
            summary = "Exchange a provider ID token for a session",
            description = "Verifies the provider's ID token and returns an access token and a "
                    + "refresh token issued by this service. Provisions the account on first use.")
    public ResponseEntity<Responses.SessionResponse> create(
            @Valid @RequestBody Requests.ExchangeToken request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(sessions.exchange(request.providerToken())));
    }

    /**
     * Rotates a refresh token.
     *
     * <p>The previous refresh token stops working the moment this succeeds, so
     * a client must store the new one before making its next call. Presenting a
     * spent token ends the whole session -- see {@link SessionService#refresh}.
     */
    @PostMapping("/refresh")
    @Operation(operationId = "refreshSession",
            summary = "Exchange a refresh token for a new session",
            description = "Rotates the refresh token. The presented token is invalidated, and "
                    + "presenting it a second time revokes the entire session.")
    public Responses.SessionResponse refresh(@Valid @RequestBody Requests.RefreshSession request) {
        return toResponse(sessions.refresh(request.refreshToken()));
    }

    /**
     * Ends a session.
     *
     * <p>Returns 204 whether or not the token was live. Sign-out is not a place
     * to tell a caller whether a token exists.
     */
    @PostMapping("/logout")
    @Operation(operationId = "endSession",
            summary = "Revoke a session",
            description = "Revokes the refresh token and every token descended from the same "
                    + "sign-in. Succeeds even if the token was already invalid.")
    public ResponseEntity<Void> logout(@Valid @RequestBody Requests.RefreshSession request) {
        sessions.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private static Responses.SessionResponse toResponse(SessionService.Session session) {
        long expiresIn = Math.max(0, Duration.between(Instant.now(), session.accessTokenExpiresAt()).toSeconds());
        return new Responses.SessionResponse(
                session.accessToken(),
                "Bearer",
                expiresIn,
                session.refreshToken(),
                session.refreshTokenExpiresAt());
    }
}
