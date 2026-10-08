package dev.dhuelin.watchguru.data

import dev.dhuelin.watchguru.api.models.RegisterDevice
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the backend where to send this user's pushes, and when to stop.
 *
 * Registration is idempotent by design on the server, which is what lets this
 * run on every launch: push services reissue tokens without saying which launch
 * was the one that changed, so the only reliable policy is to send whatever the
 * device has, every time.
 *
 * The device's time zone rides along because this is the only call that knows
 * it. Without it the backend's quiet hours would be Greenwich's for everybody,
 * and a user in Auckland would be woken at four in the afternoon.
 */
@Singleton
class DeviceRegistrar @Inject constructor(
    private val tokens: PushTokens,
    private val repository: WatchGuruRepository,
) {

    /**
     * Registers this install, if it has a token and a session.
     *
     * Best-effort on purpose: a failure here must not interrupt a launch or a
     * sign-in. The next launch tries again, and nothing the user can see
     * depends on it having worked this time.
     */
    suspend fun register(): Boolean {
        val token = tokens.current() ?: return false
        return repository.registerDevice(
            RegisterDevice(
                platform = RegisterDevice.Platform.ANDROID,
                token = token,
                // The IANA name, not a raw offset: an offset is wrong twice a
                // year, and quiet hours that drift an hour each spring are
                // worse than none.
                timeZone = ZoneId.systemDefault().id,
            )
        ) is ApiResult.Success
    }

    /**
     * Forgets this install, so the next user of this phone is not notified
     * about the last one's series.
     *
     * This has to happen while the session still exists -- it is an
     * authenticated call about the caller's own device -- which is why
     * sign-out runs it before clearing the token rather than after.
     */
    suspend fun unregister() {
        val token = tokens.current() ?: return
        repository.unregisterDevice(token)
    }
}
