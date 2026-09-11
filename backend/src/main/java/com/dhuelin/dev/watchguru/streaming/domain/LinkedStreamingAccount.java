package com.dhuelin.dev.watchguru.streaming.domain;

import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A user's connection to a streaming account, for importing their list and
 * viewing history.
 *
 * <p>No credential material is stored on this row. {@link #credentialRef} is a
 * pointer into a secret manager, so access tokens never reach the application
 * database or its backups. {@link #lastSyncCursor} lets an import resume from
 * where the previous run stopped rather than re-reading everything.
 */
@Entity
@Table(name = "linked_streaming_account")
@Getter
@Setter
@NoArgsConstructor
public class LinkedStreamingAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "streaming_service_id", nullable = false)
    private StreamingService streamingService;

    /** Free-text label, useful when a service has multiple profiles. */
    @Column(name = "account_label", length = 128)
    private String accountLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LinkStatus status = LinkStatus.PENDING;

    /** Opaque handle to the credentials held in the secret manager. */
    @Column(name = "credential_ref")
    private String credentialRef;

    @Column(name = "sync_enabled", nullable = false)
    private boolean syncEnabled = true;

    /**
     * SHA-256 of the token in the webhook URL this link hands out.
     *
     * <p>The hash and not the token, because a webhook URL is a bearer
     * credential: anyone holding it can post a watch into this user's history.
     * Stored like a password so a database dump does not yield a working URL,
     * and shown to the user exactly once, when it is issued.
     */
    @Column(name = "webhook_token_hash", length = 64)
    private String webhookTokenHash;

    @Column(name = "webhook_token_issued_at")
    private Instant webhookTokenIssuedAt;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "last_sync_cursor")
    private String lastSyncCursor;

    @Column(name = "last_sync_error", columnDefinition = "text")
    private String lastSyncError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public LinkedStreamingAccount(AppUser user, StreamingService streamingService) {
        this.user = user;
        this.streamingService = streamingService;
    }

    /** True when this account is in a state where an import should be attempted. */
    public boolean isSyncable() {
        return syncEnabled && (status == LinkStatus.CONNECTED || status == LinkStatus.ERROR);
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
