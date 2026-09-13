package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.ApiMapper;
import com.dhuelin.dev.watchguru.security.CurrentUserService;
import com.dhuelin.dev.watchguru.api.dto.Requests;
import com.dhuelin.dev.watchguru.api.dto.Responses;
import com.dhuelin.dev.watchguru.tracking.service.WatchStats;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.service.StatsPeriod;
import com.dhuelin.dev.watchguru.tracking.service.WatchHistoryService;
import com.dhuelin.dev.watchguru.tracking.service.StatsService;
import com.dhuelin.dev.watchguru.tracking.service.UpNextService;
import com.dhuelin.dev.watchguru.tracking.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Recording what was watched, reading it back, and the aggregate stats. */
@RestController
@RequestMapping("/api/v1/me")
public class WatchHistoryController {

    private static final int MAX_PAGE_SIZE = 200;

    private final WatchlistService watchlist;
    private final StatsService stats;
    private final UpNextService upNextService;
    private final WatchHistoryService history;
    private final CurrentUserService currentUser;
    private final ApiMapper mapper;

    public WatchHistoryController(WatchlistService watchlist,
                                  StatsService stats,
                                  UpNextService upNextService,
                                  WatchHistoryService history,
                                  CurrentUserService currentUser,
                                  ApiMapper mapper) {
        this.watchlist = watchlist;
        this.stats = stats;
        this.upNextService = upNextService;
        this.history = history;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    @PostMapping("/watch-events/movie")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "logMovieWatched")
    public Responses.WatchEventResponse logMovie(@Valid @RequestBody Requests.LogMovieWatched request) {
        return mapper.toWatchEvent(watchlist.logMovieWatched(
                currentUser.require().getId(),
                request.titleId(), request.watchedAt(), request.streamingServiceId()));
    }

    @PostMapping("/watch-events/episode")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "logEpisodeWatched")
    public Responses.WatchEventResponse logEpisode(@Valid @RequestBody Requests.LogEpisodeWatched request) {
        return mapper.toWatchEvent(watchlist.logEpisodeWatched(
                currentUser.require().getId(),
                request.episodeId(), request.watchedAt(), request.streamingServiceId()));
    }

    /**
     * Marks every aired episode up to and including the given one.
     *
     * <p>One request, not one per episode: someone catching up on a series they
     * finished years ago should not make forty calls, and doing it here keeps
     * the operation idempotent -- episodes already watched are left alone
     * rather than turned into rewatches.
     */
    @PostMapping("/watch-events/episodes/up-to")
    @Operation(operationId = "markWatchedUpTo")
    public Responses.BulkMarkResponse markUpTo(@Valid @RequestBody Requests.MarkWatchedUpTo request) {
        return mapper.toBulkMark(watchlist.markWatchedUpTo(
                currentUser.require().getId(), request.episodeId(), request.watchedAt()));
    }

    /**
     * The next unwatched episode of every series in progress.
     *
     * <p>What the Home screen opens to. Server-side because the alternative is
     * one progress call per series, and because "what counts as next" is
     * product logic that must not be implemented twice.
     */
    @GetMapping("/up-next")
    @Operation(operationId = "getUpNext")
    public List<Responses.UpNextResponse> upNext(@RequestParam(defaultValue = "20") int limit) {
        return upNextService.forUser(currentUser.require().getId(), Math.clamp(limit, 1, 50))
                .stream()
                .map(mapper::toUpNext)
                .toList();
    }

    /**
     * Removes an episode from the watched history entirely.
     *
     * <p>Idempotent: unmarking something already unwatched returns 204 rather
     * than an error, so a client retrying after a dropped response does not see
     * a failure for work already done.
     */
    @DeleteMapping("/watch-events/episodes/{episodeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "unmarkEpisode")
    public void unmarkEpisode(@PathVariable Long episodeId) {
        watchlist.unmarkEpisode(currentUser.require().getId(), episodeId);
    }

    /**
     * Deletes one history entry.
     *
     * <p>Not the same as unmarking: removing one of three rewatches leaves the
     * episode watched with a lower count. Only losing the last event for an
     * episode makes it unwatched.
     */
    @DeleteMapping("/watch-events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteWatchEvent")
    public void deleteWatchEvent(@PathVariable Long eventId) {
        watchlist.deleteWatchEvent(currentUser.require().getId(), eventId);
    }

    /**
     * The viewing history, newest first, filtered by anything the timeline can
     * filter on (#22).
     *
     * <p>Dates rather than instants, because a person filtering their history
     * thinks in days, and the day they mean is the day where they are. Both
     * bounds are inclusive: {@code to=2026-09-13} includes that whole evening,
     * which the service turns into an exclusive instant at the start of the
     * next day. Inclusive-looking bounds that silently drop the last day are
     * the kind of bug nobody reports, because it looks like having watched
     * nothing.
     *
     * @param type      {@code MOVIE} or {@code TV_SERIES}; omitted for both
     * @param serviceId one streaming service; omitted for all
     * @param query     a substring of the title or the episode name
     */
    @GetMapping("/history")
    @Operation(operationId = "getHistory")
    public List<Responses.WatchEventResponse> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) TitleType type,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) String query) {

        AppUser user = currentUser.require();
        ZoneId zone = user.zone();
        WatchHistoryService.Filter filter = new WatchHistoryService.Filter(
                WatchHistoryService.startOf(from, zone),
                WatchHistoryService.endOf(to, zone),
                type,
                serviceId,
                query);

        return history.list(user.getId(), filter,
                        PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)))
                .getContent().stream()
                .map(mapper::toWatchEvent)
                .toList();
    }

    /**
     * Corrects one entry: when it was watched, or where.
     *
     * <p>Editing rather than delete-and-relog, because the two are not the
     * same: relogging loses the event's identity, and with it whatever an
     * import or a media server recorded against it -- which is what stops the
     * same viewing arriving twice.
     */
    @PatchMapping("/watch-events/{eventId}")
    @Operation(operationId = "updateWatchEvent")
    public Responses.WatchEventResponse updateWatchEvent(
            @PathVariable Long eventId,
            @Valid @RequestBody Requests.UpdateWatchEvent request) {

        return mapper.toWatchEvent(history.update(currentUser.require().getId(), eventId,
                request.watchedAt(), request.streamingServiceId()));
    }

    /**
     * Aggregate viewing statistics.
     *
     * @param months how far back the monthly time series should reach
     */
    @GetMapping("/stats")
    @Operation(operationId = "getStats")
    public WatchStats stats(@RequestParam(defaultValue = "12") int months,
                            @RequestParam(defaultValue = "ALL_TIME") StatsPeriod period) {
        return stats.forUser(currentUser.require().getId(), months, period);
    }
}
