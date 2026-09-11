package dev.dhuelin.watchguru.data

import dev.dhuelin.watchguru.api.models.AvailabilityResponse
import java.time.OffsetDateTime

/**
 * How the offers on a title are turned into the "Where to watch" section.
 *
 * Plain Kotlin rather than something inside the composable, because the two
 * rules below are the ones worth testing: what order the ways of watching are
 * shown in, and that a service does not appear twice in the same group.
 */
object WatchOffers {

    /**
     * Ways of watching, in the order they are offered to the user.
     *
     * Cheapest first, which is what the app can honestly rank by: free, then
     * free with ads, then a subscription, then rent, then buy. It does not
     * know which services the user already pays for -- when it does, a
     * subscription they hold belongs above a free service they have never
     * heard of, and this order should change with it.
     *
     * Free was previously listed after subscriptions, which contradicted this
     * comment; the comment was the one making the promise.
     */
    val order: List<AvailabilityResponse.OfferType> = listOf(
        AvailabilityResponse.OfferType.FREE,
        AvailabilityResponse.OfferType.ADS,
        AvailabilityResponse.OfferType.FLATRATE,
        AvailabilityResponse.OfferType.RENT,
        AvailabilityResponse.OfferType.BUY,
    )

    /** One group per way of watching, empty groups dropped. */
    fun group(offers: List<AvailabilityResponse>): List<Group> =
        order.mapNotNull { type ->
            val services = offers.asSequence()
                .filter { it.offerType == type }
                // A provider can return the same service twice for one offer
                // type (regional sub-brands share an id); showing "Netflix,
                // Netflix" would read as a bug.
                .distinctBy { it.serviceId }
                .sortedBy { it.serviceName.lowercase() }
                .toList()
            if (services.isEmpty()) null else Group(type, services)
        }

    /**
     * The one link to open, or null when there isn't one.
     *
     * The provider gives a single link per region -- a page listing every way
     * to watch the title -- not a deep link per service. So the section offers
     * one link rather than making each service row look like a way into that
     * app, which it is not.
     */
    fun link(offers: List<AvailabilityResponse>): String? =
        offers.firstNotNullOfOrNull { it.link?.takeIf(String::isNotBlank) }

    /**
     * When this data was last confirmed with the provider.
     *
     * The oldest of the rows rather than the newest: a section is only as
     * current as its stalest row, and claiming otherwise would round in the
     * flattering direction.
     */
    fun checkedAt(offers: List<AvailabilityResponse>): OffsetDateTime? =
        offers.minByOrNull { it.fetchedAt }?.fetchedAt

    data class Group(
        val offerType: AvailabilityResponse.OfferType,
        val services: List<AvailabilityResponse>,
    )
}
