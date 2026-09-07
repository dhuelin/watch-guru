package com.dhuelin.dev.watchguru.tracking.domain;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Current per-episode progress: one row per user and episode.
 *
 * <p>A rewatch increments {@link #watchCount} instead of inserting another row,
 * which keeps "have I seen this episode" a single indexed lookup. The full
 * chronological history lives in {@link WatchEvent}.
 */
@Entity
@Table(name = "episode_watch")
@Getter
@Setter
@NoArgsConstructor
public class EpisodeWatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id", nullable = false)
    private Title title;

    @Column(name = "watched_at", nullable = false)
    private Instant watchedAt;

    @Column(name = "watch_count", nullable = false)
    private int watchCount = 1;

    @Column(name = "user_rating", precision = 4, scale = 2)
    private BigDecimal userRating;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public EpisodeWatch(AppUser user, Episode episode, Instant watchedAt) {
        this.user = user;
        this.episode = episode;
        this.title = episode.getTitle();
        this.watchedAt = watchedAt;
    }

    /** Records another viewing of an already-watched episode. */
    public void markWatchedAgain(Instant watchedAt) {
        this.watchCount++;
        this.watchedAt = watchedAt;
    }
}
