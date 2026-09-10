package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.ApiMapper;
import com.dhuelin.dev.watchguru.api.dto.Requests;
import com.dhuelin.dev.watchguru.api.dto.Responses;
import com.dhuelin.dev.watchguru.security.CurrentUserService;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import io.swagger.v3.oas.annotations.Operation;
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

    private final CurrentUserService currentUser;
    private final AppUserRepository users;
    private final ApiMapper mapper;

    public MeController(CurrentUserService currentUser, AppUserRepository users, ApiMapper mapper) {
        this.currentUser = currentUser;
        this.users = users;
        this.mapper = mapper;
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
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteAccount")
    public void deleteAccount() {
        currentUser.deleteCurrentUser();
    }
}
