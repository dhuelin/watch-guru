package com.dhuelin.dev.watchguru.streaming.service;

import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.config.CatalogProperties;
import com.dhuelin.dev.watchguru.provider.MetadataProvider;
import com.dhuelin.dev.watchguru.provider.model.ProviderOffer;
import com.dhuelin.dev.watchguru.provider.model.ProviderWatchService;
import com.dhuelin.dev.watchguru.streaming.domain.StreamingService;
import com.dhuelin.dev.watchguru.streaming.domain.TitleAvailability;
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

    private final TitleRepository titles;
    private final TitleAvailabilityRepository availability;
    private final StreamingServiceRepository services;
    private final MetadataProvider provider;
    private final CatalogProperties catalogProperties;

    public AvailabilityService(TitleRepository titles,
                               TitleAvailabilityRepository availability,
                               StreamingServiceRepository services,
                               MetadataProvider provider,
                               CatalogProperties catalogProperties) {
        this.titles = titles;
        this.availability = availability;
        this.services = services;
        this.provider = provider;
        this.catalogProperties = catalogProperties;
    }

    /** Cached offers for a title in a region, refetched once the TTL expires. */
    @Transactional
    public List<TitleAvailability> offersFor(Title title, String region) {
        if (title.needsAvailabilityRefresh(catalogProperties.availabilityTtl())) {
            try {
                refresh(title, region);
            } catch (RuntimeException e) {
                // Stale availability is better than failing the whole request.
                log.warn("Could not refresh availability for {}: {}", title.getPrimaryTitle(), e.getMessage());
            }
        }
        return availability.findByTitleIdAndRegion(title.getId(), region);
    }

    @Transactional
    public void refresh(Title title, String region) {
        List<ProviderOffer> offers = provider.fetchOffers(title.getTitleType(), title.getTmdbId(), region);

        // Replace rather than merge: an offer disappearing from the provider
        // means the title left that service, which the local copy must reflect.
        availability.deleteByTitleIdAndRegion(title.getId(), region);

        for (ProviderOffer offer : offers) {
            StreamingService service = resolveService(offer);
            TitleAvailability row = new TitleAvailability(title, service, region, offer.offerType());
            row.setLink(offer.link());
            row.setFetchedAt(Instant.now());
            availability.save(row);
        }

        title.setAvailabilityFetchedAt(Instant.now());
        titles.save(title);
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

    /** Finds or creates the local service row for an offer. */
    private StreamingService resolveService(ProviderOffer offer) {
        String slug = StreamingService.toSlug(
                offer.serviceName() == null ? String.valueOf(offer.serviceProviderId()) : offer.serviceName());

        StreamingService service = services.findByTmdbProviderId(offer.serviceProviderId())
                .or(() -> services.findBySlug(slug))
                .orElseGet(() -> new StreamingService(slug, offer.serviceName()));

        service.setTmdbProviderId(offer.serviceProviderId());
        if (offer.serviceName() != null) {
            service.setName(offer.serviceName());
        }
        if (offer.logoPath() != null) {
            service.setLogoPath(offer.logoPath());
        }
        return services.save(service);
    }
}
