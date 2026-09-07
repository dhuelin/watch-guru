package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.ApiMapper;
import com.dhuelin.dev.watchguru.api.dto.Responses;
import com.dhuelin.dev.watchguru.streaming.repository.LinkedStreamingAccountRepository;
import com.dhuelin.dev.watchguru.streaming.repository.StreamingServiceRepository;
import com.dhuelin.dev.watchguru.streaming.service.AvailabilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
@RequestMapping("/api")
public class StreamingController {

    private final StreamingServiceRepository services;
    private final LinkedStreamingAccountRepository linkedAccounts;
    private final AvailabilityService availability;
    private final ApiMapper mapper;

    public StreamingController(StreamingServiceRepository services,
                               LinkedStreamingAccountRepository linkedAccounts,
                               AvailabilityService availability,
                               ApiMapper mapper) {
        this.services = services;
        this.linkedAccounts = linkedAccounts;
        this.availability = availability;
        this.mapper = mapper;
    }

    @GetMapping("/streaming-services")
    public List<Responses.StreamingServiceResponse> list() {
        return services.findAll().stream().map(mapper::toService).toList();
    }

    /** Pulls the provider's streaming-service catalogue into the local table. */
    @PostMapping("/streaming-services/reconcile")
    public Map<String, Object> reconcile(@RequestParam(required = false) String region) {
        int count = availability.reconcileServices(region);
        return Map.of("reconciled", count);
    }

    @GetMapping("/users/{userId}/streaming-accounts")
    public List<Responses.LinkedAccountResponse> linked(@PathVariable Long userId) {
        return linkedAccounts.findByUserId(userId).stream().map(mapper::toLinkedAccount).toList();
    }
}
