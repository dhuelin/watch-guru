package dev.dhuelin.watchguru.data

import dev.dhuelin.watchguru.api.models.AvailabilityResponse

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
     * Included is free before paid, and subscription before transactional: the
     * cheapest way to watch something tonight goes first. Rent before buy for
     * the same reason. This is not the order the API returns.
     */
    val order: List<AvailabilityResponse.OfferType> = listOf(
        AvailabilityResponse.OfferType.FLATRATE,
        AvailabilityResponse.OfferType.FREE,
        AvailabilityResponse.OfferType.ADS,
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

    data class Group(
        val offerType: AvailabilityResponse.OfferType,
        val services: List<AvailabilityResponse>,
    )
}
