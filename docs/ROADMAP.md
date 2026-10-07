# Roadmap

Three phases. Phase 0 makes the backend something two apps can safely be built
against; Phase 1 is a shippable app; Phase 2 is what makes it worth keeping on
a home screen.

The ordering is not arbitrary. Phase 0 exists because the two most expensive
mistakes available here are (a) building two apps against an API contract that
then changes, and (b) shipping an app whose authentication was retrofitted.

---

## Phase 0 — Foundations ✅ complete

Backend work that had to settle before either app was worth writing. Nothing
here is user-visible; all of it is expensive to change afterwards.

| # | Issue | Why it is in Phase 0 |
|---|---|---|
| [#1](https://github.com/dhuelin/watch-guru/issues/1) | OIDC authentication (Sign in with Apple + Google) | Every endpoint currently trusts a `userId` in the URL. Retrofitting auth after two apps depend on the URL shape means changing both. |
| [#2](https://github.com/dhuelin/watch-guru/issues/2) | OpenAPI spec and generated clients | Two codebases would otherwise hand-transcribe the API twice and drift. |
| [#3](https://github.com/dhuelin/watch-guru/issues/3) | IMDb ratings import from bulk datasets | The columns exist and are empty. Needed before any screen promises IMDb ratings. |
| [#4](https://github.com/dhuelin/watch-guru/issues/4) | Restructure into `backend/`, `ios/`, `android/` | Moving an Xcode project later is far worse than moving a Maven one now. |
| [#5](https://github.com/dhuelin/watch-guru/issues/5) | CI and a pinned JDK toolchain | Nothing currently proves the build works anywhere but one machine. |
| [#6](https://github.com/dhuelin/watch-guru/issues/6) | TMDB rate limiting, retries, failure isolation | Two apps with search boxes will hit TMDB's rate limits on day one. |

**Done.** A signed-in client can be generated from the committed spec, CI runs
build and tests on every PR, and the backend serves stale local data rather
than failing when TMDB is unreachable. **105 tests passing**, including the
full integration suite against a real PostgreSQL: all four migrations apply,
Hibernate's `validate` confirms every entity matches them, and episode
progress, rewatch handling, stats aggregation and the IMDb batch UPDATE are
exercised end to end.

The running service was also driven directly: every `/api/v1` route returns
401 without a token; a token signed by a controlled test issuer provisions an
account and returns it; and tokens that are expired, signed with the wrong
key, or carry a **different application's audience** are all rejected. That
last one is the bug described below, confirmed fixed rather than assumed.

One thing remains unverified:

- **The IMDb download path.** `datasets.imdbws.com` is blocked from the
  environment this was built in. Parsing and batching are covered against
  fixtures, and the batch UPDATE runs against real Postgres, but the HTTP fetch
  and the `If-Modified-Since` handling are written to the documented format and
  want one real run before being trusted. The job is off by default, so this
  costs nothing until somebody turns it on.

Verifying this turned up two bugs worth recording, both of the kind that look
fine in development. The audience validator was silently inert: 
`JwtIssuerAuthenticationManagerResolver.fromTrustedIssuers` builds its own
decoders internally and discards a configured validator, so any token from
Apple or Google would have been accepted, including one minted for an
unrelated application. And: `NimbusJwtDecoder.withIssuerLocation(...).build()`
fetches the provider's discovery document *while building the decoder*, so the
application could not start unless Apple and Google were both reachable. That
looks fine on a laptop and fails in a restricted network or during a provider
outage. Decoder construction is now deferred to the first token that needs
verifying.

---

## Phase 1 — A shippable app

The smallest thing that genuinely answers *what did I watch and where am I*.
Both platforms, feature-equivalent, each looking correct on its own platform.

| # | Issue | Notes |
|---|---|---|
| # | Issue | State |
|---|---|---|
| [#16](https://github.com/dhuelin/watch-guru/issues/16) | Design system | ✅ `docs/DESIGN.md` |
| [#8](https://github.com/dhuelin/watch-guru/issues/8) | Android scaffold (Compose) | ✅ Shell, DI, data layer, screens |
| [#7](https://github.com/dhuelin/watch-guru/issues/7) | iOS scaffold (SwiftUI) | ✅ Shell, client, screens |
| [#9](https://github.com/dhuelin/watch-guru/issues/9) | Search and discovery | ✅ Both platforms, debounced, add from a row |
| [#10](https://github.com/dhuelin/watch-guru/issues/10) | Title detail screen | ✅ Both platforms |
| [#11](https://github.com/dhuelin/watch-guru/issues/11) | Library screen | ✅ Status filter, optimistic removal. No progress bars — see below |
| [#12](https://github.com/dhuelin/watch-guru/issues/12) | **Episode progress tracking** | ✅ Season/episode list, per-episode marking and unmarking, *mark all up to here* |
| [#13](https://github.com/dhuelin/watch-guru/issues/13) | Home: Up Next | ✅ Both platforms, over `GET /me/up-next` |
| [#25](https://github.com/dhuelin/watch-guru/issues/25) | Backend: episodes, progress, up-next | ✅ Closed the gap that blocked #12 and #13 |
| [#15](https://github.com/dhuelin/watch-guru/issues/15) | Sign-in and account management | 🟡 Written on both platforms — Apple on iOS, Google on Android, plus sign-out and account deletion. **Never compiled or run:** it needs an Apple Developer account and a Google OAuth client, neither of which existed here |
| [#26](https://github.com/dhuelin/watch-guru/issues/26) | Session tokens (exchange + refresh) | ✅ Backend and both apps. Rotating refresh tokens with family reuse detection; renewal on the server's 401, serialised so a rotating token is never spent twice. Android's half is unit-tested; iOS's is not compiled |
| [#14](https://github.com/dhuelin/watch-guru/issues/14) | Offline cache and sync | ✅ Snapshot cache for library and Up Next, plus a persisted mutation queue replayed in order. Android's logic is unit-tested; iOS's mirrors it and is not compiled |

**Done when:** a user can sign in, find a series, track it episode by episode,
see where they left off, and do all of that on a train with no signal.

### What the mobile work could not verify

**Superseded, as of PR #27.** Both apps now build and pass their tests in CI,
which the environment they were written in could not do. What follows describes
the constraint they were written under; the remaining gap is that neither app
has been *run* by anyone, and neither sign-in flow has ever executed.



The apps were built in an environment that could reach neither Google's Maven
nor a Swift toolchain, so **no Gradle build and no Xcode build has ever run**.
What was verified, and what was not:

| | Verified |
|---|---|
| Generated Kotlin client | ✅ Compiles — 108 classes |
| Android `data/` layer | ✅ Compiles, 9 unit tests passing on a plain JVM |
| Android `ui/` | ❌ Compose needs the Android SDK |
| Android Gradle setup | ❌ AGP will not resolve |
| Everything iOS | ❌ No macOS, no Xcode, no Swift compiler |

The Android data layer is on firm ground because it deliberately contains no
Android imports. Everything else should be expected to need fixes on first
build, and each app's README says so.

### A backend gap that building the apps exposed — since closed

Writing the screens turned up something bigger than expected: **the API exposed
no seasons or episodes at all.** The tables were populated and
`POST /watch-events/episode` took an `episodeId`, but nothing told a client
which ids existed. The apps could mark "the next episode" only because the
progress endpoint happened to leak one id — so #12, the feature the product is
judged on, was unbuildable.

Five endpoints closed it, all server-side because they are query shape or
product logic rather than presentation:

| Endpoint | Why it belongs on the server |
|---|---|
| `GET /titles/{id}/seasons` | Episodes with watched state, four queries whatever the series length |
| `POST /me/watch-events/episodes/up-to` | "Mark all up to here", idempotent, so a double tap cannot create rewatches |
| `DELETE /me/watch-events/episodes/{id}` | Unmarking, deleting the history too so derived state cannot drift |
| `DELETE /me/watch-events/{id}` | Removing one entry of several rewatches |
| `GET /me/up-next` | "Next" is product logic — implemented twice, the apps would disagree |

Progress also moved into the watchlist response, gathered per page in one
grouped query, which is what let the library progress bars exist at all.

### Start here

[#7](https://github.com/dhuelin/watch-guru/issues/7) and
[#8](https://github.com/dhuelin/watch-guru/issues/8), the two scaffolds, then
[#16](https://github.com/dhuelin/watch-guru/issues/16) early — the design
system constrains every screen after it, and retrofitting it is worse than
starting with it. The API they build against is already published and stable.

### The one screen that has to be perfect

[#12](https://github.com/dhuelin/watch-guru/issues/12), and specifically
**mark all up to here**. Everything else is a list of things; this is the
interaction the product is judged on. If recording an episode takes more than
one tap, or takes a moment to feel confirmed, people stop doing it and the
library rots.

---

## Phase 2 — Worth keeping installed

| # | Issue | Why it matters |
|---|---|---|
| [#18](https://github.com/dhuelin/watch-guru/issues/18) | Where to watch, and deep links | Turns a ledger into a guide. The backend data already exists and is unused. |
| [#19](https://github.com/dhuelin/watch-guru/issues/19) | New-episode notifications | The strongest reason to reopen the app. Backend and both apps are built; nothing is delivered until the push credentials in [#37](https://github.com/dhuelin/watch-guru/issues/37) exist, see below |
| [#21](https://github.com/dhuelin/watch-guru/issues/21) | Import from Trakt, CSV, Netflix | Nobody starts from zero; an empty library is why tracking apps get deleted on day one |
| [#20](https://github.com/dhuelin/watch-guru/issues/20) | Home screen widgets | Next episode, markable, without opening the app. Done on both platforms; the poster is not there yet, see below |
| [#17](https://github.com/dhuelin/watch-guru/issues/17) | Stats and streaks | Already computed server-side, never rendered |
| [#22](https://github.com/dhuelin/watch-guru/issues/22) | History timeline and editing | Where mistakes get corrected, and backdated entries get logged. Done: filters, search, date range, editing, and logging a film for any past date |
| [#23](https://github.com/dhuelin/watch-guru/issues/23) | Accessibility and localisation | Filed separately because that is the only way it does not get skipped |
| [#24](https://github.com/dhuelin/watch-guru/issues/24) | IMDb licensing decision | Cheap to settle now, a compliance problem after launch |
| [#39](https://github.com/dhuelin/watch-guru/issues/39) | Connect Plex, Jellyfin, Emby and Trakt | Watching something should record it without anybody marking anything. Plex, Jellyfin, Emby and Trakt are connected; see [`MEDIA-SERVERS.md`](MEDIA-SERVERS.md) and [`TRAKT.md`](TRAKT.md) |

---

## Notifications are built and cannot yet deliver

Everything about #19 exists except the thing only an account can provide.

The backend scans for new episodes, honours quiet hours in the user's own time
zone, records deliveries, and exposes a global switch and one per series. Both
apps ask for permission, register the device, show the switches, and forget the
device on sign-out — before clearing the token, because that call needs the
session it is ending. On a shared phone that is the difference between the next
user being notified about their own series and about the last user's.

What is missing is a credential on each platform, and they fail differently:

- **iOS** gets a real APNs token the moment the App ID has the Push
  Notifications capability and the profile is reissued (#30). Until then
  `registerForRemoteNotifications` fails every launch, so `PushTokens` keeps the
  system's reason and the settings screen shows it.
- **Android** cannot get a token at all yet. FCM needs a `google-services.json`
  from a Firebase project that does not exist (#37), and the Gradle plugin fails
  the build outright when that file is absent. So `PushTokens` is an interface
  with one implementation that returns null, and `NotificationModule` is the
  single binding to change when the project exists.

Returning null is deliberate rather than lazy. A plausible fake token would be
accepted by the backend, counted as a registered device, and then silently
deliver nothing — so the settings screen would claim notifications were working
while no notification could possibly arrive. Null makes the screen say the true
thing: this device is not registered, and nothing can reach it.

That is why the screen reports *which* of the three prerequisites is missing
rather than only showing switches. Permission, a token, and the backend knowing
that token fail independently, and a screen that showed switches alone would
leave somebody toggling a setting that could never do anything. The ordering is
tested, because reporting "not registered" to somebody who has not been asked
for permission sends them looking in the wrong place.

---

## Known constraints, carried forward

**The API refuses to start without `WATCH_GURU_AUTH_AUDIENCES` and
`WATCH_GURU_AUTH_SESSION_SECRET`.** Deliberate: without an audience list, any
token from Apple or Google validates, including one minted for a completely
different application. Both were briefly warn-and-continue, which is the state
this now prevents.

**IMDb datasets are non-commercial.** Fine for a free app, a blocker for a paid
one. The import is behind a flag specifically so this stays a configuration
change. [#24](https://github.com/dhuelin/watch-guru/issues/24)

**TMDB attribution is required in-app** by TMDB's terms of use. It is already in
the OpenAPI description; it still has to appear in both apps.

**The search cache is per-instance.** Behind several replicas the hit rate falls
but nothing breaks. A shared cache can be introduced later without changing any
calling code.

## Explicitly not planned

- **A shared cross-platform UI layer.** The point of two native codebases is
  two native apps; a shared component abstraction would undo that.
- **Scraping streaming services with stored user credentials.** The schema
  supports linked accounts structurally, but holding people's Netflix passwords
  is a liability with no good version. Official APIs and user-initiated exports
  only.
- **Video playback.** Watch Guru tracks and points at; it does not play.

---

## The widget, and the one thing it does not show

[#20](https://github.com/dhuelin/watch-guru/issues/20) ships on both platforms:
next episode, series, code and progress, at every size, markable from the home
screen without launching anything, with a prompt rather than an error while
signed out.

**No artwork yet.** Neither framework can render a URL. Glance needs a decoded
`Bitmap`; WidgetKit needs the image already in the timeline entry, because
`AsyncImage` does not load in a widget. So a poster means fetching, decoding and
caching inside two extensions that are killed rather than warned when they exceed
their memory budget — a change of its own, not a line in this one. The series,
the episode and the progress are what the widget is *for*, and they are there.

**How each platform gets its data differs, and it is not drift.** On Android a
Glance widget runs inside the app's own process, so it uses the very repository
the screens use. On iOS a widget is a separate process that can read neither the
app's memory nor its container, so the app writes a small feed into a shared App
Group and the widget draws that. Giving iOS the Android design would mean linking
the whole generated API client into an extension to read six fields; giving
Android the iOS design would mean maintaining a feed file for no reason.

**Marking, when the session has expired.** The iOS widget deliberately cannot
refresh a session — that is a second endpoint, a rotating refresh token, and a
Keychain write racing the app. Instead the mark joins the queue the app already
replays. It is safe to send it twice because every mark carries a `clientRef` and
the server treats a repeat as the same event
([#21](https://github.com/dhuelin/watch-guru/issues/21)); without that this
design would turn a flaky connection into rewatches.
