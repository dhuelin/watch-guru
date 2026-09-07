package com.dhuelin.dev.watchguru.tracking.service;

import com.dhuelin.dev.watchguru.catalog.repository.EpisodeRepository;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.catalog.service.CatalogService;
import com.dhuelin.dev.watchguru.common.NotFoundException;
import com.dhuelin.dev.watchguru.streaming.repository.StreamingServiceRepository;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;
import com.dhuelin.dev.watchguru.tracking.domain.WatchlistItem;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import com.dhuelin.dev.watchguru.tracking.repository.EpisodeWatchRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchEventRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchlistItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One user cannot reach another user's watchlist item by its id.
 *
 * <p>Removing the user id from the URL stops a caller <em>naming</em> another
 * user, but item ids are still sequential integers in the path. Ownership has
 * to be checked on the item itself, and this is that check.
 */
class WatchlistOwnershipTest {

    private static final long OWNER = 1L;
    private static final long INTRUDER = 2L;
    private static final long ITEM_ID = 100L;

    private WatchlistItemRepository items;
    private WatchlistService watchlist;

    @BeforeEach
    void setUp() {
        items = mock(WatchlistItemRepository.class);
        watchlist = new WatchlistService(
                items,
                mock(AppUserRepository.class),
                mock(TitleRepository.class),
                mock(EpisodeRepository.class),
                mock(EpisodeWatchRepository.class),
                mock(WatchEventRepository.class),
                mock(StreamingServiceRepository.class),
                mock(CatalogService.class));

        AppUser owner = new AppUser("owner@example.com", "Owner");
        owner.setId(OWNER);

        WatchlistItem item = new WatchlistItem();
        item.setId(ITEM_ID);
        item.setUser(owner);
        item.setStatus(WatchStatus.WATCHING);

        when(items.findById(ITEM_ID)).thenReturn(Optional.of(item));
    }

    @Test
    @DisplayName("reading someone else's item is a 404, not a 403")
    void readingAnotherUsersItemIsNotFound() {
        // 404 rather than 403 on purpose: 403 would confirm the item exists,
        // which turns sequential ids into a way of counting other users' data.
        assertThatThrownBy(() -> watchlist.get(INTRUDER, ITEM_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("changing the status of someone else's item is refused")
    void updatingAnotherUsersItemIsRefused() {
        assertThatThrownBy(() -> watchlist.updateStatus(INTRUDER, ITEM_ID, WatchStatus.DROPPED))
                .isInstanceOf(NotFoundException.class);
        verify(items, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("rating someone else's item is refused")
    void ratingAnotherUsersItemIsRefused() {
        assertThatThrownBy(() -> watchlist.rate(INTRUDER, ITEM_ID, new BigDecimal("9.0")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("deleting someone else's item is refused and deletes nothing")
    void deletingAnotherUsersItemIsRefused() {
        assertThatThrownBy(() -> watchlist.remove(INTRUDER, ITEM_ID))
                .isInstanceOf(NotFoundException.class);
        verify(items, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("the owner reaches their own item")
    void ownerCanReadTheirOwnItem() {
        WatchlistItem found = watchlist.get(OWNER, ITEM_ID);
        org.assertj.core.api.Assertions.assertThat(found.getId()).isEqualTo(ITEM_ID);
    }
}
