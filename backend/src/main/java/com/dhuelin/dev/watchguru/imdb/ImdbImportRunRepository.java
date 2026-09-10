package com.dhuelin.dev.watchguru.imdb;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImdbImportRunRepository extends JpaRepository<ImdbImportRun, Long> {

    /**
     * The most recent run that actually read the dataset, used to recover the
     * {@code Last-Modified} to send as {@code If-Modified-Since}.
     *
     * <p>Only successful runs count: a failed run may have read a partial file,
     * and treating its header as "already imported" would skip the retry.
     */
    Optional<ImdbImportRun> findFirstByStatusOrderByStartedAtDesc(ImdbImportRun.Status status);
}
