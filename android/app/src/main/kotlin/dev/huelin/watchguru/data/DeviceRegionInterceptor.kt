package dev.huelin.watchguru.data

import okhttp3.Interceptor
import okhttp3.Response
import java.util.Locale

/**
 * Tells the backend which country this device is in.
 *
 * The backend reads it once, when it creates the account on first sign-in, so
 * a new user starts out seeing streaming offers for their own country rather
 * than the server's default. After that the region on the profile is the
 * user's own, changed in Profile, and this header no longer affects anything.
 *
 * Read per request, like [AuthInterceptor], so a locale change made while the
 * app is running is what the next sign-up sees. Omitted when the locale has no
 * country, or only a numeric area such as Latin America's 419: the server
 * would ignore those anyway.
 */
class DeviceRegionInterceptor(
    private val locale: () -> Locale = { Locale.getDefault() },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val region = regionOf(locale())
        val request = if (region == null) {
            chain.request()
        } else {
            chain.request().newBuilder().header(HEADER, region).build()
        }
        return chain.proceed(request)
    }

    companion object {
        const val HEADER = "X-Device-Region"

        /** The locale's ISO 3166-1 alpha-2 country, or null when it has none. */
        fun regionOf(locale: Locale): String? =
            locale.country.uppercase(Locale.ROOT).takeIf { it.length == 2 && it.all(Char::isLetter) }
    }
}
