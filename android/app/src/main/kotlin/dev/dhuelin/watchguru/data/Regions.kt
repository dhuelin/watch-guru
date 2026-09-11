package dev.dhuelin.watchguru.data

import java.text.Collator
import java.util.Locale

/**
 * The list of countries the user can pick from, and how it is searched.
 *
 * Kept out of the UI so the rules can be tested: names come from the platform
 * in the user's own language, the list is sorted the way that language sorts
 * (which is not the same as sorting by code point), and a search matches the
 * country code as well as the name.
 */
object Regions {

    data class Region(val code: String, val name: String)

    /**
     * Every ISO 3166-1 country, named in [locale] and sorted for it.
     *
     * Codes with no display name in this locale are dropped: the platform
     * returns the code back when it has no translation, and a list row reading
     * "XK" helps nobody choose.
     */
    fun all(locale: Locale = Locale.getDefault()): List<Region> {
        val collator = Collator.getInstance(locale)
        return Locale.getISOCountries()
            .map { code -> Region(code, displayName(code, locale)) }
            .filter { it.name.isNotBlank() && !it.name.equals(it.code, ignoreCase = true) }
            .sortedWith(compareBy(collator) { it.name })
    }

    /**
     * The list narrowed to a search.
     *
     * The code is matched too, because someone who knows their country is "CH"
     * should not have to remember whether the app calls it Switzerland or
     * Suisse.
     */
    fun search(regions: List<Region>, query: String): List<Region> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return regions
        return regions.filter {
            it.name.contains(trimmed, ignoreCase = true) || it.code.equals(trimmed, ignoreCase = true)
        }
    }

    /** The country's name, or the code itself when the platform has no name for it. */
    fun displayName(code: String, locale: Locale = Locale.getDefault()): String =
        Locale.Builder().setRegion(code).build().getDisplayCountry(locale).ifBlank { code }
}
