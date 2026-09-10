package com.dhuelin.dev.watchguru.notifications.service;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;

import java.util.List;

/**
 * One series' worth of new episodes, and what to say about them.
 *
 * <p>Grouped per series on purpose. Three episodes of one show dropping at
 * once is one event to a person, and three pushes for it is how an app gets
 * muted; six shows returning on the same night is six events, and folding them
 * into "you have 9 new episodes" throws away the only thing that makes a
 * notification worth opening -- which show.
 *
 * @param episodes in broadcast order; never empty
 * @param stillUrl the first episode's still, already resolved to a full URL --
 *                 the raw provider path would render as a broken image
 */
public record EpisodeAnnouncement(Long titleId, String titleName, List<Episode> episodes, String stillUrl) {

    public EpisodeAnnouncement {
        if (episodes == null || episodes.isEmpty()) {
            throw new IllegalArgumentException("an announcement with no episodes is not an announcement");
        }
        episodes = List.copyOf(episodes);
    }

    /** The episode a tap should open: the first new one, not the last. */
    public Episode firstEpisode() {
        return episodes.getFirst();
    }

    public PushMessage toMessage() {
        return new PushMessage(titleName, body(), deepLink(), stillUrl);
    }

    private String body() {
        Episode first = firstEpisode();
        if (episodes.size() == 1) {
            String name = first.getName();
            return name == null || name.isBlank()
                    ? first.code() + " is out"
                    : first.code() + " · " + name;
        }
        // The count, then where to start. Somebody three episodes behind wants
        // the first one, and the notification should already know that.
        return episodes.size() + " new episodes, from " + first.code();
    }

    private String deepLink() {
        return "watchguru://titles/" + titleId + "/episodes/" + firstEpisode().getId();
    }
}
