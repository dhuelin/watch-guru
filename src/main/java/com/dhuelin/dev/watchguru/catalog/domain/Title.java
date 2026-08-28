package com.dhuelin.dev.watchguru.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A movie or series mirrored from TMDB.
 *
 * <p>TMDB is the source of record for metadata, but {@code imdbId} is the
 * cross-source identifier: it is what links this row to IMDb ratings data and
 * to anything a streaming service reports about the same title.
 */
@Entity
@Table(name = "title")
@Getter
@Setter
@NoArgsConstructor
public class Title {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tmdb_id", nullable = false)
    private Long tmdbId;

    @Enumerated(EnumType.STRING)
    @Column(name = "title_type", nullable = false, length = 16)
    private TitleType titleType;

    @Column(name = "imdb_id", length = 16)
    private String imdbId;

    @Column(name = "primary_title", nullable = false, length = 512)
    private String primaryTitle;

    @Column(name = "original_title", length = 512)
    private String originalTitle;

    @Column(name = "tagline", length = 512)
    private String tagline;

    @Column(name = "overview", columnDefinition = "text")
    private String overview;

    @Column(name = "original_language", length = 16)
    private String originalLanguage;

    @Column(name = "homepage", length = 1024)
    private String homepage;

    @Column(name = "poster_path")
    private String posterPath;

    @Column(name = "backdrop_path")
    private String backdropPath;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "first_air_date")
    private LocalDate firstAirDate;

    @Column(name = "last_air_date")
    private LocalDate lastAirDate;

    @Column(name = "production_status", length = 64)
    private String productionStatus;

    @Column(name = "adult", nullable = false)
    private boolean adult;

    /** Movie runtime, or the typical episode runtime for a series. */
    @Column(name = "runtime_minutes")
    private Integer runtimeMinutes;

    @Column(name = "number_of_seasons")
    private Integer numberOfSeasons;

    @Column(name = "number_of_episodes")
    private Integer numberOfEpisodes;

    @Column(name = "tmdb_vote_average", precision = 4, scale = 2)
    private BigDecimal tmdbVoteAverage;

    @Column(name = "tmdb_vote_count")
    private Integer tmdbVoteCount;

    @Column(name = "tmdb_popularity", precision = 10, scale = 3)
    private BigDecimal tmdbPopularity;

    @Column(name = "imdb_rating", precision = 3, scale = 1)
    private BigDecimal imdbRating;

    @Column(name = "imdb_vote_count")
    private Integer imdbVoteCount;

    @Column(name = "summary_fetched_at")
    private Instant summaryFetchedAt;

    /** Null means this row came from a search result and was never fully fetched. */
    @Column(name = "detail_fetched_at")
    private Instant detailFetchedAt;

    @Column(name = "availability_fetched_at")
    private Instant availabilityFetchedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "title_genre",
            joinColumns = @JoinColumn(name = "title_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id"))
    private Set<Genre> genres = new LinkedHashSet<>();

    @OneToMany(mappedBy = "title", fetch = FetchType.LAZY)
    @OrderBy("seasonNumber ASC")
    private List<Season> seasons = new ArrayList<>();

    public Title(Long tmdbId, TitleType titleType, String primaryTitle) {
        this.tmdbId = tmdbId;
        this.titleType = titleType;
        this.primaryTitle = primaryTitle;
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

    /** The date this title first became available, whichever field applies. */
    public LocalDate primaryReleaseDate() {
        return titleType == TitleType.MOVIE ? releaseDate : firstAirDate;
    }

    /** True when full detail has never been fetched, or the cached copy has aged out. */
    public boolean needsDetailRefresh(Duration ttl) {
        return detailFetchedAt == null || detailFetchedAt.isBefore(Instant.now().minus(ttl));
    }

    public boolean needsAvailabilityRefresh(Duration ttl) {
        return availabilityFetchedAt == null || availabilityFetchedAt.isBefore(Instant.now().minus(ttl));
    }
}
