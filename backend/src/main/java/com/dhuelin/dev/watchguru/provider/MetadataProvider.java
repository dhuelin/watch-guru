package com.dhuelin.dev.watchguru.provider;

import com.dhuelin.dev.watchguru.catalog.domain.TitleType;
import com.dhuelin.dev.watchguru.provider.model.ProviderOffer;
import com.dhuelin.dev.watchguru.provider.model.ProviderSearchPage;
import com.dhuelin.dev.watchguru.provider.model.ProviderSeasonDetail;
import com.dhuelin.dev.watchguru.provider.model.ProviderTitleDetail;
import com.dhuelin.dev.watchguru.provider.model.ProviderWatchService;

import java.util.List;

/**
 * Source of film and series metadata.
 *
 * <p>Everything above this interface works in terms of the provider-neutral
 * records in {@code provider.model}, so swapping TMDB for another source — or
 * layering the IMDb bulk datasets alongside it — does not reach into the
 * catalog or tracking code.
 */
public interface MetadataProvider {

    /** Short name of the backing source, recorded for diagnostics. */
    String sourceName();

    /** Combined movie and series search, 1-based page numbering. */
    ProviderSearchPage search(String query, int page, String language);

    /**
     * What people are watching this week, films and series together.
     *
     * <p>The answer to an empty search box. A tracker whose search screen is
     * blank until something is typed gives a new user nothing to track, and
     * asks them to remember a title before the app is any use.
     *
     * <p>Returns the same summaries as {@link #search}, so a row here and a
     * result there are the same thing and are imported the same way.
     */
    ProviderSearchPage trending(int page, String language);

    ProviderTitleDetail fetchDetail(TitleType titleType, long providerId, String language);

    ProviderSeasonDetail fetchSeason(long seriesProviderId, int seasonNumber, String language);

    /** Ways to watch the title in the given ISO 3166-1 region. */
    List<ProviderOffer> fetchOffers(TitleType titleType, long providerId, String region);

    /** The provider's full list of streaming services for a region. */
    List<ProviderWatchService> listWatchServices(String region);
}
