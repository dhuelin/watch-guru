package com.dhuelin.dev.watchguru.streaming.repository;

import com.dhuelin.dev.watchguru.streaming.domain.AvailabilityCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AvailabilityCheckRepository extends JpaRepository<AvailabilityCheck, Long> {

    Optional<AvailabilityCheck> findByTitleIdAndRegion(Long titleId, String region);
}
