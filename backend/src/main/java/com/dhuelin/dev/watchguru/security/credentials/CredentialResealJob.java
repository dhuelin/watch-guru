package com.dhuelin.dev.watchguru.security.credentials;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps every stored credential under the current key and format.
 *
 * <p>At startup, because that is when a rotated key or a new format first
 * becomes available, and daily after that so a deployment that rarely
 * restarts still finishes the move. Both are cheap when there is nothing to
 * do: one pass over a table with a row per connected user.
 */
@Component
public class CredentialResealJob {

    private final EncryptedCredentialStore store;

    public CredentialResealJob(EncryptedCredentialStore store) {
        this.store = store;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void atStartup() {
        store.resealOutdated();
    }

    @Scheduled(cron = "${watch-guru.credentials.reseal-cron:0 45 4 * * *}")
    public EncryptedCredentialStore.ResealResult run() {
        return store.resealOutdated();
    }
}
