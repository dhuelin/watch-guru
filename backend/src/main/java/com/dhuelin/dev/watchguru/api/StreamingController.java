package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.ApiMapper;
import com.dhuelin.dev.watchguru.security.CurrentUserService;
import com.dhuelin.dev.watchguru.api.dto.Responses;
import com.dhuelin.dev.watchguru.streaming.repository.LinkedStreamingAccountRepository;
import com.dhuelin.dev.watchguru.streaming.repository.StreamingServiceRepository;
import com.dhuelin.dev.watchguru.streaming.service.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Streaming services and, for the planned direct integrations, the user's
 * linked accounts.
 *
 * <p>Linking is read-only for now: the schema and endpoints are in place, but
 * no service adapter has been implemented, so the list is whatever has been
 * created directly. See {@code LinkedStreamingAccount} for the credential
 * handling this will use.
 */
@RestController
@RequestMapping("/api/v1")
public class StreamingController {

    private final StreamingServiceRepository services;
    private final LinkedStreamingAccountRepository linkedAccounts;
    private final AvailabilityService availability;
    private final CurrentUserService currentUser;
    private final ApiMapper mapper;

    public StreamingController(StreamingServiceRepository services,
                               LinkedStreamingAccountRepository linkedAccounts,
                               AvailabilityService availability,
                               CurrentUserService currentUser,
                               ApiMapper mapper) {
        this.services = services;
        this.linkedAccounts = linkedAccounts;
        this.availability = availability;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    @GetMapping("/streaming-services")
    @Operation(operationId = "listStreamingServices")
    public List<Responses.StreamingServiceResponse> list() {
        return services.findAll().stream().map(mapper::toService).toList();
    }

    /** Pulls the provider's streaming-service catalogue into the local table. */
    @PostMapping("/streaming-services/reconcile")
    @Operation(operationId = "reconcileStreamingServices")
    public Map<String, Object> reconcile(@RequestParam(required = false) String region) {
        int count = availability.reconcileServices(region);
        return Map.of("reconciled", count);
    }

    @GetMapping("/me/streaming-accounts")
    @Operation(operationId = "listLinkedAccounts")
    public List<Responses.LinkedAccountResponse> linked() {
        return linkedAccounts.findByUserId(currentUser.require().getId()).stream()
                .map(mapper::toLinkedAccount)
                .toList();
    }
}
