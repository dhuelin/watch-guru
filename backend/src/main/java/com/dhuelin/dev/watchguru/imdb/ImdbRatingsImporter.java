package com.dhuelin.dev.watchguru.imdb;

import com.dhuelin.dev.watchguru.config.ImdbProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Fills {@code title.imdb_rating} and {@code title.imdb_vote_count} from IMDb's
 * published bulk datasets.
 *
 * <p>Disabled by default. Enabling it is a licensing decision, not a
 * configuration one: the datasets are licensed for personal and non-commercial
 * use, which is fine for a free app and a blocker for a paid or ad-supported
 * one. See {@link ImdbProperties}.
 *
 * <p>The file is streamed and applied in batches. It is around 25MB compressed
 * and 1.5 million rows, so it is never held in memory, and the catalog it
 * enriches is far smaller than the dataset -- rows for titles nobody here has
 * heard of are dropped as they go past.
 */
@Service
@ConditionalOnProperty(name = "watch-guru.imdb.enabled", havingValue = "true")
public class ImdbRatingsImporter {

    private static final Logger log = LoggerFactory.getLogger(ImdbRatingsImporter.class);

    private final ImdbProperties properties;
    private final ImdbRatingsUpdater updater;
    private final ImdbImportRunRepository runs;
    private final MeterRegistry meters;

    public ImdbRatingsImporter(ImdbProperties properties,
                               ImdbRatingsUpdater updater,
                               ImdbImportRunRepository runs,
                               MeterRegistry meters) {
        this.properties = properties;
        this.updater = updater;
        this.runs = runs;
        this.meters = meters;
    }

    @Scheduled(cron = "${watch-guru.imdb.cron}")
    public void scheduledImport() {
        try {
            runImport();
        } catch (RuntimeException e) {
            // A scheduled method that throws kills nothing but its own
            // execution, and the exception would otherwise vanish into the
            // scheduler's log at a level nobody reads.
            log.error("IMDb ratings import failed", e);
        }
    }

    /**
     * Runs one import.
     *
     * @return the recorded run, whose status says what actually happened
     */
    public ImdbImportRun runImport() {
        ImdbImportRun run = new ImdbImportRun();
        run.setStartedAt(Instant.now());
        run = runs.save(run);

        String previousLastModified = runs
                .findFirstByStatusOrderByStartedAtDesc(ImdbImportRun.Status.SUCCESS)
                .map(ImdbImportRun::getSourceLastModified)
                .orElse(null);

        try {
            HttpURLConnection connection = open(previousLastModified);
            int status = connection.getResponseCode();

            if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                // IMDb republishes daily but the content is not always new.
                // Re-downloading 25MB to write the same numbers is pure waste.
                log.info("IMDb dataset unchanged since {}; skipping", previousLastModified);
                run.setStatus(ImdbImportRun.Status.SKIPPED_NOT_MODIFIED);
                run.setSourceLastModified(previousLastModified);
                return finish(run);
            }
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("IMDb dataset request returned HTTP " + status);
            }

            run.setSourceLastModified(connection.getHeaderField("Last-Modified"));
            stream(connection, run);

            run.setStatus(ImdbImportRun.Status.SUCCESS);
            log.info("IMDb ratings import complete: {} rows read, {} catalog rows updated",
                    run.getRowsRead(), run.getRowsUpdated());
            meters.counter("watchguru.imdb.import", "outcome", "success").increment();
        } catch (IOException | RuntimeException e) {
            run.setStatus(ImdbImportRun.Status.FAILED);
            run.setErrorMessage(truncate(e.getMessage()));
            meters.counter("watchguru.imdb.import", "outcome", "failure").increment();
            log.error("IMDb ratings import failed after {} rows", run.getRowsRead(), e);
        }
        return finish(run);
    }

    private HttpURLConnection open(String ifModifiedSince) throws IOException {
        URL url = URI.create(properties.ratingsUrl()).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout((int) properties.readTimeout().toMillis());
        // Deliberately no Accept-Encoding: the body is a .gz file, decoded
        // explicitly below. Requesting a gzip transfer encoding as well would
        // risk a double decode.
        if (ifModifiedSince != null && !ifModifiedSince.isBlank()) {
            connection.setRequestProperty("If-Modified-Since", ifModifiedSince);
        }
        return connection;
    }

    /** Streams the gzipped TSV, applying it in batches. */
    private void stream(HttpURLConnection connection, ImdbImportRun run) throws IOException {
        try (InputStream raw = connection.getInputStream();
             GZIPInputStream gzip = new GZIPInputStream(raw);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(gzip, StandardCharsets.UTF_8), 1 << 16)) {
            consume(reader, run);
        }
    }

    /**
     * Reads the TSV and applies it batch by batch, recording the counts on the
     * run.
     *
     * <p>Separated from the transport so the batching can be tested against a
     * fixture rather than a 25MB download.
     */
    void consume(BufferedReader reader, ImdbImportRun run) throws IOException {
        List<ImdbRating> batch = new ArrayList<>(properties.batchSize());
        long read = 0;
        long parsed = 0;
        long updated = 0;

        {
            String line = reader.readLine();
            if (line != null && !ImdbRatingsParser.isHeader(line)) {
                // No header: the first line is data, so do not discard it.
                read++;
                ImdbRating first = ImdbRatingsParser.parseLine(line);
                if (first != null) {
                    batch.add(first);
                    parsed++;
                }
            }

            while ((line = reader.readLine()) != null) {
                read++;
                ImdbRating rating = ImdbRatingsParser.parseLine(line);
                if (rating == null) {
                    continue;
                }
                batch.add(rating);
                parsed++;

                if (batch.size() >= properties.batchSize()) {
                    // A copy, not the buffer itself. The buffer is cleared and
                    // reused on the next iteration, so handing it over directly
                    // means the callee holds a reference to a list that changes
                    // underneath it -- harmless today, and exactly the kind of
                    // aliasing that breaks the moment anything downstream
                    // retains or defers.
                    updated += updater.applyBatch(List.copyOf(batch));
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                updated += updater.applyBatch(List.copyOf(batch));
            }
        }

        run.setRowsRead(read);
        run.setRowsParsed(parsed);
        run.setRowsUpdated(updated);
        meters.counter("watchguru.imdb.rows", "kind", "updated").increment(updated);
    }

    private ImdbImportRun finish(ImdbImportRun run) {
        run.setFinishedAt(Instant.now());
        return runs.save(run);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
