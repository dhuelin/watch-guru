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

| Layer | State |
|---|---|
| The whole app | **Builds.** `./gradlew :app:assembleDebug` passes in CI — resources, KSP, Hilt aggregation, Kotlin, Java, dexing, packaging. |
| Unit tests | **Pass.** 39 of them, covering the session authenticator (rotation, refusal, no-loop, and eight threads racing to prove exactly one refresh), the offline queue's ordering and failure rules, and the repository's error mapping. |
| `api-client/` | **Compiled**, generated from the committed spec. |

That is a real green build, and it is not the same as a working app.

**Nobody has run it.** Not on a device, not on an emulator. Compiling proves
the code is well-formed, not that a screen lays out, that navigation goes where
it should, or that the offline queue behaves on a real network transition.

**Sign-in has never executed.** It needs a Google OAuth client that does not
exist yet, so the one path that gates every other screen is unproven beyond
compiling.

The first Gradle build found six real defects, in order: a `DayNight` theme
parent that does not exist in the platform, a missing launcher icon, Hilt 2.53
being incompatible with KSP2, five `retrofit.create()` calls missing the
reified extension import, a `FontFeature` import for a class that is not real,
and AGP putting JavaPoet 1.10 on a plugin classpath that needs 1.13. None of
those were findable by reading.

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
