package dev.dhuelin.watchguru.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.dhuelin.watchguru.BuildConfig
import dev.dhuelin.watchguru.data.GoogleSignIn
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    /**
     * No context here on purpose: Credential Manager needs the Activity, which
     * is supplied per call. Binding the application context would compile and
     * then fail at runtime when the sheet tries to appear.
     */
    @Provides
    @Singleton
    fun googleSignIn(): GoogleSignIn = GoogleSignIn(BuildConfig.GOOGLE_WEB_CLIENT_ID)
}
