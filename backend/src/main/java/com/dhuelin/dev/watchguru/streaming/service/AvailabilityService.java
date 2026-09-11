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

    /**
     * Cached offers for a title in a region, refetched once the TTL expires.
     *
     * <p>Freshness is judged per region, from the rows themselves, rather than
     * from the one timestamp on the title. The title-level stamp says when
     * some region was last fetched, which is the wrong question the moment a
     * user changes country: their region has never been fetched, the title
     * looks fresh, and they are shown an empty list as though nothing carried
     * it.
     *
     * @return the offers, and whether they are something we can vouch for --
     *         an empty list from a successful fetch means "on no service
     *         here", and an empty list from an upstream that would not answer
     *         means nothing at all, which the apps must not present as the
     *         first
     */
    @Transactional
    public Offers offersFor(Title title, String region) {
        List<TitleAvailability> cached = availability.findByTitleIdAndRegion(title.getId(), region);

        if (!isFresh(cached)) {
            try {
                refresh(title, region);
                return new Offers(availability.findByTitleIdAndRegion(title.getId(), region), true);
            } catch (RuntimeException e) {
                // Stale availability is better than failing the whole request.
                log.warn("Could not refresh availability for {}: {}", title.getPrimaryTitle(), e.getMessage());
                return new Offers(cached, !cached.isEmpty());
            }
        }
        return new Offers(cached, true);
    }

    /**
     * The offers for one region, and whether they are current.
     *
     * @param checked false when the provider could not be reached and there
     *                was nothing cached to fall back on. The difference
     *                matters: only a checked empty list means a title is
     *                genuinely unavailable
     */
    public record Offers(List<TitleAvailability> offers, boolean checked) {
    }

    /**
     * Whether these rows are current enough to use.
     *
     * <p>No rows is not freshness. It is either a region nobody has asked
     * about yet or a title carried nowhere in it, and the two are told apart
     * by asking the provider -- once per TTL, not once per request, because
     * the refresh writes a fetched-at even when it finds nothing.
     */
    private boolean isFresh(List<TitleAvailability> cached) {
        if (cached.isEmpty()) {
            return false;
        }
        Instant cutoff = Instant.now().minus(catalogProperties.availabilityTtl());
        return cached.stream()
                .map(TitleAvailability::getFetchedAt)
                .allMatch(fetchedAt -> fetchedAt != null && fetchedAt.isAfter(cutoff));
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
