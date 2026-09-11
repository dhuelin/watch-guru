package com.dhuelin.dev.watchguru.notifications;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.catalog.domain.Season;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.notifications.service.EpisodeAnnouncement;
import com.dhuelin.dev.watchguru.notifications.service.PushMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What a new-episode push actually says. */
class EpisodeAnnouncementTest {

    private final Title series = new Title(1396L, TitleType.TV_SERIES, "Breaking Bad");

    private Episode episode(int number, String name, Long id) {
        Season season = new Season(series, 5);
        Episode episode = new Episode(season, number);
        episode.setTitle(series);
        episode.setName(name);
        episode.setId(id);
        return episode;
    }

    @Test
    @DisplayName("one episode is named, not counted")
    void singleEpisode() {
        PushMessage message = new EpisodeAnnouncement(
                7L, "Breaking Bad", List.of(episode(14, "Ozymandias", 100L)), null).toMessage();

        assertThat(message.title()).isEqualTo("Breaking Bad");
        assertThat(message.body()).isEqualTo("S05E14 · Ozymandias");
    }

    @Test
    @DisplayName("an episode with no title still says something")
    void singleEpisodeWithoutName() {
        PushMessage message = new EpisodeAnnouncement(
                7L, "Breaking Bad", List.of(episode(14, null, 100L)), null).toMessage();

        assertThat(message.body()).isEqualTo("S05E14 is out");
    }

    @Test
    @DisplayName("several episodes are counted, and point at the first")
    void severalEpisodes() {
        // Somebody three episodes behind wants to start at the first one; the
        // notification should already know that rather than dropping them at
        // the newest and letting them work backwards.
        PushMessage message = new EpisodeAnnouncement(
                7L,
                "Breaking Bad",
                List.of(episode(14, "Ozymandias", 100L),
                        episode(15, "Granite State", 101L),
                        episode(16, "Felina", 102L)),
                null).toMessage();

        assertThat(message.body()).isEqualTo("3 new episodes, from S05E14");
        assertThat(message.deepLink()).isEqualTo("watchguru://titles/7/episodes/100");
    }

    @Test
    @DisplayName("the still image travels as a resolved URL, not a provider path")
    void carriesTheStill() {
        PushMessage message = new EpisodeAnnouncement(
                7L, "Breaking Bad", List.of(episode(14, "Ozymandias", 100L)),
                "https://image.tmdb.org/t/p/w300/still.jpg").toMessage();

        assertThat(message.imageUrl()).isEqualTo("https://image.tmdb.org/t/p/w300/still.jpg");
    }

    @Test
    @DisplayName("an announcement with no episodes is rejected rather than sent empty")
    void rejectsEmpty() {
        assertThatThrownBy(() -> new EpisodeAnnouncement(7L, "Breaking Bad", List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
