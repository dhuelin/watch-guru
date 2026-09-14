package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.ApiMapper;
import com.dhuelin.dev.watchguru.api.dto.Responses;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.support.PostgresTestConfig;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;
import com.dhuelin.dev.watchguru.tracking.domain.WatchlistItem;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchlistItemRepository;
import com.dhuelin.dev.watchguru.streaming.service.AvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a title says about the person looking at it.
 *
 * <p>The detail screen has to know at once whether this is something the user
 * already tracks: fetched separately it would render as if nothing were
 * tracked and correct itself a moment later, which is how somebody ends up
 * adding the same title twice.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestConfig.class)
class TitleLibraryEntryTest {

    @Autowired
    private ApiMapper mapper;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private TitleRepository titles;
    @Autowired
    private WatchlistItemRepository items;

    private AppUser user;
    private Title film;

    @BeforeEach
    void setUp() {
        long seed = System.nanoTime();
        user = users.save(new AppUser("entry-" + seed + "@example.com", "Entry"));
        film = titles.save(new Title(9_400_000L + seed % 100_000, TitleType.MOVIE, "Arrival " + seed));
    }

    private Responses.TitleResponse renderedFor(WatchlistItem item) {
        return mapper.toTitle(film, new AvailabilityService.Offers(List.of(), null), item);
    }

    @Test
    @DisplayName("a title nobody has added carries no entry")
    void untrackedTitleHasNoEntry() {
        // Null is a real answer rather than a missing one: it is what the
        // screen renders its "add" action from.
        assertThat(renderedFor(null).library()).isNull();
    }

    @Test
    @DisplayName("a tracked title carries the id, status and rating")
    void trackedTitleCarriesTheEntry() {
        WatchlistItem item = new WatchlistItem(user, film, WatchStatus.COMPLETED);
        item.setUserRating(new BigDecimal("8"));
        item = items.save(item);

        Responses.LibraryEntry entry = renderedFor(item).library();

        assertThat(entry).isNotNull();
        assertThat(entry.itemId()).isEqualTo(item.getId());
        assertThat(entry.status()).isEqualTo(WatchStatus.COMPLETED);
        assertThat(entry.rating()).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("an unrated title is tracked without a rating rather than rated zero")
    void trackedButUnratedIsNotZero() {
        // Zero is a rating somebody could mean. "I have not rated this" is not
        // the same claim, and rendering it as 0/10 says something the user
        // never said.
        WatchlistItem item = items.save(new WatchlistItem(user, film, WatchStatus.WATCHLIST));

        Responses.LibraryEntry entry = renderedFor(item).library();

        assertThat(entry.rating()).isNull();
        assertThat(entry.status()).isEqualTo(WatchStatus.WATCHLIST);
    }
}
