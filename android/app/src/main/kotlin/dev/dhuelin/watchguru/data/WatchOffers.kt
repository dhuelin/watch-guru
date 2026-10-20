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
     * free with ads, then a subscription, then rent, then buy.
     *
     * This is now the order only for services the user has NOT told us they
     * pay for. It used to be the whole story, and the comment here promised
     * that it would stop being: "a subscription they hold belongs above a free
     * service they have never heard of, and this order should change with it".
     * [group] now takes that set and does exactly that.
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

    /**
     * One group per way of watching, empty groups dropped.
     *
     * @param mine the services the user says they subscribe to. A way of
     *   watching that uses one of them comes first, because to that user it
     *   costs nothing -- which is the same reason free comes before rent. The
     *   remaining groups keep [order].
     *
     * Partitioned rather than sorted, on both platforms, so the two agree
     * exactly: a partition preserves the order of each side by construction,
     * where Swift's `sorted(by:)` makes no stability promise at all.
     */
    fun group(
        offers: List<AvailabilityResponse>,
        mine: Set<Long> = emptySet(),
    ): List<Group> {
        val groups = order.mapNotNull { type ->
            val services = offers.asSequence()
                .filter { it.offerType == type }
                // A provider can return the same service twice for one offer
                // type (regional sub-brands share an id); showing "Netflix,
                // Netflix" would read as a bug.
                .distinctBy { it.serviceId }
                .sortedBy { it.serviceName.lowercase() }
                .toList()
            if (services.isEmpty()) {
                null
            } else {
                // Within a group too: the service they pay for is the one they
                // will use, so it goes above one they would have to sign up to.
                val (held, rest) = services.partition { it.serviceId in mine }
                Group(type, held + rest, yours = held.isNotEmpty())
            }
        }
        val (yours, others) = groups.partition { it.yours }
        return yours + others
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
        /**
         * Whether this group uses a service the user subscribes to.
         *
         * Carried so the screen can say so. Reordering without a visible
         * reason reads as arbitrary -- the badge is what turns "why is Rent
         * above Free" into "because I pay for that one".
         */
        val yours: Boolean = false,
    )
}
