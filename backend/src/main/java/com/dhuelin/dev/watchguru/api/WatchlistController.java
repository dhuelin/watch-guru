package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.ApiMapper;
import com.dhuelin.dev.watchguru.tracking.service.SeriesProgressCounts;
import com.dhuelin.dev.watchguru.catalog.repository.EpisodeRepository;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.security.CurrentUserService;
import com.dhuelin.dev.watchguru.api.dto.Requests;
import com.dhuelin.dev.watchguru.api.dto.Responses;
import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;
import com.dhuelin.dev.watchguru.tracking.domain.WatchlistItem;
import com.dhuelin.dev.watchguru.tracking.service.TitleProgress;
import com.dhuelin.dev.watchguru.tracking.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The signed-in user's watchlist and their progress through it.
 *
 * <p>Every method scopes to the token's user. {@code itemId} and {@code titleId}
 * still appear in paths, and {@link WatchlistService} checks that each belongs
 * to the caller -- an id in a URL is a reference, not a claim of ownership.
 */
@RestController
@RequestMapping("/api/v1/me/watchlist")
public class WatchlistController {

    private static final int MAX_PAGE_SIZE = 100;

    private final WatchlistService watchlist;
    private final EpisodeRepository episodes;
    private final CurrentUserService currentUser;
    private final ApiMapper mapper;

    public WatchlistController(WatchlistService watchlist,
                               EpisodeRepository episodes,
                               CurrentUserService currentUser,
                               ApiMapper mapper) {
        this.watchlist = watchlist;
        this.episodes = episodes;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(operationId = "listWatchlist")
    public List<Responses.WatchlistItemResponse> list(@RequestParam(required = false) WatchStatus status,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        Long userId = currentUser.require().getId();
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "priority").and(Sort.by(Sort.Direction.DESC, "addedAt")));

        Page<WatchlistItem> items = watchlist.list(userId, status, pageable);

        // Progress for the whole page in one query. Fetching it per row would
        // be an N+1 on the screen users open most, which is exactly why it was
        // missing from this response and why the apps could not draw a
        // progress bar.
        Map<Long, Responses.SeriesProgress> progress = progressFor(userId, items.getContent());

        // Availability is still omitted: that one would mean a provider
        // round-trip per row, which no amount of batching fixes. Fetch a single
        // title to get its offers.
        return items.getContent().stream()
                .map(item -> mapper.toWatchlistItem(
                        item, List.of(), progress.get(item.getTitle().getId())))
                .toList();
    }

    /**
     * Series progress for a page of items, keyed by title id.
     *
     * <p>Films are absent from the result rather than present with zeroes: a
     * film has no episode progress, and a zero would render as an empty
     * progress bar on something that cannot have one.
     */
    private Map<Long, Responses.SeriesProgress> progressFor(Long userId, List<WatchlistItem> items) {
        List<Long> seriesIds = items.stream()
                .map(WatchlistItem::getTitle)
                .filter(title -> title.getTitleType() == TitleType.TV_SERIES)
                .map(Title::getId)
                .toList();

        if (seriesIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Responses.SeriesProgress> byTitle = new HashMap<>();
        for (SeriesProgressCounts counts : episodes.progressForTitles(userId, seriesIds)) {
            byTitle.put(counts.titleId(), mapper.toSeriesProgress(counts));
        }
        return byTitle;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "addToWatchlist")
    public Responses.WatchlistItemResponse add(@Valid @RequestBody Requests.AddToWatchlist request) {
        Long userId = currentUser.require().getId();
        WatchlistItem item = watchlist.add(
                userId, request.titleType(), request.providerId(), request.statusOrDefault());
        return mapper.toWatchlistItem(item, List.of());
    }

    @PatchMapping("/{itemId}")
    @Operation(operationId = "updateWatchlistItem")
    public Responses.WatchlistItemResponse update(@PathVariable Long itemId,
                                                  @Valid @RequestBody Requests.UpdateWatchlistItem request) {
        Long userId = currentUser.require().getId();
        // An empty body is a no-op that returns current state.
        WatchlistItem item = watchlist.get(userId, itemId);
        if (request.status() != null) {
            item = watchlist.updateStatus(userId, itemId, request.status());
        }
        if (request.rating() != null) {
            item = watchlist.rate(userId, itemId, request.rating());
        }
        if (request.notes() != null) {
            item = watchlist.setNotes(userId, itemId, request.notes());
        }
        return mapper.toWatchlistItem(item, List.of());
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "removeFromWatchlist")
    public void remove(@PathVariable Long itemId) {
        watchlist.remove(currentUser.require().getId(), itemId);
    }

    /** Aired-episode progress and the next episode to watch. */
    @GetMapping("/titles/{titleId}/progress")
    @Operation(operationId = "getTitleProgress")
    public TitleProgress progress(@PathVariable Long titleId) {
        return watchlist.progress(currentUser.require().getId(), titleId);
    }
}
