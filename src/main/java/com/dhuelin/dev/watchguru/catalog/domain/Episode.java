package com.dhuelin.dev.watchguru.catalog.domain;

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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "episode")
@Getter
@Setter
@NoArgsConstructor
public class Episode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    /** Denormalised parent series, so title-wide episode queries skip a join. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id", nullable = false)
    private Title title;

    @Column(name = "tmdb_id")
    private Long tmdbId;

    @Column(name = "season_number", nullable = false)
    private Integer seasonNumber;

    @Column(name = "episode_number", nullable = false)
    private Integer episodeNumber;

    @Column(name = "name", length = 512)
    private String name;

    @Column(name = "overview", columnDefinition = "text")
    private String overview;

    @Column(name = "air_date")
    private LocalDate airDate;

    @Column(name = "runtime_minutes")
    private Integer runtimeMinutes;

    @Column(name = "still_path")
    private String stillPath;

    @Column(name = "tmdb_vote_average", precision = 4, scale = 2)
    private BigDecimal tmdbVoteAverage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public Episode(Season season, Integer episodeNumber) {
        this.season = season;
        this.title = season.getTitle();
        this.seasonNumber = season.getSeasonNumber();
        this.episodeNumber = episodeNumber;
    }

    /** Conventional "S01E02" label. */
    public String code() {
        return String.format("S%02dE%02d", seasonNumber, episodeNumber);
    }

    /** True once the air date has passed; unaired episodes are excluded from progress totals. */
    public boolean hasAired() {
        return airDate != null && !airDate.isAfter(LocalDate.now());
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
