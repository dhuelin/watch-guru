package com.dhuelin.dev.watchguru.security.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A sign-in nonce that has already been exchanged for a session.
 *
 * <p>Written only through {@link AuthNonceRepository#claim}, which is what
 * makes a provider token single-use; mapped as an entity so the cleanup query
 * and schema validation have something to hold on to.
 */
@Entity
@Table(name = "auth_nonce")
@Getter
@NoArgsConstructor
public class AuthNonce {

    @Id
    @Column(name = "nonce_hash", nullable = false, length = 64)
    private String nonceHash;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
