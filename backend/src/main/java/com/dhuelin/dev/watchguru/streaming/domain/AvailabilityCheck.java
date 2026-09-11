package com.dhuelin.dev.watchguru.streaming.domain;

import com.dhuelin.dev.watchguru.catalog.domain.Title;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * When this title's availability was last asked about in this country.
 *
 * <p>Separate from the offers themselves because the useful answer is often
 * that there are none: a title carried by no service in a country produces no
 * rows, and freshness read from rows would then say "never asked" forever and
 * send every visit to that screen back to the provider.
 */
@Entity
@Table(name = "availability_check")
@Getter
@Setter
@NoArgsConstructor
public class AvailabilityCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id", nullable = false)
    private Title title;

    @Column(name = "region", nullable = false, length = 2)
    private String region;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    public AvailabilityCheck(Title title, String region, Instant checkedAt) {
        this.title = title;
        this.region = region;
        this.checkedAt = checkedAt;
    }
}
