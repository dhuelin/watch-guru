package com.dhuelin.dev.watchguru.imdb;

import com.dhuelin.dev.watchguru.config.ImdbProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.BufferedReader;
import java.io.StringReader;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How the importer chunks a dataset it must never hold in memory.
 *
 * <p>The real file is around 1.5 million rows. Reading it into a list first
 * would work in a test and fall over in production, so the batching is asserted
 * here directly.
 */
class ImdbImportBatchingTest {

    private ImdbRatingsUpdater updater;
    private ImdbImportRunRepository runs;

    @BeforeEach
    void setUp() {
        updater = mock(ImdbRatingsUpdater.class);
        runs = mock(ImdbImportRunRepository.class);
        when(updater.applyBatch(anyList())).thenAnswer(call -> ((List<?>) call.getArgument(0)).size());
    }

    private ImdbRatingsImporter importerWithBatchSize(int batchSize) {
        ImdbProperties properties = new ImdbProperties(
                true, "https://example.invalid/ratings.tsv.gz", "0 30 3 * * *",
                batchSize, Duration.ofMinutes(10));
        return new ImdbRatingsImporter(properties, updater, runs, new SimpleMeterRegistry());
    }

    private static BufferedReader rows(int count) {
        StringBuilder tsv = new StringBuilder("tconst\taverageRating\tnumVotes\n");
        for (int i = 1; i <= count; i++) {
            tsv.append("tt%07d\t7.5\t%d%n".formatted(i, i * 10));
        }
        return new BufferedReader(new StringReader(tsv.toString()));
    }

    @Test
    @DisplayName("rows are applied in batches of the configured size, not all at once")
    void appliesInBatches() throws Exception {
        ImdbRatingsImporter importer = importerWithBatchSize(10);
        ImdbImportRun run = new ImdbImportRun();

        importer.consume(rows(25), run);

        ArgumentCaptor<List<ImdbRating>> batches = ArgumentCaptor.forClass(List.class);
        verify(updater, times(3)).applyBatch(batches.capture());

        // Three calls: 10, 10, then the 5-row remainder. The final partial
        // batch is the one that gets forgotten.
        assertThat(batches.getAllValues()).extracting(List::size).containsExactly(10, 10, 5);
    }

    @Test
    @DisplayName("a dataset that divides evenly does not produce a trailing empty batch")
    void noEmptyTrailingBatch() throws Exception {
        ImdbRatingsImporter importer = importerWithBatchSize(10);

        importer.consume(rows(20), new ImdbImportRun());

        verify(updater, times(2)).applyBatch(anyList());
    }

    @Test
    @DisplayName("counts are recorded on the run so a failing job is visible")
    void recordsCounts() throws Exception {
        ImdbRatingsImporter importer = importerWithBatchSize(1000);
        ImdbImportRun run = new ImdbImportRun();

        importer.consume(rows(25), run);

        assertThat(run.getRowsRead()).isEqualTo(25);
        assertThat(run.getRowsParsed()).isEqualTo(25);
        assertThat(run.getRowsUpdated()).isEqualTo(25);
    }

    @Test
    @DisplayName("unparseable rows are counted as read but not sent to the database")
    void skipsUnparseableRowsWithoutFailing() throws Exception {
        ImdbRatingsImporter importer = importerWithBatchSize(1000);
        ImdbImportRun run = new ImdbImportRun();

        String tsv = """
                tconst\taverageRating\tnumVotes
                tt0000001\t7.5\t10
                tt0000002\t\\N\t20
                total nonsense
                tt0000003\t8.5\t30
                """;

        importer.consume(new BufferedReader(new StringReader(tsv)), run);

        assertThat(run.getRowsRead()).isEqualTo(4);
        assertThat(run.getRowsParsed()).isEqualTo(2);
    }

    @Test
    @DisplayName("an empty dataset touches the database not at all")
    void emptyDatasetDoesNothing() throws Exception {
        ImdbRatingsImporter importer = importerWithBatchSize(10);
        ImdbImportRun run = new ImdbImportRun();

        importer.consume(new BufferedReader(new StringReader("tconst\taverageRating\tnumVotes\n")), run);

        verify(updater, never()).applyBatch(anyList());
        assertThat(run.getRowsRead()).isZero();
    }

    @Test
    @DisplayName("the header is not counted as data")
    void headerIsNotData() throws Exception {
        ImdbRatingsImporter importer = importerWithBatchSize(10);
        ImdbImportRun run = new ImdbImportRun();

        importer.consume(rows(3), run);

        assertThat(run.getRowsRead()).isEqualTo(3);
    }
}
