package com.dhuelin.dev.watchguru.provider.model;

import java.util.List;

public record ProviderSearchPage(
        List<ProviderTitleSummary> results,
        int page,
        int totalPages,
        long totalResults
) {
}
