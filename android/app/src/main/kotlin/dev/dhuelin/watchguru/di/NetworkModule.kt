package dev.dhuelin.watchguru.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.dhuelin.watchguru.BuildConfig
import dev.dhuelin.watchguru.api.apis.MeControllerApi
import dev.dhuelin.watchguru.api.apis.TitleControllerApi
import dev.dhuelin.watchguru.api.apis.WatchHistoryControllerApi
import dev.dhuelin.watchguru.api.apis.WatchlistControllerApi
import dev.dhuelin.watchguru.data.AuthInterceptor
import dev.dhuelin.watchguru.data.EncryptedTokenStore
import dev.dhuelin.watchguru.data.TokenStore
import dev.dhuelin.watchguru.data.WatchGuruRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun tokenStore(@ApplicationContext context: Context): TokenStore = EncryptedTokenStore(context)

    @Provides
    @Singleton
    fun json(): Json = Json {
        // The backend may add response fields before this app is updated; a
        // shipped app cannot be forced to upgrade, so unknown keys must never
        // be fatal.
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun okHttp(tokens: TokenStore): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokens))
        .apply {
            if (BuildConfig.DEBUG) {
                // BASIC, not BODY: bodies carry the bearer token and the user's
                // viewing history, neither of which belongs in logcat.
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
            }
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton fun titleApi(retrofit: Retrofit): TitleControllerApi = retrofit.create()
    @Provides @Singleton fun watchlistApi(retrofit: Retrofit): WatchlistControllerApi = retrofit.create()
    @Provides @Singleton fun historyApi(retrofit: Retrofit): WatchHistoryControllerApi = retrofit.create()
    @Provides @Singleton fun meApi(retrofit: Retrofit): MeControllerApi = retrofit.create()

    @Provides
    @Singleton
    fun repository(
        titles: TitleControllerApi,
        watchlist: WatchlistControllerApi,
        history: WatchHistoryControllerApi,
        me: MeControllerApi,
    ): WatchGuruRepository = WatchGuruRepository(titles, watchlist, history, me, ioDispatcher())

    private fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
