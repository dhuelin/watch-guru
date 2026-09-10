package dev.dhuelin.watchguru.data

import dev.dhuelin.watchguru.api.models.AvailabilityResponse
import dev.dhuelin.watchguru.api.models.AvailabilityResponse.OfferType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

/**
 * The two rules behind the "Where to watch" section: what order the ways of
 * watching appear in, and that one service never shows up twice in a group.
 */
class WatchOffersTest {

    private fun offer(
        serviceId: Long,
        name: String,
        type: OfferType,
        link: String? = null,
    ) = AvailabilityResponse(
        fetchedAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        offerType = type,
        serviceId = serviceId,
        serviceName = name,
        link = link,
    )

    @Test
    fun `free ways to watch come before paid ones`() {
        val groups = WatchOffers.group(
            listOf(
                offer(1, "Apple TV", OfferType.BUY),
                offer(2, "Amazon", OfferType.RENT),
                offer(3, "Netflix", OfferType.FLATRATE),
                offer(4, "Pluto TV", OfferType.ADS),
            ),
        )

        assertEquals(
            listOf(OfferType.FLATRATE, OfferType.ADS, OfferType.RENT, OfferType.BUY),
            groups.map { it.offerType },
        )
    }

    @Test
    fun `a group with nothing in it is not shown at all`() {
        val groups = WatchOffers.group(listOf(offer(3, "Netflix", OfferType.FLATRATE)))

        assertEquals(1, groups.size)
        assertEquals(OfferType.FLATRATE, groups.single().offerType)
    }

    @Test
    fun `the same service twice in one group is shown once`() {
        val groups = WatchOffers.group(
            listOf(
                offer(3, "Netflix", OfferType.FLATRATE),
                offer(3, "Netflix", OfferType.FLATRATE),
            ),
        )

        assertEquals(listOf("Netflix"), groups.single().services.map { it.serviceName })
    }

    @Test
    fun `a service offering two ways to watch appears in both groups`() {
        val groups = WatchOffers.group(
            listOf(
                offer(1, "Apple TV", OfferType.RENT),
                offer(1, "Apple TV", OfferType.BUY),
            ),
        )

        assertEquals(listOf(OfferType.RENT, OfferType.BUY), groups.map { it.offerType })
    }

    @Test
    fun `services within a group are sorted by name, case aside`() {
        val groups = WatchOffers.group(
            listOf(
                offer(1, "netflix", OfferType.FLATRATE),
                offer(2, "Disney+", OfferType.FLATRATE),
            ),
        )

        assertEquals(listOf("Disney+", "netflix"), groups.single().services.map { it.serviceName })
    }

    @Test
    fun `the link is taken from whichever offer has one`() {
        val link = WatchOffers.link(
            listOf(
                offer(3, "Netflix", OfferType.FLATRATE),
                offer(1, "Apple TV", OfferType.BUY, link = "https://example.test/watch"),
            ),
        )

        assertEquals("https://example.test/watch", link)
    }

    @Test
    fun `a blank link is no link, so no button is offered`() {
        assertNull(WatchOffers.link(listOf(offer(3, "Netflix", OfferType.FLATRATE, link = "  "))))
        assertNull(WatchOffers.link(emptyList()))
    }
}
