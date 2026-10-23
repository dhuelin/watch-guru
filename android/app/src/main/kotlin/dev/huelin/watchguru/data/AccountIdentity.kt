package dev.huelin.watchguru.data

import java.util.Locale

/**
 * Which parts of an account are fit to show as "who you are".
 *
 * Plain Kotlin, so the rule is tested rather than eyeballed.
 */
object AccountIdentity {

    /**
     * Whether an address is one nobody would recognise as their own.
     *
     * Two kinds. Synthetic ones the backend mints for an account whose
     * provider gave no address -- under the reserved `.invalid` top-level
     * domain, which can never deliver mail -- and relay addresses that forward
     * to a hidden inbox, such as Apple's private relay. A 32-character hex
     * string under the name reads as a bug, not as an identity.
     */
    fun isRelayAddress(email: String?): Boolean {
        val address = email?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (address.isEmpty()) return true
        val domain = address.substringAfterLast('@', missingDelimiterValue = "")
        return domain.endsWith(".invalid") ||
            domain == "invalid" ||
            domain.endsWith("privaterelay.appleid.com") ||
            "noreply" in address ||
            "no-reply" in address
    }
}
