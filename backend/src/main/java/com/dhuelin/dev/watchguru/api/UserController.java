package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.ApiMapper;
import com.dhuelin.dev.watchguru.api.dto.Requests;
import com.dhuelin.dev.watchguru.api.dto.Responses;
import com.dhuelin.dev.watchguru.common.NotFoundException;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * User records.
 *
 * <p>There is no authentication yet, so the user is identified by a path
 * variable. Once OIDC is wired up this collapses to the authenticated
 * principal and {@code AppUser.authSubject}.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AppUserRepository users;
    private final ApiMapper mapper;

    public UserController(AppUserRepository users, ApiMapper mapper) {
        this.users = users;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.UserResponse create(@Valid @RequestBody Requests.CreateUser request) {
        AppUser user = new AppUser(request.email(), request.displayName());
        if (request.region() != null) {
            user.setRegion(request.region().toUpperCase());
        }
        if (request.language() != null) {
            user.setLanguage(request.language());
        }
        if (request.timeZone() != null) {
            user.setTimeZone(request.timeZone());
        }
        return mapper.toUser(users.save(user));
    }

    @GetMapping("/{userId}")
    public Responses.UserResponse get(@PathVariable Long userId) {
        return users.findById(userId)
                .map(mapper::toUser)
                .orElseThrow(() -> NotFoundException.of("User", userId));
    }
}
