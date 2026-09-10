package com.dhuelin.dev.watchguru.imdb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One execution of the IMDb ratings enrichment job. */
@Entity
@Table(name = "imdb_import_run")
@Getter
@Setter
@NoArgsConstructor
public class ImdbImportRun {

    public enum Status { RUNNING, SUCCESS, SKIPPED_NOT_MODIFIED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.RUNNING;

    @Column(name = "source_last_modified", length = 64)
    private String sourceLastModified;

    @Column(name = "rows_read", nullable = false)
    private long rowsRead;

    @Column(name = "rows_parsed", nullable = false)
    private long rowsParsed;

    @Column(name = "rows_updated", nullable = false)
    private long rowsUpdated;

    @Column(name = "error_message")
    private String errorMessage;
}
