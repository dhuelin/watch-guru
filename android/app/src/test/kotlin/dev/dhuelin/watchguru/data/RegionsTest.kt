package dev.dhuelin.watchguru.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** How the country list is built and searched. */
class RegionsTest {

    private val english = Locale.ENGLISH

    @Test
    fun `the list covers the countries people will look for`() {
        val codes = Regions.all(english).map { it.code }

        assertTrue(codes.containsAll(listOf("US", "GB", "CH", "DE", "JP")))
    }

    @Test
    fun `countries are named, not left as codes`() {
        val switzerland = Regions.all(english).single { it.code == "CH" }

        assertEquals("Switzerland", switzerland.name)
    }

    @Test
    fun `accented names sort under their base letter, not after Z`() {
        // The reason the list goes through a Collator at all. Sorting these
        // strings by code point puts "Aland Islands" and "Cote d'Ivoire" --
        // both spelled with an accent -- at the very bottom of the list, past
        // Zimbabwe, where nobody scrolling for A or C would ever find them.
        val names = Regions.all(english).map { it.name }
        val aland = names.first { it.endsWith("land Islands") }
        val ivoryCoast = names.first { it.endsWith("te d\u2019Ivoire") }

        assertTrue(names.indexOf(aland) < names.indexOf("Albania"))
        assertTrue(names.indexOf(ivoryCoast) < names.indexOf("Croatia"))
        assertTrue(names.indexOf(ivoryCoast) > names.indexOf("Costa Rica"))
    }

    @Test
    fun `searching matches part of a name, whatever the case`() {
        val hits = Regions.search(Regions.all(english), "switz")

        assertEquals(listOf("CH"), hits.map { it.code })
    }

    @Test
    fun `searching matches the country code itself`() {
        // Someone who knows they want "CH" should not have to know whether the
        // app says Switzerland, Suisse or Schweiz.
        val hits = Regions.search(Regions.all(english), "ch")

        assertTrue(hits.map { it.code }.contains("CH"))
    }

    @Test
    fun `an empty search is not a filter`() {
        val all = Regions.all(english)

        assertEquals(all, Regions.search(all, "   "))
    }

    @Test
    fun `a code the platform has no name for reads back as itself`() {
        // Never blank: a profile row showing nothing at all looks like a
        // failed load. "ZZ" is not the example to use here -- CLDR does have a
        // name for it, its own placeholder, "Unknown Region".
        assertEquals("QQ", Regions.displayName("QQ", english))
    }
}
