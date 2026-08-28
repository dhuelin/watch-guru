package com.dhuelin.dev.watchguru.streaming.domain;

import com.dhuelin.dev.watchguru.catalog.domain.Title;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Where a title can be watched, per region and offer type. */
@Entity
@Table(name = "title_availability")
@Getter
@Setter
@NoArgsConstructor
public class TitleAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id", nullable = false)
    private Title title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "streaming_service_id", nullable = false)
    private StreamingService streamingService;

    @Column(name = "region", nullable = false, length = 2)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(name = "offer_type", nullable = false, length = 16)
    private OfferType offerType;

    @Column(name = "link", length = 1024)
    private String link;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt = Instant.now();

    public TitleAvailability(Title title, StreamingService service, String region, OfferType offerType) {
        this.title = title;
        this.streamingService = service;
        this.region = region;
        this.offerType = offerType;
    }
}
