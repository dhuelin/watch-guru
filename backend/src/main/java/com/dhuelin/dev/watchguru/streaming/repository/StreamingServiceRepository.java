package com.dhuelin.dev.watchguru.streaming.repository;

import com.dhuelin.dev.watchguru.streaming.domain.StreamingService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StreamingServiceRepository extends JpaRepository<StreamingService, Long> {

    Optional<StreamingService> findByTmdbProviderId(Long tmdbProviderId);

    Optional<StreamingService> findBySlug(String slug);

    List<StreamingService> findBySupportsSyncTrue();
}
