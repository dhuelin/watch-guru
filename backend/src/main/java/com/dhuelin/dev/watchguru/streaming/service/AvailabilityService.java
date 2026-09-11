package com.dhuelin.dev.watchguru.streaming.service;

import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.config.CatalogProperties;
import com.dhuelin.dev.watchguru.streaming.domain.AvailabilityCheck;
import com.dhuelin.dev.watchguru.provider.MetadataProvider;
import com.dhuelin.dev.watchguru.provider.model.ProviderWatchService;
import com.dhuelin.dev.watchguru.streaming.domain.StreamingService;
import com.dhuelin.dev.watchguru.streaming.domain.TitleAvailability;
import com.dhuelin.dev.watchguru.streaming.repository.AvailabilityCheckRepository;
import com.dhuelin.dev.watchguru.streaming.repository.StreamingServiceRepository;
import com.dhuelin.dev.watchguru.streaming.repository.TitleAvailabilityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Keeps "where can I watch this" data current, and reconciles the local
 * streaming-service list against the provider's.
 */
@Service
public class AvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityService.class);

    private final TitleAvailabilityRepository availability;
    private final StreamingServiceRepository services;
    private final AvailabilityCheckRepository checks;
    private final AvailabilityRefresher refresher;
    private final MetadataProvider provider;
    private final CatalogProperties catalogProperties;

    public AvailabilityService(TitleAvailabilityRepository availability,
                               StreamingServiceRepository services,
                               AvailabilityCheckRepository checks,
                               AvailabilityRefresher refresher,
                               MetadataProvider provider,
                               CatalogProperties catalogProperties) {
        this.availability = availability;
        this.services = services;
        this.checks = checks;
        this.refresher = refresher;
        this.provider = provider;
        this.catalogProperties = catalogProperties;
    }

    /**
     * Cached offers for a title in a region, refetched once the TTL expires.
     *
     * <p>Freshness is judged per region, and from a record of when the
     * provider was last asked rather than from the offers themselves. Two
     * reasons, both of which produced a wrong answer before: the title-level
     * timestamp says when *some* region was fetched, so a user changing
     * country sees a title that looks fresh and a list that is empty; and a
     * title carried by no service in a country has no rows to date, so
     * freshness read from rows would send every visit to that screen back to
     * the provider forever.
     *
     * @return the offers, and when they were confirmed -- null when nobody has
     *         been able to ask. An empty list with a timestamp means "on no
     *         service here", which is worth saying; an empty list without one
     *         means nothing at all, which the apps must not present as the
     *         first
     */
    @Transactional(readOnly = true)
    public Offers offersFor(Title title, String region) {
        AvailabilityCheck lastCheck = checks.findByTitleIdAndRegion(title.getId(), region).orElse(null);
        Instant cutoff = Instant.now().minus(catalogProperties.availabilityTtl());

        if (lastCheck == null || lastCheck.getCheckedAt().isBefore(cutoff)) {
            try {
                refresher.refresh(title, region);
                return new Offers(
                        availability.findByTitleIdAndRegion(title.getId(), region), Instant.now());
            } catch (RuntimeException e) {
                // Stale availability is better than failing the whole request.
                log.warn("Could not refresh availability for {}: {}", title.getPrimaryTitle(), e.getMessage());
                return new Offers(
                        availability.findByTitleIdAndRegion(title.getId(), region),
                        lastCheck == null ? null : lastCheck.getCheckedAt());
            }
        }
        return new Offers(
                availability.findByTitleIdAndRegion(title.getId(), region), lastCheck.getCheckedAt());
    }

    /**
     * The offers for one region, and when the provider last confirmed them.
     *
     * @param checkedAt null when the provider has never answered for this
     *                  region. The difference matters: only a list that was
     *                  checked means a title is genuinely unavailable
     */
    public record Offers(List<TitleAvailability> offers, Instant checkedAt) {

        /** Whether this answer is one the apps may present as fact. */
        public boolean checked() {
            return checkedAt != null;
        }
    }

    /**
     * Refreshes the streaming-service table from the provider's catalogue.
     *
     * <p>This is what populates {@code tmdb_provider_id} on the seeded rows: a
     * seeded service is matched by slug the first time its provider id is seen,
     * and anything else the provider returns is inserted. The local
     * {@code supportsSync} flag is never overwritten.
     */
    @Transactional
    public int reconcileServices(String region) {
        List<ProviderWatchService> remote = provider.listWatchServices(region);
        int touched = 0;

        for (ProviderWatchService source : remote) {
            if (source.name() == null || source.name().isBlank()) {
                continue;
            }
            String slug = StreamingService.toSlug(source.name());

            StreamingService service = services.findByTmdbProviderId(source.serviceProviderId())
                    .or(() -> services.findBySlug(slug))
                    .orElseGet(() -> new StreamingService(slug, source.name()));

            service.setTmdbProviderId(source.serviceProviderId());
            service.setName(source.name());
            service.setLogoPath(source.logoPath());
            services.save(service);
            touched++;
        }
        log.info("Reconciled {} streaming services for region {}", touched, region);
        return touched;
    }
}
