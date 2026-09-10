package dev.dhuelin.watchguru.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.dhuelin.watchguru.BuildConfig
import dev.dhuelin.watchguru.api.apis.AuthenticationApi
import dev.dhuelin.watchguru.api.apis.MeControllerApi
import dev.dhuelin.watchguru.api.apis.TitleControllerApi
import dev.dhuelin.watchguru.api.apis.WatchHistoryControllerApi
import dev.dhuelin.watchguru.api.apis.WatchlistControllerApi
import dev.dhuelin.watchguru.data.AuthInterceptor
import dev.dhuelin.watchguru.data.EncryptedTokenStore
import dev.dhuelin.watchguru.data.SessionAuthenticator
import dev.dhuelin.watchguru.data.SessionEvents
import dev.dhuelin.watchguru.data.SessionRepository
import dev.dhuelin.watchguru.data.TokenStore
import dev.dhuelin.watchguru.data.WatchGuruRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
// The reified extension. Without it retrofit.create() resolves to Retrofit's
// Java create(Class<T>) and the compiler asks for the argument that the Kotlin
// form exists to avoid.
import retrofit2.create
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Marks the client and Retrofit used only for the token endpoints.
 *
 * That stack has no [SessionAuthenticator] and no [AuthInterceptor]. Both would
 * be wrong here: the token endpoints take their credential in the body, and a
 * refusal from the refresh endpoint must not itself trigger a refresh.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthStack

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

    private fun logging(): HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        // BASIC, not BODY: bodies carry the tokens themselves and the user's
        // viewing history, neither of which belongs in logcat.
        level = HttpLoggingInterceptor.Level.BASIC
    }

    @Provides
    @Singleton
    @AuthStack
    fun authOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .apply { if (BuildConfig.DEBUG) addInterceptor(logging()) }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @AuthStack
    fun authRetrofit(@AuthStack client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun authApi(@AuthStack retrofit: Retrofit): AuthenticationApi = retrofit.create()

    @Provides
    @Singleton
    fun sessionRepository(auth: AuthenticationApi): SessionRepository =
        SessionRepository(auth, Dispatchers.IO)

    /**
     * The client every other call goes through.
     *
     * The authenticator is what keeps a session alive past the fifteen-minute
     * access token. It refreshes on the server's 401 rather than on a clock,
     * and it clears the session when the refresh token is refused -- which is
     * not transient: it means revoked, expired, or detected as reused.
     */
    @Provides
    @Singleton
    fun okHttp(
        tokens: TokenStore,
        sessions: SessionRepository,
        events: SessionEvents,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokens))
            .authenticator(
                SessionAuthenticator(
                    tokens = tokens,
                    refresh = { refreshToken -> sessions.refresh(refreshToken) },
                    onSessionLost = {
                        tokens.clear()
                        events.onSessionLost()
                    },
                )
            )
            .apply { if (BuildConfig.DEBUG) addInterceptor(logging()) }
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
