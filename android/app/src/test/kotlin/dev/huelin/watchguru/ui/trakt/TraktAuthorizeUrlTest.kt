package dev.huelin.watchguru.ui.trakt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app opens the browser only on Trakt's own sign-in page. */
class TraktAuthorizeUrlTest {

    @Test
    fun `Trakt's authorize page is accepted`() {
        assertTrue(isTraktAuthorizeUrl("https://trakt.tv/oauth/authorize?client_id=x&state=y"))
        assertTrue(isTraktAuthorizeUrl("https://api.trakt.tv/oauth/authorize"))
    }

    @Test
    fun `anything else is refused`() {
        assertFalse(isTraktAuthorizeUrl("http://trakt.tv/oauth/authorize"))
        assertFalse(isTraktAuthorizeUrl("https://trakt.tv.example.com/oauth/authorize"))
        assertFalse(isTraktAuthorizeUrl("https://eviltrakt.tv/oauth/authorize"))
        assertFalse(isTraktAuthorizeUrl("intent://trakt.tv#Intent;end"))
        assertFalse(isTraktAuthorizeUrl("not a url"))
    }
}
