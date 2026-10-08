package dev.dhuelin.watchguru.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.dhuelin.watchguru.data.PushTokens
import dev.dhuelin.watchguru.data.UnavailablePushTokens
import javax.inject.Singleton

/**
 * Where push tokens come from.
 *
 * A module of its own, and a one-line one, because this is the single binding
 * that changes when #37 lands: swapping [UnavailablePushTokens] for a Firebase
 * implementation is the whole of the client-side work, and keeping it here
 * means nothing else has to be found and edited to do it.
 */
@Module
@InstallIn(SingletonComponent::class)
object NotificationModule {

    @Provides
    @Singleton
    fun pushTokens(): PushTokens = UnavailablePushTokens()
}
