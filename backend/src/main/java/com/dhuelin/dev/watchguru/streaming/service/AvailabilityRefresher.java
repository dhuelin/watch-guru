package com.dhuelin.dev.watchguru.streaming.service;

import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.provider.MetadataProvider;
import com.dhuelin.dev.watchguru.provider.model.ProviderOffer;
import com.dhuelin.dev.watchguru.streaming.domain.AvailabilityCheck;
import com.dhuelin.dev.watchguru.streaming.domain.StreamingService;
import com.dhuelin.dev.watchguru.streaming.domain.TitleAvailability;
import com.dhuelin.dev.watchguru.streaming.repository.AvailabilityCheckRepository;
import com.dhuelin.dev.watchguru.streaming.repository.StreamingServiceRepository;
import com.dhuelin.dev.watchguru.streaming.repository.TitleAvailabilityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Refetches one title's offers for one country.
 *
 * <p>A bean of its own, and in a transaction of its own, for a reason a test
 * found rather than a reviewer: {@link AvailabilityService} catches a failed
 * refresh so that a title screen still renders with whatever was cached. If
 * the refresh shared the caller's transaction, catching it would leave that
 * transaction marked rollback-only and the whole request would fail at commit
 * anyway -- the graceful degradation would be an illusion. Because a call
 * between two methods of one bean never reaches the proxy, "its own
 * transaction" also means "its own bean".
 */
@Service
public class AvailabilityRefresher {

    private final TitleRepository titles;
    private final TitleAvailabilityRepository availability;
    private final StreamingServiceRepository services;
    private final AvailabilityCheckRepository checks;
    private final MetadataProvider provider;

    public AvailabilityRefresher(TitleRepository titles,
                                 TitleAvailabilityRepository availability,
                                 StreamingServiceRepository services,
                                 AvailabilityCheckRepository checks,
                                 MetadataProvider provider) {
        this.titles = titles;
        this.availability = availability;
        this.services = services;
        this.checks = checks;
        this.provider = provider;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refresh(Title title, String region) {
        List<ProviderOffer> offers = provider.fetchOffers(title.getTitleType(), title.getTmdbId(), region);

        // Replace rather than merge: an offer disappearing from the provider
        // means the title left that service, which the local copy must reflect.
        availability.deleteByTitleIdAndRegion(title.getId(), region);

        Title managed = titles.findById(title.getId()).orElse(title);

        for (ProviderOffer offer : offers) {
            StreamingService service = resolveService(offer);
            TitleAvailability row = new TitleAvailability(managed, service, region, offer.offerType());
            row.setLink(offer.link());
            row.setFetchedAt(Instant.now());
            availability.save(row);
        }

        managed.setAvailabilityFetchedAt(Instant.now());
        titles.save(managed);

        // Recorded even when the provider returned nothing: "asked, and there
        // is nothing here" is an answer with a shelf life, and without this
        // row every visit to such a title would ask again.
        AvailabilityCheck check = checks.findByTitleIdAndRegion(managed.getId(), region)
                .orElseGet(() -> new AvailabilityCheck(managed, region, Instant.now()));
        check.setCheckedAt(Instant.now());
        checks.save(check);
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
