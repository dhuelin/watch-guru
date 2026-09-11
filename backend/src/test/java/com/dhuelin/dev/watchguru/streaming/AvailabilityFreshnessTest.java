package com.dhuelin.dev.watchguru.streaming;

import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.provider.MetadataProvider;
import com.dhuelin.dev.watchguru.provider.model.ProviderOffer;
import com.dhuelin.dev.watchguru.streaming.service.AvailabilityService;
import com.dhuelin.dev.watchguru.support.PostgresTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How often "where can I watch this" goes back to the provider, and for which
 * country.
 *
 * <p>Both halves of this were wrong at some point in this branch. Freshness
 * lived on the title while the offers are per region, so changing country
 * showed an empty list as though nothing carried the title; and when freshness
 * was then read from the offer rows, a title carried nowhere had no rows to
 * date and went back to the provider on every single request.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestConfig.class)
class AvailabilityFreshnessTest {

    @Autowired
    private AvailabilityService availability;
    @Autowired
    private TitleRepository titles;

    @MockitoBean
    private MetadataProvider provider;

    private Title title;

    @BeforeEach
    void setUp() {
        long seed = System.nanoTime();
        title = titles.save(new Title(4_000_000L + seed % 100_000, TitleType.MOVIE, "Availability " + seed));
    }

    @Test
    @DisplayName("a confirmed empty answer is cached, not asked again on every request")
    void emptyResultsAreCacheable() {
        when(provider.fetchOffers(any(), anyLong(), eq("CH"))).thenReturn(List.of());

        AvailabilityService.Offers first = availability.offersFor(title, "CH");
        AvailabilityService.Offers second = availability.offersFor(title, "CH");

        assertThat(first.offers()).isEmpty();
        assertThat(first.checked()).isTrue();
        assertThat(second.checked()).isTrue();
        // Once, not twice: "nothing here" is an answer with a shelf life.
        verify(provider, times(1)).fetchOffers(any(), anyLong(), eq("CH"));
    }

    @Test
    @DisplayName("a region nobody has asked about is asked about, whatever another region's age")
    void freshnessIsPerRegion() {
        when(provider.fetchOffers(any(), anyLong(), any())).thenReturn(List.of());

        availability.offersFor(title, "US");
        availability.offersFor(title, "CH");

        // The US answer must not make the Swiss one look fresh.
        verify(provider).fetchOffers(any(), anyLong(), eq("US"));
        verify(provider).fetchOffers(any(), anyLong(), eq("CH"));
    }

    @Test
    @DisplayName("a provider that will not answer leaves no timestamp, so nothing is claimed")
    void unreachableProviderIsNotAnAnswer() {
        when(provider.fetchOffers(any(), anyLong(), eq("DE")))
                .thenThrow(new IllegalStateException("upstream down"));

        AvailabilityService.Offers offers = availability.offersFor(title, "DE");

        assertThat(offers.offers()).isEmpty();
        // Not "on no service in Germany" -- nobody knows.
        assertThat(offers.checked()).isFalse();
    }

    @Test
    @DisplayName("offers that were found come back with the answer's age")
    void offersCarryTheirAge() {
        when(provider.fetchOffers(any(), anyLong(), eq("GB"))).thenReturn(List.of(
                new ProviderOffer(8L, "Netflix", "/netflix.jpg",
                        com.dhuelin.dev.watchguru.streaming.domain.OfferType.FLATRATE,
                        "https://example.test/watch")));

        AvailabilityService.Offers offers = availability.offersFor(title, "GB");

        assertThat(offers.offers()).hasSize(1);
        assertThat(offers.checkedAt()).isNotNull();
    }
}
