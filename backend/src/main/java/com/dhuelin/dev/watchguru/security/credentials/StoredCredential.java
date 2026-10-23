package com.dhuelin.dev.watchguru.security.credentials;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One sealed credential.
 *
 * <p>Nothing here is readable without the deployment's key: the row holds
 * ciphertext, the nonce it was sealed with, which key did it, and in which
 * format. That is the whole point -- a database dump, a backup, or a support
 * query over this table yields no access token.
 */
@Entity
@Table(name = "streaming_credential")
@Getter
@Setter
@NoArgsConstructor
public class StoredCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credential_ref", nullable = false, length = 64)
    private String credentialRef;

    @Column(name = "ciphertext", nullable = false, columnDefinition = "text")
    private String ciphertext;

    @Column(name = "nonce", nullable = false, length = 64)
    private String nonce;

    @Column(name = "key_id", nullable = false, length = 64)
    private String keyId;

    /**
     * How the row was sealed. 0 is the original format, with no associated
     * data; 1 binds the ciphertext to its {@code credential_ref}, so a sealed
     * value copied onto another row no longer opens there.
     */
    @Column(name = "format_version", nullable = false)
    private int formatVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public StoredCredential(String credentialRef) {
        this.credentialRef = credentialRef;
    }

    @PrePersist
    void onInsert() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
