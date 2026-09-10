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
| `data/` | **Compiled and tested**, except `EncryptedTokenStore`, `CoilDataCleaner` and `AndroidFileStore` — 39 unit tests pass against the real generated client on a plain JVM, including the session authenticator (rotation, refusal, no-loop, and eight threads racing to prove one refresh). Those three touch AndroidX Keystore, Coil and `Context.filesDir` and could not be compiled here. |
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

`data/` is almost free of Android types — the exceptions are `EncryptedTokenStore`
and `CoilDataCleaner`, which exist to *be* the Android-specific half behind an
interface (`TokenStore`, `LocalDataCleaner`). Everything else, the repository
and its error mapping in particular, stays plain Kotlin, which is what let it be
tested here at all.

Signing out clears the token synchronously and then wipes Coil's image caches
off the main thread. That second part is not cosmetic: on a shared device the
cached poster art is a list of what the previous user was watching. API
responses need no equivalent — the OkHttp client is built without a cache.

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

### How the session is kept alive

The Google ID token is never stored. It is exchanged at
`POST /api/v1/auth/session` for an access token (15 minutes) and a refresh
token (30 days), and `SessionAuthenticator` renews on the server's 401 — not on
a clock, because a device whose clock is wrong would otherwise renew constantly
or never.

Refresh tokens **rotate**: each renewal invalidates the one it presented, and
the backend treats a token presented twice as theft and revokes the whole
session. Two calls that 401 at the same moment must therefore cause one
renewal, not two. That is what the lock in `SessionAuthenticator` is for, and
what the eight-thread test actually checks — get it wrong and the user is
signed out precisely when the app is busiest, which is also when it is hardest
to reproduce.

A refused renewal is final (revoked, expired, or detected as reused), so it
clears the tokens and pushes the app back to the sign-in screen through
`SessionEvents` rather than letting every screen 401 in silence.

## Working without a signal (#14)

Reads go to the network **first** and fall back to the last snapshot. Never the
other way round — showing a stale library to someone with a working connection
would be a bug, not a feature. A snapshot is only substituted when the failure
was `Offline`: an upstream error means the backend is up and answering, so its
answer, including an empty one, is the truth.

Writes go to the network first and fall back to a persisted queue, and the
caller is told it worked — because it did. Marking an episode on a train has to
feel certain.

The queue's rules are where the bugs would be, so they are the tested part:

| Rule | What goes wrong without it |
|---|---|
| Replay strictly in order | An unmark overtaking the mark it was meant to undo leaves the episode watched — silently, days later, on a device nobody is watching |
| Stop at the first offline failure | Skipping ahead reorders everything behind it |
| A rejection (4xx) is permanent — drop it | Keeping it blocks every later change for ever |
| A server error (5xx) is transient — keep it | The user's work is thrown away because the server had a bad minute |
| A later change to the same target replaces the earlier one | Toggling one episode forty times queues forty entries |
| An expired session stops the drain | Otherwise it collects a 401 per entry and achieves nothing |

The stored timestamp travels with the mutation. Replaying with "now" would file
three episodes watched last night as watched the moment the train reached
signal, which is wrong on the history screen and wrong in the streak.

Storage is a JSON file, not Room. Nothing here queries — the app loads a few
hundred rows and filters in memory — so Room would buy code generation and
schema migrations for no benefit, and would put all of this logic behind an
Android dependency instead of under test. `FileStore` is the seam to swap if the
library ever outgrows loading it whole.

## Not built yet

- **Stats (#17), notifications (#19), widgets (#20).**

## Regenerating the API client

`api-client/src` is generated. Do not edit it:

```bash
./tools/generate-clients.sh    # from the repository root
```
