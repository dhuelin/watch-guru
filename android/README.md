# Watch Guru for Android

Jetpack Compose, Material 3, single activity. Talks to the Watch Guru API over
the generated client in `api-client/`.

## Building

```bash
cd android
./gradlew :app:assembleDebug
```

Requires JDK 17+ and network access to **Google's Maven repository**
(`dl.google.com` / `maven.google.com`), which is where the Android Gradle Plugin
and every AndroidX library live. A restricted network fails at plugin
resolution with `Plugin [id: 'com.android.application'] was not found`, before
any code is compiled.

The debug build points at `http://10.0.2.2:8080/` — the emulator's route to the
host machine — so it works against a backend started with
`cd backend && ./mvnw spring-boot:run`. Cleartext is permitted for that host
only; see `network_security_config.xml`.

## What has actually been verified

Honesty matters more than optimism here, because the environment this was
written in could not reach Google's Maven and therefore **could not run a
single Gradle build**.

| Layer | State |
|---|---|
| `api-client/` | **Compiled.** 108 classes, generated from the committed spec and built with kotlinc against Retrofit, OkHttp, coroutines and kotlinx.serialization. |
| `data/` | **Compiled and tested**, except `EncryptedTokenStore` — 9 unit tests pass against the real generated client on a plain JVM. `EncryptedTokenStore` uses AndroidX Keystore APIs and could not be compiled here. |
| `ui/` | **Not compiled.** Compose needs the Compose compiler plugin and the Android SDK. Field and enum names were checked against the generated models by hand, and every `R.string` reference was checked against `strings.xml`, but the first `./gradlew` run is where this is really tested. |
| Sign-in | **Not compiled at all.** `data/GoogleSignIn.kt` and `ui/signin/` depend on Credential Manager and AndroidX Lifecycle, which live on Google's Maven. Nothing here has ever run. |
| Gradle setup | **Not resolved.** Plugin and library versions are pinned to known-compatible pairings (AGP 8.7.3 with Kotlin 2.1.21), but no build has confirmed them. |

Expect the first build to need small fixes in `ui/`. The data layer beneath it
is on firmer ground.

## Layout

```
app/src/main/kotlin/dev/dhuelin/watchguru/
├── data/        Repository, token storage, auth interceptor — no Android
│                imports, so it stays testable on a plain JVM
├── di/          Hilt modules
└── ui/
    ├── theme/       Material 3 theme; status colour/icon/label mapping
    ├── navigation/  Bottom-bar destinations and the NavHost
    ├── components/  Poster, empty/loading/error views, UiState
    ├── search/      Debounced search, add-to-library from a result row
    ├── library/     Status-filtered library with optimistic removal
    ├── detail/      Title detail and episode progress
    ├── home/        Up Next — placeholder, see below
    └── profile/     Profile
```

`data/` deliberately contains no Android types. That is what let the repository
and its error mapping be tested here at all, and it is the right place for the
logic regardless.

## Sign-in

Sign in with Google via Credential Manager. There is no registration step: the
backend provisions an account from the first valid ID token, so the screen has
one button.

To run it you need an **OAuth web client id** and an **Android client id** in
the same Google Cloud project:

1. Create the Android OAuth client with this app's package name
   (`dev.dhuelin.watchguru`, or `dev.dhuelin.watchguru.debug` for debug builds)
   and the SHA-1 of the signing certificate. Credential Manager will not return
   a token without it, but the app never references it directly.
2. Create the **web** OAuth client. Its id is what the app passes as
   `setServerClientId`, and it is the `aud` of the resulting ID token.
3. Pass it at build time:
   `-Pwatchguru.googleWebClientId=<id>.apps.googleusercontent.com` (see
   `gradle.properties`).
4. Put that same web client id in the backend's `WATCH_GURU_AUTH_AUDIENCES`,
   and `https://accounts.google.com` in `WATCH_GURU_AUTH_ISSUERS`. The backend
   refuses to start with either unset, by design — see `docs/AUTHENTICATION.md`.

Getting step 4 wrong gives a token the API correctly rejects with 401, which
looks like a sign-in failure but is not.

### Known limitation

The Google ID token is used directly as the bearer token and expires after
about an hour. There is no refresh, so a long-lived session eventually returns
401s; recovering means signing out and back in. Fixing that properly needs
a token-exchange endpoint on the backend, tracked as #26, rather than a
client-side retry that cannot succeed.

## Not built yet

- **Offline cache (#14), stats (#17), notifications (#19), widgets (#20).**

## Regenerating the API client

`api-client/src` is generated. Do not edit it:

```bash
./tools/generate-clients.sh    # from the repository root
```
