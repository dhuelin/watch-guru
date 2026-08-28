package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.ApiMapper;
import com.dhuelin.dev.watchguru.api.dto.Requests;
import com.dhuelin.dev.watchguru.api.dto.Responses;
import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;
import com.dhuelin.dev.watchguru.tracking.domain.WatchlistItem;
import com.dhuelin.dev.watchguru.tracking.service.TitleProgress;
import com.dhuelin.dev.watchguru.tracking.service.WatchlistService;
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

import java.util.List;

/** A user's watchlist and their progress through it. */
@RestController
@RequestMapping("/api/users/{userId}/watchlist")
public class WatchlistController {

    private static final int MAX_PAGE_SIZE = 100;

    private final WatchlistService watchlist;
    private final ApiMapper mapper;

    public WatchlistController(WatchlistService watchlist, ApiMapper mapper) {
        this.watchlist = watchlist;
        this.mapper = mapper;
    }

    @GetMapping
    public List<Responses.WatchlistItemResponse> list(@PathVariable Long userId,
                                                      @RequestParam(required = false) WatchStatus status,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "priority").and(Sort.by(Sort.Direction.DESC, "addedAt")));

        Page<WatchlistItem> items = watchlist.list(userId, status, pageable);
        // Availability is omitted from list responses: it would mean one
        // provider round-trip per row. Fetch a single title to get its offers.
        return items.getContent().stream()
                .map(item -> mapper.toWatchlistItem(item, List.of()))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.WatchlistItemResponse add(@PathVariable Long userId,
                                               @Valid @RequestBody Requests.AddToWatchlist request) {
        WatchlistItem item = watchlist.add(
                userId, request.titleType(), request.providerId(), request.statusOrDefault());
        return mapper.toWatchlistItem(item, List.of());
    }

    @PatchMapping("/{itemId}")
    public Responses.WatchlistItemResponse update(@PathVariable Long userId,
                                                  @PathVariable Long itemId,
                                                  @Valid @RequestBody Requests.UpdateWatchlistItem request) {
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
    public void remove(@PathVariable Long userId, @PathVariable Long itemId) {
        watchlist.remove(userId, itemId);
    }

    /** Aired-episode progress and the next episode to watch. */
    @GetMapping("/titles/{titleId}/progress")
    public TitleProgress progress(@PathVariable Long userId, @PathVariable Long titleId) {
        return watchlist.progress(userId, titleId);
    }
}
