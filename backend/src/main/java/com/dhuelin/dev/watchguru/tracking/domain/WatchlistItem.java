package com.dhuelin.dev.watchguru.tracking.domain;

import com.dhuelin.dev.watchguru.catalog.domain.Title;
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

import java.math.BigDecimal;
import java.time.Instant;

/** One title on one user's list, with its current status and rating. */
@Entity
@Table(name = "watchlist_item")
@Getter
@Setter
@NoArgsConstructor
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id", nullable = false)
    private Title title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private WatchStatus status = WatchStatus.WATCHLIST;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "favorite", nullable = false)
    private boolean favorite;

    /** User's own score, 0-10, on the same scale as IMDb/TMDB for comparison. */
    @Column(name = "user_rating", precision = 4, scale = 2)
    private BigDecimal userRating;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 32)
    private WatchOrigin origin = WatchOrigin.MANUAL;

    /** Identifier of this item in the system it was imported from, if any. */
    @Column(name = "origin_ref")
    private String originRef;

    public WatchlistItem(AppUser user, Title title, WatchStatus status) {
        this.user = user;
        this.title = title;
        this.status = status;
    }

    /**
     * Moves the item to a new status, maintaining the started/completed
     * timestamps so the history stays consistent without the caller
     * having to remember to set them.
     */
    public void transitionTo(WatchStatus next) {
        Instant now = Instant.now();
        if (next == WatchStatus.WATCHING && startedAt == null) {
            this.startedAt = now;
        }
        if (next == WatchStatus.COMPLETED) {
            if (startedAt == null) {
                this.startedAt = now;
            }
            this.completedAt = now;
        } else {
            // Re-opening a finished title clears the completion marker.
            this.completedAt = null;
        }
        this.status = next;
    }

    @PrePersist
    void onInsert() {
        Instant now = Instant.now();
        if (this.addedAt == null) {
            this.addedAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
