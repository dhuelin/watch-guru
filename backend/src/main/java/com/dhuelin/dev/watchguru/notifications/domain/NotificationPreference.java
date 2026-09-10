package com.dhuelin.dev.watchguru.notifications.domain;

import com.dhuelin.dev.watchguru.catalog.domain.Title;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A per-series answer that differs from the default.
 *
 * <p>Only exceptions are stored. Following a series is enough to be told about
 * its new episodes, so the common case writes nothing; a row here means the
 * user has said something explicit about this one title.
 */
@Entity
@Table(name = "notification_preference")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id", nullable = false)
    private Title title;

    @Column(name = "new_episodes", nullable = false)
    private boolean newEpisodes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public NotificationPreference(AppUser user, Title title, boolean newEpisodes) {
        this.user = user;
        this.title = title;
        this.newEpisodes = newEpisodes;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
