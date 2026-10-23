package dev.huelin.watchguru.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInNonceTest {

    @Test
    fun `the hash is lowercase hex SHA-256, as the backend computes it`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            SignInNonce.sha256Hex("abc"),
        )
    }

    @Test
    fun `every nonce is fresh and long enough for the backend`() {
        val first = SignInNonce.make()
        val second = SignInNonce.make()

        assertNotEquals(first, second)
        assertTrue(first.raw.length >= 16)
        assertFalse(first.raw.contains("="))
    }
}
