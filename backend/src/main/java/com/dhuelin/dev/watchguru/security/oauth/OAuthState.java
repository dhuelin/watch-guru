package com.dhuelin.dev.watchguru.security.oauth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One authorisation in progress.
 *
 * <p>The {@code state} parameter is what ties the browser coming back from the
 * provider to the user who started the flow. Without it, anybody who can reach
 * the callback could attach their own account to somebody else's library --
 * which is not a theoretical attack, it is the reason the parameter exists.
 *
 * <p>Only the hash is stored, and the row is deleted the first time it is
 * redeemed: the value itself travels through a browser and a redirect, so it
 * is a bearer credential like any other.
 */
@Entity
@Table(name = "oauth_state")
@Getter
@Setter
@NoArgsConstructor
public class OAuthState {

    @Id
    @Column(name = "state_hash", nullable = false, length = 64)
    private String stateHash;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public OAuthState(String stateHash, Long userId, String provider, Instant now, Instant expiresAt) {
        this.stateHash = stateHash;
        this.userId = userId;
        this.provider = provider;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }
}
