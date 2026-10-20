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
                offer(5, "Tubi", OfferType.FREE),
            ),
        )

        assertEquals(
            listOf(OfferType.FREE, OfferType.ADS, OfferType.FLATRATE, OfferType.RENT, OfferType.BUY),
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

    @Test
    fun `the section is only as current as its stalest row`() {
        val older = OffsetDateTime.parse("2026-01-01T00:00:00Z")
        val newer = OffsetDateTime.parse("2026-01-02T00:00:00Z")

        val offers = listOf(
            AvailabilityResponse(newer, OfferType.FLATRATE, 1, "Netflix"),
            AvailabilityResponse(older, OfferType.RENT, 2, "Apple TV"),
        )

        assertEquals(older, WatchOffers.checkedAt(offers))
    }

    @Test
    fun `no offers means nothing to date`() {
        assertNull(WatchOffers.checkedAt(emptyList()))
    }

    // The services the user says they pay for. To them a subscription they
    // already hold costs nothing, so it outranks a free service they have
    // never heard of -- which is what the ordering comment always promised.

    @Test
    fun `a way to watch on a service the user holds comes first`() {
        val groups = WatchOffers.group(
            offers = listOf(
                offer(1, "Tubi", OfferType.FREE),
                offer(2, "Netflix", OfferType.FLATRATE),
            ),
            mine = setOf(2),
        )

        // Without `mine` this is FREE then FLATRATE, as the other tests check.
        assertEquals(listOf(OfferType.FLATRATE, OfferType.FREE), groups.map { it.offerType })
        assertEquals(true, groups.first().yours)
        assertEquals(false, groups.last().yours)
    }

    @Test
    fun `no held service leaves the cheapest-first order alone`() {
        val groups = WatchOffers.group(
            offers = listOf(
                offer(1, "Tubi", OfferType.FREE),
                offer(2, "Netflix", OfferType.FLATRATE),
            ),
            mine = setOf(99),
        )

        assertEquals(listOf(OfferType.FREE, OfferType.FLATRATE), groups.map { it.offerType })
        assertEquals(false, groups.any { it.yours })
    }

    @Test
    fun `inside a group the held service is listed first`() {
        // Alphabetically Disney+ precedes Netflix, so this can only pass if
        // the held service was lifted above it.
        val groups = WatchOffers.group(
            offers = listOf(
                offer(1, "Disney+", OfferType.FLATRATE),
                offer(2, "Netflix", OfferType.FLATRATE),
            ),
            mine = setOf(2),
        )

        assertEquals(listOf("Netflix", "Disney+"), groups.single().services.map { it.serviceName })
    }

    @Test
    fun `groups that tie keep the cheapest-first order between them`() {
        // Both groups contain a held service, so neither wins on that: the
        // original order has to survive. A sort on the flag alone would be
        // free to reshuffle these.
        val groups = WatchOffers.group(
            offers = listOf(
                offer(1, "Tubi", OfferType.FREE),
                offer(2, "Netflix", OfferType.FLATRATE),
            ),
            mine = setOf(1, 2),
        )

        assertEquals(listOf(OfferType.FREE, OfferType.FLATRATE), groups.map { it.offerType })
        assertEquals(true, groups.all { it.yours })
    }

    @Test
    fun `a held service the title is not on changes nothing`() {
        val groups = WatchOffers.group(
            offers = listOf(offer(2, "Netflix", OfferType.FLATRATE)),
            mine = setOf(7),
        )

        assertEquals(listOf(OfferType.FLATRATE), groups.map { it.offerType })
        assertEquals(false, groups.single().yours)
    }
}
