package com.dhuelin.dev.watchguru.streaming.domain;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Audit record for one import attempt against a linked streaming account. */
@Entity
@Table(name = "sync_run")
@Getter
@Setter
@NoArgsConstructor
public class SyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "linked_account_id", nullable = false)
    private LinkedStreamingAccount linkedAccount;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SyncStatus status = SyncStatus.RUNNING;

    @Column(name = "items_imported", nullable = false)
    private int itemsImported;

    @Column(name = "items_skipped", nullable = false)
    private int itemsSkipped;

    @Column(name = "items_failed", nullable = false)
    private int itemsFailed;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    public SyncRun(LinkedStreamingAccount linkedAccount) {
        this.linkedAccount = linkedAccount;
    }

    /** Closes the run, deriving the terminal status from the failure count. */
    public void finish() {
        this.finishedAt = Instant.now();
        this.status = itemsFailed == 0 ? SyncStatus.SUCCESS : SyncStatus.PARTIAL;
    }

    public void fail(String message) {
        this.finishedAt = Instant.now();
        this.status = SyncStatus.FAILED;
        this.errorMessage = message;
    }
}
