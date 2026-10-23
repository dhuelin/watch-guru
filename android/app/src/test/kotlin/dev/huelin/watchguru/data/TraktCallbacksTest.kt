package dev.huelin.watchguru.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TraktCallbacksTest {

    @Test
    fun `reads the code and state, decoded`() {
        assertEquals(
            TraktCallback("a b&c", "s/t"),
            TraktCallback.parse("watchguru://trakt/callback?code=a%20b%26c&state=s/t"),
        )
    }

    @Test
    fun `anything but the app's own callback is ignored`() {
        listOf(
            "https://trakt.tv/callback?code=a&state=b",
            "watchguru://other/callback?code=a&state=b",
            "watchguru://trakt/elsewhere?code=a&state=b",
            "watchguru://trakt/callback?code=a",
            "watchguru://trakt/callback?code=&state=b",
            "not a uri at all",
        ).forEach { assertNull(it, TraktCallback.parse(it)) }
    }

    @Test
    fun `a callback is handed over once`() {
        val callbacks = TraktCallbacks()
        callbacks.offer(TraktCallback("old", "s1"))
        callbacks.offer(TraktCallback("new", "s2"))

        assertEquals(TraktCallback("new", "s2"), callbacks.take())
        assertNull(callbacks.take())
    }
}
