package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.ApiMapper;
import com.dhuelin.dev.watchguru.api.dto.Requests;
import com.dhuelin.dev.watchguru.api.dto.Responses;
import com.dhuelin.dev.watchguru.tracking.repository.WatchEventRepository;
import com.dhuelin.dev.watchguru.tracking.service.WatchStats;
import com.dhuelin.dev.watchguru.tracking.service.StatsService;
import com.dhuelin.dev.watchguru.tracking.service.WatchlistService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Recording what was watched, reading it back, and the aggregate stats. */
@RestController
@RequestMapping("/api/users/{userId}")
public class WatchHistoryController {

    private static final int MAX_PAGE_SIZE = 200;

    private final WatchlistService watchlist;
    private final StatsService stats;
    private final WatchEventRepository events;
    private final ApiMapper mapper;

    public WatchHistoryController(WatchlistService watchlist,
                                  StatsService stats,
                                  WatchEventRepository events,
                                  ApiMapper mapper) {
        this.watchlist = watchlist;
        this.stats = stats;
        this.events = events;
        this.mapper = mapper;
    }

    @PostMapping("/watch-events/movie")
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.WatchEventResponse logMovie(@PathVariable Long userId,
                                                 @Valid @RequestBody Requests.LogMovieWatched request) {
        return mapper.toWatchEvent(watchlist.logMovieWatched(
                userId, request.titleId(), request.watchedAt(), request.streamingServiceId()));
    }

    @PostMapping("/watch-events/episode")
    @ResponseStatus(HttpStatus.CREATED)
    public Responses.WatchEventResponse logEpisode(@PathVariable Long userId,
                                                   @Valid @RequestBody Requests.LogEpisodeWatched request) {
        return mapper.toWatchEvent(watchlist.logEpisodeWatched(
                userId, request.episodeId(), request.watchedAt(), request.streamingServiceId()));
    }

    /** Full viewing history, newest first. */
    @GetMapping("/history")
    public List<Responses.WatchEventResponse> history(@PathVariable Long userId,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "50") int size) {
        return events.findByUserIdOrderByWatchedAtDesc(
                        userId, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)))
                .getContent().stream()
                .map(mapper::toWatchEvent)
                .toList();
    }

    /**
     * Aggregate viewing statistics.
     *
     * @param months how far back the monthly time series should reach
     */
    @GetMapping("/stats")
    public WatchStats stats(@PathVariable Long userId,
                            @RequestParam(defaultValue = "12") int months) {
        return stats.forUser(userId, months);
    }
}
