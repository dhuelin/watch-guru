package com.dhuelin.dev.watchguru.tracking.domain;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.streaming.domain.StreamingService;
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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Append-only record of a single viewing.
 *
 * <p>This is the table that makes rich reporting possible — hours watched over
 * time, breakdown by genre or service, rewatch counts, viewing streaks. The
 * other tracking tables only carry current state, so they cannot answer
 * questions about the past.
 */
@Entity
@Table(name = "watch_event")
@Getter
@Setter
@NoArgsConstructor
public class WatchEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id", nullable = false)
    private Title title;

    /** Null for a movie; set for a series episode. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id")
    private Episode episode;

    @Column(name = "watched_at", nullable = false)
    private Instant watchedAt;

    /** Actual minutes viewed, defaulting to the catalog runtime when unknown. */
    @Column(name = "minutes_watched")
    private Integer minutesWatched;

    @Column(name = "completed", nullable = false)
    private boolean completed = true;

    @Column(name = "rewatch", nullable = false)
    private boolean rewatch;

    /** Where it was watched, when known. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "streaming_service_id")
    private StreamingService streamingService;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 32)
    private WatchOrigin origin = WatchOrigin.MANUAL;

    /**
     * Identifier of the source record for imported events. A partial unique
     * index on (user, origin, origin_ref) makes imports idempotent.
     */
    @Column(name = "origin_ref")
    private String originRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public WatchEvent(AppUser user, Title title, Instant watchedAt) {
        this.user = user;
        this.title = title;
        this.watchedAt = watchedAt;
    }

    public static WatchEvent forEpisode(AppUser user, Episode episode, Instant watchedAt) {
        WatchEvent event = new WatchEvent(user, episode.getTitle(), watchedAt);
        event.setEpisode(episode);
        event.setMinutesWatched(episode.getRuntimeMinutes() != null
                ? episode.getRuntimeMinutes()
                : episode.getTitle().getRuntimeMinutes());
        return event;
    }

    @PrePersist
    void onInsert() {
        this.createdAt = Instant.now();
    }
}
