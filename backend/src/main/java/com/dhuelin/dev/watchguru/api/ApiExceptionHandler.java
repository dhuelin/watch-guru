package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.common.NotFoundException;
import com.dhuelin.dev.watchguru.provider.MetadataProviderException;
import com.dhuelin.dev.watchguru.provider.MetadataProviderNotConfiguredException;
import com.dhuelin.dev.watchguru.security.AccountConflictException;
import com.dhuelin.dev.watchguru.security.CurrentUserService;
import com.dhuelin.dev.watchguru.security.session.SessionService;
import com.dhuelin.dev.watchguru.streaming.plex.WebhookAuthenticationException;
import com.dhuelin.dev.watchguru.streaming.trakt.TraktConnectionService;
import com.dhuelin.dev.watchguru.streaming.trakt.TraktException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/** Maps application exceptions onto RFC 9457 problem responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail onNotFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /**
     * A sign-in whose email address already belongs to a different account.
     *
     * <p>409 rather than quietly creating a second account: to the user, a
     * duplicate account is indistinguishable from having lost everything they
     * ever tracked.
     */
    /**
     * A provider token this service will not accept, at the exchange endpoint.
     *
     * <p>401 with a deliberately flat message. Saying which check failed --
     * unknown issuer, bad signature, wrong audience, expired -- would tell an
     * attacker probing the endpoint exactly how far they got.
     */
    @ExceptionHandler(JwtException.class)
    ProblemDetail onBadProviderToken(JwtException e) {
        log.debug("Rejected a provider token at the exchange endpoint: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "That sign-in could not be verified. Please try again.");
    }

    /** A refresh token that is unknown, spent, revoked or expired. */
    @ExceptionHandler(SessionService.InvalidSessionException.class)
    ProblemDetail onInvalidSession(SessionService.InvalidSessionException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /**
     * A still-valid access token for an account that has since been deleted.
     *
     * <p>401 rather than 404: from the client's point of view this is a dead
     * session, and the correct response is to sign in again rather than to
     * retry the call.
     */
    @ExceptionHandler(CurrentUserService.SessionUserGoneException.class)
    ProblemDetail onSessionUserGone(CurrentUserService.SessionUserGoneException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /**
     * A webhook delivery whose token opens nothing.
     *
     * <p>401 and one flat message for every cause. The caller is somebody's
     * Plex server, or somebody guessing; neither needs to learn whether the
     * link exists, whether the secret was close, or whether it was
     * disconnected.
     */
    @ExceptionHandler(WebhookAuthenticationException.class)
    ProblemDetail onWebhookRejected(WebhookAuthenticationException e) {
        log.debug("Rejected a webhook delivery: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "That webhook URL is not valid.");
    }

    /**
     * Trakt cannot be connected here: no application registered, no key to
     * seal the token with, or an authorisation link that has expired.
     *
     * <p>409 rather than 500, because nothing is broken -- the request cannot
     * be honoured in this deployment's current state, and the message says
     * which state that is.
     */
    @ExceptionHandler(TraktConnectionService.NotAvailableException.class)
    ProblemDetail onTraktUnavailable(TraktConnectionService.NotAvailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Trakt itself refused or could not be reached. */
    @ExceptionHandler(TraktException.class)
    ProblemDetail onTraktFailure(TraktException e) {
        log.warn("Trakt call failed: {}", e.getMessage());
        return e.isAuthFailure()
                ? ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                        "Trakt access has expired. Connect Trakt again.")
                : ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                        "Trakt could not be reached. Try again in a few minutes.");
    }

    @ExceptionHandler(AccountConflictException.class)
    ProblemDetail onAccountConflict(AccountConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onInvalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                detail.isBlank() ? "Request validation failed" : detail);
    }

    /**
     * Missing provider configuration is our fault, not the provider's, so it is
     * reported as 503 rather than 502.
     */
    @ExceptionHandler(MetadataProviderNotConfiguredException.class)
    ProblemDetail onProviderNotConfigured(MetadataProviderNotConfiguredException e) {
        log.error("Metadata provider is not configured: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    /**
     * Upstream metadata failures are reported as 502: the request was valid but
     * the provider could not answer it.
     */
    @ExceptionHandler(MetadataProviderException.class)
    ProblemDetail onProviderFailure(MetadataProviderException e) {
        log.warn("Metadata provider failure: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }
}
