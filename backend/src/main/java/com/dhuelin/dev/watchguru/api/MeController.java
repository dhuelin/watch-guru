package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.ApiMapper;
import com.dhuelin.dev.watchguru.api.dto.Requests;
import com.dhuelin.dev.watchguru.api.dto.Responses;
import com.dhuelin.dev.watchguru.security.CurrentUserService;
import com.dhuelin.dev.watchguru.streaming.trakt.TraktConnectionService;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * The signed-in user's own profile.
 *
 * <p>There is no endpoint for creating a user: an account comes into existence
 * the first time a valid token arrives, so there is nothing for a client to
 * call. Nor is there a way to read another user's profile.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private static final Logger log = LoggerFactory.getLogger(MeController.class);

    private final CurrentUserService currentUser;
    private final AppUserRepository users;
    private final ApiMapper mapper;
    private final TraktConnectionService trakt;

    public MeController(CurrentUserService currentUser,
                        AppUserRepository users,
                        ApiMapper mapper,
                        TraktConnectionService trakt) {
        this.currentUser = currentUser;
        this.users = users;
        this.mapper = mapper;
        this.trakt = trakt;
    }

    @GetMapping
    @Operation(operationId = "getProfile")
    public Responses.UserResponse me() {
        return mapper.toUser(currentUser.require());
    }

    /**
     * Updates the profile fields a user controls.
     *
     * <p>Region and language are not cosmetic: region decides which streaming
     * offers are shown, and language is passed to the metadata provider.
     *
     * <p>Display name matters more than it looks. Sign in with Apple returns a
     * name only in the first authorisation response and never in the token, so
     * for most Apple users the name is a placeholder until the app sends the
     * one it captured at sign-in.
     */
    @PatchMapping
    @Operation(operationId = "updateProfile")
    public Responses.UserResponse update(@Valid @RequestBody Requests.UpdateProfile request) {
        AppUser user = currentUser.require();
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        if (request.region() != null) {
            user.setRegion(request.region().toUpperCase(Locale.ROOT));
        }
        if (request.language() != null) {
            user.setLanguage(request.language());
        }
        if (request.timeZone() != null) {
            user.setTimeZone(request.timeZone());
        }
        return mapper.toUser(users.save(user));
    }

    /**
     * Deletes the account and everything in it, irreversibly.
     *
     * <p>Required by both app stores for any app that lets a user create an
     * account, and it has to be a real delete: watchlist, episode progress,
     * watch history and linked accounts all go with it, by database cascade.
     *
     * <p>A cascade is not enough on its own. A Trakt token is a credential of
     * somebody else's held at Trakt as well as here, and deleting our copy
     * would strand a live one there that nobody can revoke any more -- the
     * same failure
     * {@link TraktConnectionService#disconnect(AppUser) disconnect} orders
     * itself to avoid. So it is revoked first, and only then is the row it
     * lives in deleted.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteAccount")
    public void deleteAccount() {
        AppUser user = currentUser.require();
        revokeThirdPartyTokens(user);
        currentUser.deleteCurrentUser();
    }

    /**
     * Revokes what this account holds elsewhere, without letting that stand in
     * the way of the delete.
     *
     * <p>Trakt being unreachable must not leave somebody unable to delete
     * their account; an unrevoked token expires on Trakt's own schedule, which
     * is a far smaller harm than a delete that refuses to happen. The failure
     * is logged rather than swallowed silently, because it is the one case
     * where a token outlives the account that owned it.
     */
    private void revokeThirdPartyTokens(AppUser user) {
        try {
            trakt.disconnect(user);
        } catch (RuntimeException e) {
            log.warn("Could not revoke the Trakt token for account {} before deleting it. "
                    + "Deleting anyway; the token will expire on Trakt's schedule.",
                    user.getId(), e);
        }
    }
}
