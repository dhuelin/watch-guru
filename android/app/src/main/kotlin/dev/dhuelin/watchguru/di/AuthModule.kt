package dev.dhuelin.watchguru.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.dhuelin.watchguru.BuildConfig
import dev.dhuelin.watchguru.data.CoilDataCleaner
import dev.dhuelin.watchguru.data.GoogleSignIn
import dev.dhuelin.watchguru.data.LocalDataCleaner
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

    @Provides
    @Singleton
    fun localDataCleaner(@ApplicationContext context: Context): LocalDataCleaner =
        CoilDataCleaner(context)
}
