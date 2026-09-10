package com.dhuelin.dev.watchguru.imports;

import com.dhuelin.dev.watchguru.catalog.domain.Episode;
import com.dhuelin.dev.watchguru.catalog.domain.Season;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.EpisodeRepository;
import com.dhuelin.dev.watchguru.catalog.repository.SeasonRepository;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.imports.domain.MatchStatus;
import com.dhuelin.dev.watchguru.imports.domain.MatchedRow;
import com.dhuelin.dev.watchguru.imports.service.ImportService;
import com.dhuelin.dev.watchguru.support.PostgresTestConfig;
import com.dhuelin.dev.watchguru.tracking.domain.AppUser;
import com.dhuelin.dev.watchguru.tracking.repository.AppUserRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchEventRepository;
import com.dhuelin.dev.watchguru.tracking.repository.WatchlistItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Importing somebody's history, end to end.
 *
 * <p>The acceptance criteria on #21 are mostly about restraint: nothing is
 * written until the user has seen it, a second run of the same file duplicates
 * nothing, and a row that could not be matched is reported rather than
 * silently dropped. Each of those is a test here.
 *
 * <p>The metadata provider is not configured in tests, so matching falls to
 * the local catalogue -- which is the interesting path anyway, and the one a
 * returning user is on.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestConfig.class)
class ImportIntegrationTest {

    @Autowired
    private ImportService imports;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private TitleRepository titles;
    @Autowired
    private SeasonRepository seasons;
    @Autowired
    private EpisodeRepository episodes;
    @Autowired
    private WatchEventRepository watchEvents;
    @Autowired
    private WatchlistItemRepository items;

    private AppUser user;
    private Title film;
    private Title series;
    private Episode ozymandias;

    /**
     * Names are unique per test.
     *
     * <p>Not decoration: the suite can run against a database that already
     * holds rows from earlier runs, and a second title called "Fargo" makes
     * every match in this class ambiguous -- which is correct behaviour, and
     * would look like a matching bug.
     */
    private String filmName;
    private String seriesName;

    @BeforeEach
    void setUp() {
        long seed = System.nanoTime();
        user = users.save(new AppUser("importer-" + seed + "@example.com", "Importer"));
        filmName = "Fargo " + seed;
        seriesName = "Breaking Bad " + seed;

        film = new Title(5_000_000L + seed % 100_000, TitleType.MOVIE, filmName);
        // Unique across runs: the suite may share a database, and imdb_id is unique.
        film.setImdbId("tt" + seed);
        film.setReleaseDate(LocalDate.of(1996, 4, 5));
        film.setRuntimeMinutes(98);
        film = titles.save(film);

        series = new Title(5_100_000L + seed % 100_000, TitleType.TV_SERIES, seriesName);
        series = titles.save(series);
        Season season = seasons.save(new Season(series, 5));
        Episode episode = new Episode(season, 14);
        episode.setName("Ozymandias");
        episode.setAirDate(LocalDate.of(2013, 9, 15));
        ozymandias = episodes.save(episode);
    }

    /** This user's watch events; a shared database makes a global count meaningless. */
    private long eventCount() {
        return watchEvents.findByUserIdOrderByWatchedAtDesc(
                user.getId(), org.springframework.data.domain.PageRequest.of(0, 50)).getTotalElements();
    }

    private List<MatchedRow> accepted(ImportService.Preview preview) {
        return preview.rows().stream().filter(r -> r.status() == MatchStatus.MATCHED).toList();
    }

    @Test
    @DisplayName("a preview writes nothing at all")
    void previewIsReadOnly() {
        String csv = "title,year,type,watched_at\n" + filmName + ",1996,movie,2024-01-15\n";

        ImportService.Preview preview = imports.preview(user, csv, 0);

        assertThat(preview.rows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.status()).isEqualTo(MatchStatus.MATCHED);
                    assertThat(row.titleName()).isEqualTo(filmName);
                });
        assertThat(eventCount()).isZero();
        assertThat(items.findByUserIdAndTitleId(user.getId(), film.getId())).isEmpty();
    }

    @Test
    @DisplayName("committing writes the watch, the library entry and the date from the file")
    void commitWritesTheHistory() {
        String csv = "title,year,type,watched_at\n" + filmName + ",1996,movie,2024-01-15\n";
        ImportService.Preview preview = imports.preview(user, csv, 0);

        ImportService.Result result = imports.commit(user, accepted(preview));

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(items.findByUserIdAndTitleId(user.getId(), film.getId())).isPresent();

        var events = watchEvents.findByUserIdOrderByWatchedAtDesc(
                user.getId(), org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(events.getContent()).singleElement().satisfies(event -> {
            assertThat(event.getTitle().getId()).isEqualTo(film.getId());
            // Midday, not midnight: a date imported as midnight UTC lands on
            // the previous evening for everybody west of Greenwich.
            assertThat(event.getWatchedAt().toString()).startsWith("2024-01-15T12:00");
        });
    }

    @Test
    @DisplayName("running the same import twice duplicates nothing")
    void reimportIsIdempotent() {
        String csv = "title,year,type,watched_at\n" + filmName + ",1996,movie,2024-01-15\n";

        imports.commit(user, accepted(imports.preview(user, csv, 0)));
        ImportService.Result second = imports.commit(user, accepted(imports.preview(user, csv, 0)));

        assertThat(second.imported()).isZero();
        assertThat(second.skipped()).isEqualTo(1);
        assertThat(eventCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a second run says how many rows it already has, before writing anything")
    void previewCountsWhatIsAlreadyImported() {
        String csv = "title,year,type,watched_at\n" + filmName + ",1996,movie,2024-01-15\n";
        imports.commit(user, accepted(imports.preview(user, csv, 0)));

        assertThat(imports.preview(user, csv, 0).alreadyImported()).isEqualTo(1);
    }

    @Test
    @DisplayName("a Netflix episode is matched by name, and recorded against the episode")
    void netflixEpisodeByName() {
        String csv = "Title,Date\n\"" + seriesName + ": Season 5: Ozymandias\",9/16/13\n";

        ImportService.Preview preview = imports.preview(user, csv, 0);

        assertThat(preview.rows()).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo(MatchStatus.MATCHED);
            assertThat(row.episodeId()).isEqualTo(ozymandias.getId());
            assertThat(row.episodeCode()).isEqualTo("S05E14");
        });

        imports.commit(user, accepted(preview));

        var events = watchEvents.findByUserIdOrderByWatchedAtDesc(
                user.getId(), org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(events.getContent()).singleElement()
                .satisfies(event -> assertThat(event.getEpisode().getId()).isEqualTo(ozymandias.getId()));
    }

    @Test
    @DisplayName("a title nobody has heard of is reported, not dropped")
    void unmatchedIsReported() {
        String csv = "title,year,type,watched_at\nA Film That Does Not Exist,1999,movie,2024-01-15\n";

        ImportService.Preview preview = imports.preview(user, csv, 0);

        assertThat(preview.rows()).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo(MatchStatus.UNMATCHED);
            assertThat(row.note()).contains("A Film That Does Not Exist");
        });
    }

    @Test
    @DisplayName("an episode the catalogue does not hold is reported against its series")
    void unmatchedEpisodeNamesTheSeries() {
        String csv = "Title,Date\n\"" + seriesName + ": Season 5: An Episode That Never Aired\",9/16/13\n";

        assertThat(imports.preview(user, csv, 0).rows()).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo(MatchStatus.UNMATCHED);
            assertThat(row.note()).contains(seriesName);
        });
    }

    @Test
    @DisplayName("an IMDb export warns that its dates are rating dates")
    void imdbWarning() {
        String csv = """
                Const,Title,Title Type,Year,Your Rating,Date Rated
                tt0116282,%s,Movie,1996,9,2021-03-04
                """.formatted(filmName);

        assertThat(imports.preview(user, csv, 0).warnings())
                .anySatisfy(warning -> assertThat(warning).contains("rated"));
    }

    @Test
    @DisplayName("an unreadable file imports nothing and says why")
    void unknownFormat() {
        ImportService.Preview preview = imports.preview(user, "Foo,Bar\n1,2\n", 0);

        assertThat(preview.rows()).isEmpty();
        assertThat(preview.problems()).isNotEmpty();
        assertThat(eventCount()).isZero();
    }

    @Test
    @DisplayName("only the rows the user sent are written, whatever the preview matched")
    void commitWritesOnlyWhatWasAccepted() {
        String csv = """
                title,year,type,watched_at
                %s,1996,movie,2024-01-15
                %s,2008,series,2024-01-16
                """.formatted(filmName, seriesName);
        ImportService.Preview preview = imports.preview(user, csv, 0);
        assertThat(accepted(preview)).hasSize(2);

        // The user unticked the series.
        List<MatchedRow> chosen = accepted(preview).stream()
                .filter(row -> row.titleId().equals(film.getId()))
                .toList();

        ImportService.Result result = imports.commit(user, chosen);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(items.findByUserIdAndTitleId(user.getId(), series.getId())).isEmpty();
    }
}
