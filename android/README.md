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
| `ui/` | **Not compiled.** Compose needs the Compose compiler plugin and the Android SDK. Field and enum names were checked against the generated models by hand, but the first `./gradlew` run is where this is really tested. |
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

## Not built yet

- **Sign-in (#15).** The app expects a token to already be in the token store,
  so every call returns 401 until Sign in with Google and Apple land. This is
  the next thing to do.
- **Unmarking an episode.** There is no endpoint for it, so the control is
  disabled in that direction rather than shown as something that silently does
  nothing.
- **Offline cache (#14), stats (#17), notifications (#19), widgets (#20).**

## Regenerating the API client

`api-client/src` is generated. Do not edit it:

```bash
./tools/generate-clients.sh    # from the repository root
```
