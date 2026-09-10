package com.dhuelin.dev.watchguru.streaming.repository;

import com.dhuelin.dev.watchguru.streaming.domain.TitleAvailability;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TitleAvailabilityRepository extends JpaRepository<TitleAvailability, Long> {

    @EntityGraph(attributePaths = "streamingService")
    List<TitleAvailability> findByTitleIdAndRegion(Long titleId, String region);

    void deleteByTitleIdAndRegion(Long titleId, String region);
}
