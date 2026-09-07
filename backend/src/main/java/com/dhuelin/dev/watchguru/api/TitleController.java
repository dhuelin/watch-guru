package com.dhuelin.dev.watchguru.api;

import com.dhuelin.dev.watchguru.api.dto.ApiMapper;
import com.dhuelin.dev.watchguru.api.dto.Responses;
import com.dhuelin.dev.watchguru.catalog.domain.Title;
import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.catalog.repository.TitleRepository;
import com.dhuelin.dev.watchguru.catalog.service.CatalogService;
import com.dhuelin.dev.watchguru.common.NotFoundException;
import com.dhuelin.dev.watchguru.config.TmdbProperties;
import com.dhuelin.dev.watchguru.streaming.service.AvailabilityService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Catalog search and title detail. */
@RestController
@RequestMapping("/api/v1/titles")
public class TitleController {

    private final CatalogService catalog;
    private final AvailabilityService availability;
    private final TitleRepository titles;
    private final ApiMapper mapper;
    private final TmdbProperties tmdbProperties;

    public TitleController(CatalogService catalog,
                           AvailabilityService availability,
                           TitleRepository titles,
                           ApiMapper mapper,
                           TmdbProperties tmdbProperties) {
        this.catalog = catalog;
        this.availability = availability;
        this.titles = titles;
        this.mapper = mapper;
        this.tmdbProperties = tmdbProperties;
    }

    /** Live search against the metadata provider; nothing is persisted. */
    @GetMapping("/search")
    public Responses.SearchResponse search(@RequestParam @NotBlank String query,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(required = false) String language) {
        return mapper.toSearch(catalog.search(query, page, language));
    }

    /** Imports a provider title into the local catalog and returns it. */
    @PostMapping("/import")
    public Responses.TitleResponse importTitle(@RequestParam TitleType titleType,
                                               @RequestParam long providerId,
                                               @RequestParam(required = false) String language) {
        Title title = catalog.importTitle(titleType, providerId, language);
        return mapper.toTitle(title, availability.offersFor(title, region(null)));
    }

    @GetMapping("/{titleId}")
    public Responses.TitleResponse get(@PathVariable Long titleId,
                                       @RequestParam(required = false) String region) {
        Title title = titles.findById(titleId)
                .orElseThrow(() -> NotFoundException.of("Title", titleId));
        return mapper.toTitle(title, availability.offersFor(title, region(region)));
    }

    private String region(String requested) {
        return requested == null || requested.isBlank()
                ? tmdbProperties.defaultRegion()
                : requested.toUpperCase();
    }
}
