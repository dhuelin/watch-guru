package com.dhuelin.dev.watchguru.notifications.domain;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A notification that has already gone out.
 *
 * <p>The unique constraint on (user, episode) is what makes "exactly one
 * notification per new episode" true. It is not the scheduler being careful:
 * a job that runs twice, or two instances running at once, still cannot
 * produce a second push, because the second insert fails.
 */
@Entity
@Table(name = "notification_delivery")
@Getter
@Setter
@NoArgsConstructor
public class NotificationDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public NotificationDelivery(AppUser user, Episode episode) {
        this.user = user;
        this.episode = episode;
    }

    /** Only a fallback: the caller sets this from the application's clock. */
    @PrePersist
    void onInsert() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
