# Roadmap

Three phases. Phase 0 makes the backend something two apps can safely be built
against; Phase 1 is a shippable app; Phase 2 is what makes it worth keeping on
a home screen.

The ordering is not arbitrary. Phase 0 exists because the two most expensive
mistakes available here are (a) building two apps against an API contract that
then changes, and (b) shipping an app whose authentication was retrofitted.

---

## Phase 0 — Foundations

Backend work that has to settle before either app is worth writing. Nothing
here is user-visible; all of it is expensive to change afterwards.

| # | Issue | Why it is in Phase 0 |
|---|---|---|
| [#1](https://github.com/dhuelin/watch-guru/issues/1) | OIDC authentication (Sign in with Apple + Google) | Every endpoint currently trusts a `userId` in the URL. Retrofitting auth after two apps depend on the URL shape means changing both. |
| [#2](https://github.com/dhuelin/watch-guru/issues/2) | OpenAPI spec and generated clients | Two codebases would otherwise hand-transcribe the API twice and drift. |
| [#3](https://github.com/dhuelin/watch-guru/issues/3) | IMDb ratings import from bulk datasets | The columns exist and are empty. Needed before any screen promises IMDb ratings. |
| [#4](https://github.com/dhuelin/watch-guru/issues/4) | Restructure into `backend/`, `ios/`, `android/` | Moving an Xcode project later is far worse than moving a Maven one now. |
| [#5](https://github.com/dhuelin/watch-guru/issues/5) | CI and a pinned JDK toolchain | Nothing currently proves the build works anywhere but one machine. |
| [#6](https://github.com/dhuelin/watch-guru/issues/6) | TMDB rate limiting, retries, failure isolation | Two apps with search boxes will hit TMDB's rate limits on day one. |

**Done when:** a signed-in client can be generated from a committed spec, CI is
green, and the backend degrades gracefully when TMDB is down.

---

## Phase 1 — A shippable app

The smallest thing that genuinely answers *what did I watch and where am I*.
Both platforms, feature-equivalent, each looking correct on its own platform.

| # | Issue | Notes |
|---|---|---|
| [#7](https://github.com/dhuelin/watch-guru/issues/7) | iOS scaffold (SwiftUI) | Tab shell, networking, previews |
| [#8](https://github.com/dhuelin/watch-guru/issues/8) | Android scaffold (Compose) | Bottom nav, networking, DI |
| [#16](https://github.com/dhuelin/watch-guru/issues/16) | Design system | Shared semantics, two native expressions. Start early — it constrains every screen. |
| [#15](https://github.com/dhuelin/watch-guru/issues/15) | Sign-in and account management | Client half of #1 |
| [#9](https://github.com/dhuelin/watch-guru/issues/9) | Search and discovery | How anything enters the library |
| [#10](https://github.com/dhuelin/watch-guru/issues/10) | Title detail screen | The hub |
| [#11](https://github.com/dhuelin/watch-guru/issues/11) | Library screen | The screen users open most |
| [#12](https://github.com/dhuelin/watch-guru/issues/12) | **Episode progress tracking** | The reason the app exists |
| [#13](https://github.com/dhuelin/watch-guru/issues/13) | Home: Up Next | Answers "what do I put on now" |
| [#14](https://github.com/dhuelin/watch-guru/issues/14) | Offline cache and sync | An app that loses a watch record has failed at its job |

**Done when:** a user can sign in, find a series, track it episode by episode,
see where they left off, and do all of that on a train with no signal.

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
| [#19](https://github.com/dhuelin/watch-guru/issues/19) | New-episode notifications | The strongest reason to reopen the app |
| [#21](https://github.com/dhuelin/watch-guru/issues/21) | Import from Trakt, CSV, Netflix | Nobody starts from zero; an empty library is why tracking apps get deleted on day one |
| [#20](https://github.com/dhuelin/watch-guru/issues/20) | Home screen widgets | Next episode, markable, without opening the app |
| [#17](https://github.com/dhuelin/watch-guru/issues/17) | Stats and streaks | Already computed server-side, never rendered |
| [#22](https://github.com/dhuelin/watch-guru/issues/22) | History timeline and editing | Where mistakes get corrected, and backdated entries get logged |
| [#23](https://github.com/dhuelin/watch-guru/issues/23) | Accessibility and localisation | Filed separately because that is the only way it does not get skipped |
| [#24](https://github.com/dhuelin/watch-guru/issues/24) | IMDb licensing decision | Cheap to settle now, a compliance problem after launch |

---

## Known constraints, carried forward

**No authentication yet.** Every endpoint takes a caller-supplied `userId`.
Prototype-only, blocks any release. [#1](https://github.com/dhuelin/watch-guru/issues/1)

**JDK 26.** `pom.xml` targets Java 26 with Spring Boot 4.1.1, so older JDKs
cannot compile the project at all. Pin the toolchain or lower the target.
[#5](https://github.com/dhuelin/watch-guru/issues/5)

**IMDb datasets are non-commercial.** Fine for a free app, a blocker for a paid
one, and a one-flag change if caught early. [#24](https://github.com/dhuelin/watch-guru/issues/24)

**TMDB attribution is required in-app** by TMDB's terms of use.

## Explicitly not planned

- **A shared cross-platform UI layer.** The point of two native codebases is
  two native apps; a shared component abstraction would undo that.
- **Scraping streaming services with stored user credentials.** The schema
  supports linked accounts structurally, but holding people's Netflix passwords
  is a liability with no good version. Official APIs and user-initiated exports
  only.
- **Video playback.** Watch Guru tracks and points at; it does not play.
