package com.dhuelin.dev.watchguru.tracking.service;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.repository.EpisodeRepository;
import com.dhuelin.dev.watchguru.tracking.domain.WatchStatus;
import com.dhuelin.dev.watchguru.tracking.domain.WatchlistItem;
import com.dhuelin.dev.watchguru.tracking.repository.EpisodeWatchRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchlistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What to watch next, across every series a user is part-way through.
 *
 * <p>This lives on the server rather than in each app for two reasons. It would
 * otherwise be one progress request per series on the screen the app opens to;
 * and the rule for what counts as "next" is real product logic — aired only,
 * specials excluded, and sensible when someone has marked S03E05 without
 * touching seasons one and two. Implemented twice, the two apps would
 * eventually disagree about which episode is next, which is the single thing
 * this product cannot get wrong.
 */
@Service
public class UpNextService {

    private final WatchlistItemRepository items;
    private final EpisodeRepository episodes;
    private final EpisodeWatchRepository episodeWatches;

    public UpNextService(WatchlistItemRepository items,
                         EpisodeRepository episodes,
                         EpisodeWatchRepository episodeWatches) {
        this.items = items;
        this.episodes = episodes;
        this.episodeWatches = episodeWatches;
    }

    /**
     * One entry per in-progress series, most recently watched first.
     *
     * <p>A series with nothing left to watch is omitted rather than returned
     * with a null episode: the screen is a list of things to play, and a row
     * that cannot be played does not belong on it.
     */
    @Transactional(readOnly = true)
    public List<UpNext> forUser(Long userId, int limit) {
        List<WatchlistItem> watching = items.findByUserIdAndStatus(userId, WatchStatus.WATCHING);
        if (watching.isEmpty()) {
            return List.of();
        }

        List<Long> titleIds = watching.stream()
                .map(item -> item.getTitle().getId())
                .toList();

        // Three queries for the whole screen, whatever the number of series.
        Map<Long, SeriesProgressCounts> progress = new HashMap<>();
        for (SeriesProgressCounts counts : episodes.progressForTitles(userId, titleIds)) {
            progress.put(counts.titleId(), counts);
        }
        Map<Long, Instant> lastWatched = new HashMap<>();
        for (Object[] row : episodeWatches.findLastWatchedAt(userId, titleIds)) {
            lastWatched.put((Long) row[0], (Instant) row[1]);
        }

        List<UpNext> result = new ArrayList<>();
        for (WatchlistItem item : watching) {
            Title title = item.getTitle();
            Episode next = nextEpisode(userId, title.getId());
            if (next == null) {
                continue;
            }
            SeriesProgressCounts counts = progress.get(title.getId());
            result.add(new UpNext(
                    title,
                    next,
                    counts == null ? 0 : (int) counts.watchedEpisodes(),
                    counts == null ? 0 : (int) counts.airedEpisodes(),
                    lastWatched.get(title.getId())));
        }

        // Most recently watched first: the series someone is actively bingeing
        // is the one they want at the top. Never-started series sort last,
        // rather than being ordered arbitrarily among themselves.
        result.sort(Comparator.comparing(
                (UpNext entry) -> entry.lastWatchedAt() == null ? Instant.EPOCH : entry.lastWatchedAt())
                .reversed());

        return result.size() > limit ? result.subList(0, limit) : result;
    }

    /**
     * The first aired, unwatched episode in broadcast order.
     *
     * <p>Broadcast order, not "one after the highest watched": someone who
     * jumped ahead to S03E05 has still not seen season one, and the next thing
     * to watch is S01E01. Specials are skipped — they are optional viewing, and
     * offering one as the next episode of a series someone is working through
     * is wrong.
     */
    private Episode nextEpisode(Long userId, Long titleId) {
        Set<Long> watched = new HashSet<>(episodeWatches.findWatchedEpisodeIdSet(userId, titleId));
        for (Episode episode : episodes.findAiredByTitleId(titleId)) {
            if (episode.getSeasonNumber() != null && episode.getSeasonNumber() == 0) {
                continue;
            }
            if (!watched.contains(episode.getId())) {
                return episode;
            }
        }
        return null;
    }

    /**
     * @param lastWatchedAt null when the series has been added but never
     *                      watched, which is why it is not part of the sort key
     *                      directly
     */
    public record UpNext(
            Title title,
            Episode nextEpisode,
            int watchedEpisodes,
            int airedEpisodes,
            Instant lastWatchedAt) {
    }
}
