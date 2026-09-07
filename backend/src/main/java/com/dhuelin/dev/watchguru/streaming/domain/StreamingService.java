package com.dhuelin.dev.watchguru.streaming.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A streaming service such as Netflix or Disney+.
 *
 * <p>Rows are reconciled against TMDB's watch-provider list, which is what
 * populates {@link #tmdbProviderId}. {@link #supportsSync} is set locally and
 * marks the services we have a direct account integration for.
 */
@Entity
@Table(name = "streaming_service")
@Getter
@Setter
@NoArgsConstructor
public class StreamingService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "tmdb_provider_id")
    private Long tmdbProviderId;

    @Column(name = "logo_path")
    private String logoPath;

    @Column(name = "supports_sync", nullable = false)
    private boolean supportsSync;

    public StreamingService(String slug, String name) {
        this.slug = slug;
        this.name = name;
    }

    /** Derives a stable slug from a provider name, e.g. "Disney Plus" -> "disney-plus". */
    public static String toSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
