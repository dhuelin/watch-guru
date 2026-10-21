package dev.huelin.watchguru.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** Which country the device reports to the backend at sign-up. */
class DeviceRegionInterceptorTest {

    @Test
    fun `a locale with a country reports it`() {
        assertEquals("CH", DeviceRegionInterceptor.regionOf(Locale.forLanguageTag("de-CH")))
        assertEquals("US", DeviceRegionInterceptor.regionOf(Locale.US))
    }

    @Test
    fun `a locale without a country reports nothing`() {
        assertNull(DeviceRegionInterceptor.regionOf(Locale.ENGLISH))
    }

    @Test
    fun `a numeric area is not a country`() {
        assertNull(DeviceRegionInterceptor.regionOf(Locale.forLanguageTag("es-419")))
    }
}
