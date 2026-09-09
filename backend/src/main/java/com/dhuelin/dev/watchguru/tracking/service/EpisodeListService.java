package com.dhuelin.dev.watchguru.tracking.service;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.catalog.domain.Season;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.repository.EpisodeRepository;
import com.dhuelin.dev.watchguru.catalog.repository.SeasonRepository;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.common.NotFoundException;
import com.dhuelin.dev.watchguru.tracking.repository.EpisodeWatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Season and episode listings, with the signed-in user's watched state folded
 * in.
 *
 * <p>Exists because nothing previously exposed episodes at all: the tables were
 * populated and {@code POST /watch-events/episode} took an episode id, but no
 * endpoint told a client which ids existed. The apps could mark "the next
 * episode" only because the progress endpoint happened to leak one id.
 */
@Service
public class EpisodeListService {

    private final TitleRepository titles;
    private final SeasonRepository seasons;
    private final EpisodeRepository episodes;
    private final EpisodeWatchRepository episodeWatches;

    public EpisodeListService(TitleRepository titles,
                              SeasonRepository seasons,
                              EpisodeRepository episodes,
                              EpisodeWatchRepository episodeWatches) {
        this.titles = titles;
        this.seasons = seasons;
        this.episodes = episodes;
        this.episodeWatches = episodeWatches;
    }

    /**
     * Every season of a title with its episodes and the user's watched state.
     *
     * <p>Four queries regardless of how many seasons a series has: the title,
     * its seasons, its episodes, and the user's watches. Walking seasons to
     * fetch each one's episodes would be an N+1 over a long-running show.
     */
    @Transactional(readOnly = true)
    public SeasonListing seasonsFor(Long userId, Long titleId) {
        Title title = titles.findById(titleId)
                .orElseThrow(() -> NotFoundException.of("Title", titleId));

        List<Season> allSeasons = seasons.findByTitleIdOrderBySeasonNumberAsc(titleId);
        List<Episode> allEpisodes = episodes.findByTitleIdOrderBySeasonNumberAscEpisodeNumberAsc(titleId);
        Set<Long> watchedIds = episodeWatches.findWatchedEpisodeIdSet(userId, titleId);
        Map<Long, Integer> watchCounts = watchCounts(userId, titleId);

        Map<Integer, List<Episode>> bySeason = new HashMap<>();
        for (Episode episode : allEpisodes) {
            bySeason.computeIfAbsent(episode.getSeasonNumber(), key -> new ArrayList<>()).add(episode);
        }

        List<SeasonWithEpisodes> result = new ArrayList<>(allSeasons.size());
        for (Season season : allSeasons) {
            List<Episode> seasonEpisodes = bySeason.getOrDefault(season.getSeasonNumber(), List.of());
            result.add(new SeasonWithEpisodes(season, seasonEpisodes, watchedIds, watchCounts));
        }

        // A season row present in `episode` but absent from `season` would
        // otherwise vanish. That happens when a season fetch failed partway,
        // and silently hiding episodes is worse than showing a bare season.
        for (Map.Entry<Integer, List<Episode>> entry : bySeason.entrySet()) {
            boolean known = allSeasons.stream()
                    .anyMatch(season -> season.getSeasonNumber().equals(entry.getKey()));
            if (!known) {
                result.add(new SeasonWithEpisodes(null, entry.getValue(), watchedIds, watchCounts));
            }
        }
        result.sort((a, b) -> Integer.compare(a.seasonNumber(), b.seasonNumber()));

        return new SeasonListing(title, result);
    }

    private Map<Long, Integer> watchCounts(Long userId, Long titleId) {
        Map<Long, Integer> counts = new HashMap<>();
        for (Object[] row : episodeWatches.findWatchCounts(userId, titleId)) {
            counts.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    /** A title and its seasons. */
    public record SeasonListing(Title title, List<SeasonWithEpisodes> seasons) {
    }

    /**
     * One season's episodes, already joined to the user's watched state.
     *
     * @param season null when the catalogue holds episodes for a season it has
     *               no row for
     */
    public record SeasonWithEpisodes(
            Season season,
            List<Episode> episodes,
            Set<Long> watchedEpisodeIds,
            Map<Long, Integer> watchCounts) {

        public int seasonNumber() {
            return season != null
                    ? season.getSeasonNumber()
                    : episodes.stream().findFirst().map(Episode::getSeasonNumber).orElse(0);
        }

        /** Aired episodes in this season. Specials are counted here but the
         *  caller excludes season 0 from series-level progress. */
        public int airedEpisodes() {
            return (int) episodes.stream().filter(Episode::hasAired).count();
        }

        public int watchedEpisodes() {
            return (int) episodes.stream()
                    .filter(episode -> watchedEpisodeIds.contains(episode.getId()))
                    .count();
        }

        public boolean isWatched(Episode episode) {
            return watchedEpisodeIds.contains(episode.getId());
        }

        public int watchCount(Episode episode) {
            return watchCounts.getOrDefault(episode.getId(), 0);
        }
    }

    /** Convenience for callers that only need the ids. */
    public static Collection<Long> episodeIds(List<Episode> episodes) {
        return episodes.stream().map(Episode::getId).toList();
    }
}
