package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.Requests;
import com.dhuelin.dev.watchguru.api.dto.Responses;
import com.dhuelin.dev.watchguru.notifications.service.NotificationSettingsService;
import com.dhuelin.dev.watchguru.security.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where a user's pushes go, and whether they want them.
 *
 * <p>Under /me because every route here is about the caller: there is no way
 * to register a device for, or read the settings of, anybody else.
 */
@RestController
@RequestMapping("/api/v1/me")
public class NotificationController {

    private final CurrentUserService currentUser;
    private final NotificationSettingsService settings;

    public NotificationController(CurrentUserService currentUser, NotificationSettingsService settings) {
        this.currentUser = currentUser;
        this.settings = settings;
    }

    /**
     * Registers this install for push, or refreshes a token already known.
     *
     * <p>Idempotent, because the app calls it on every launch: push services
     * reissue tokens without telling the app which launch was the one that
     * changed.
     *
     * <p>The device's time zone rides along, because this is the only call
     * that knows it. Without it every user's quiet hours are Greenwich's.
     */
    @PostMapping("/devices")
    @Operation(operationId = "registerDevice")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerDevice(@Valid @RequestBody Requests.RegisterDevice request) {
        settings.register(currentUser.require(), request.token(), request.platform(), request.timeZone());
    }

    /** Forgets one of the caller's devices; used on sign-out. */
    @DeleteMapping("/devices/{token}")
    @Operation(operationId = "unregisterDevice")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregisterDevice(@PathVariable String token) {
        settings.unregister(currentUser.require(), token);
    }

    @GetMapping("/notifications")
    @Operation(operationId = "getNotificationSettings")
    public Responses.NotificationSettingsResponse notifications() {
        return settings.settings(currentUser.require());
    }

    /** The global switch. Off here beats any per-series setting. */
    @PatchMapping("/notifications")
    @Operation(operationId = "updateNotificationSettings")
    public Responses.NotificationSettingsResponse updateNotifications(
            @Valid @RequestBody Requests.UpdateNotificationSettings request) {
        return settings.setEnabled(currentUser.require(), request.enabled());
    }

    /** The per-series switch, for the toggle on a title screen. */
    @PutMapping("/notifications/titles/{titleId}")
    @Operation(operationId = "updateSeriesNotification")
    public Responses.NotificationSettingsResponse updateSeriesNotification(
            @PathVariable Long titleId,
            @Valid @RequestBody Requests.UpdateSeriesNotification request) {
        return settings.setSeries(currentUser.require(), titleId, request.newEpisodes());
    }
}
