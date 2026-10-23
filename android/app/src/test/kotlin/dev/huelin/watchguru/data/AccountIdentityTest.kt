package dev.huelin.watchguru.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which addresses Profile may show as the person's identity.
 *
 * The case that prompted it: a 32-character hex address under the reserved
 * `.invalid` domain, shown under the user's name as if it were theirs.
 */
class AccountIdentityTest {

    @Test
    fun `a synthetic address under the invalid domain is hidden`() {
        assertTrue(
            AccountIdentity.isRelayAddress("9732794a2c0607a589e5d464f9c460d7@users.noreply.watch-guru.invalid"),
        )
        assertTrue(AccountIdentity.isRelayAddress("someone@example.invalid"))
    }

    @Test
    fun `noreply and relay addresses are hidden`() {
        assertTrue(AccountIdentity.isRelayAddress("noreply@watch-guru.example"))
        assertTrue(AccountIdentity.isRelayAddress("NO-REPLY@example.com"))
        assertTrue(AccountIdentity.isRelayAddress("abc123@privaterelay.appleid.com"))
    }

    @Test
    fun `no address at all is not an identity either`() {
        assertTrue(AccountIdentity.isRelayAddress(null))
        assertTrue(AccountIdentity.isRelayAddress("  "))
    }

    @Test
    fun `an ordinary address is shown`() {
        assertFalse(AccountIdentity.isRelayAddress("denis@example.com"))
        // Only the domain's ending counts: "invalid" elsewhere is a real name.
        assertFalse(AccountIdentity.isRelayAddress("invalid.person@gmail.com"))
    }
}
