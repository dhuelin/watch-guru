package com.dhuelin.dev.watchguru.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "season")
@Getter
@Setter
@NoArgsConstructor
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id", nullable = false)
    private Title title;

    @Column(name = "tmdb_id")
    private Long tmdbId;

    @Column(name = "season_number", nullable = false)
    private Integer seasonNumber;

    @Column(name = "name", length = 512)
    private String name;

    @Column(name = "overview", columnDefinition = "text")
    private String overview;

    @Column(name = "air_date")
    private LocalDate airDate;

    @Column(name = "episode_count")
    private Integer episodeCount;

    @Column(name = "poster_path")
    private String posterPath;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "season", fetch = FetchType.LAZY)
    @OrderBy("episodeNumber ASC")
    private List<Episode> episodes = new ArrayList<>();

    public Season(Title title, Integer seasonNumber) {
        this.title = title;
        this.seasonNumber = seasonNumber;
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
