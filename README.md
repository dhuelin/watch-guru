# Watch Guru

A guide to what you have watched and where you left off, for films and TV series.

Watch Guru answers three questions the streaming apps themselves answer badly:
**what have I already seen**, **what episode am I on**, and **what should I
watch next**. Progress lives with the user, not with whichever service happened
to be playing the episode.

## Status

| Component | State |
|---|---|
| Backend (Spring Boot) | Phase 0 complete. Catalog, watchlist, episode progress, history, stats, streaming availability, OIDC authentication, published API contract, IMDb ratings enrichment. |
| iOS app (SwiftUI) | Not started — issue #7. |
| Android app (Jetpack Compose) | Not started — issue #8. |

See [`docs/ROADMAP.md`](docs/ROADMAP.md) for the phased plan and the GitHub
issues tracking each piece.

## Architecture

```
                       ┌──────────────┐   ┌──────────────────┐
                       │  iOS app     │   │  Android app     │
                       │  SwiftUI     │   │  Compose / M3    │
                       └──────┬───────┘   └────────┬─────────┘
                              │  HTTPS + OIDC JWT  │
                              └─────────┬──────────┘
                                        │
                              ┌─────────▼──────────┐
                              │  Watch Guru API    │
                              │  Spring Boot       │
                              │  Postgres + Flyway │
                              └─────────┬──────────┘
                                        │
                        ┌───────────────┼────────────────┐
                        │               │                │
                   ┌────▼─────┐   ┌─────▼──────┐   ┌─────▼──────┐
                   │  TMDB    │   │ IMDb       │   │ TMDB watch │
                   │  API     │   │ datasets   │   │ providers  │
                   │ metadata │   │ ratings    │   │ streaming  │
                   └──────────┘   └────────────┘   └────────────┘
```

The backend is the single source of truth. Both apps are thin over it: they own
presentation, navigation and an offline cache, but no business rules. Anything
that decides *what counts as watched* lives server-side so the two platforms
cannot drift.

### Why two native codebases

"Native style and design" is taken literally. iOS gets SwiftUI with real
navigation stacks, context menus, swipe actions, share sheets and WidgetKit;
Android gets Jetpack Compose with Material 3, predictive back, and Glance
widgets. The apps are expected to *look different from each other*, because
each looks correct on its own platform. The cost — every screen written twice —
is accepted deliberately; the backend absorbs the logic that would otherwise be
duplicated.

### Content sources

TMDB is the spine. It has the thing this app depends on most and IMDb does not
expose over any free API: **structured season and episode data**, plus
per-region streaming availability via watch providers.

IMDb contributes ratings. There is no free public IMDb REST API — the official
route is IMDb Essential Metadata on AWS Data Exchange (paid, enterprise
pricing), and the "IMDb APIs" on RapidAPI are unofficial scrapers. Instead,
`imdb_rating` and `imdb_vote_count` are filled from IMDb's published bulk
datasets on a scheduled job, joined on the `imdb_id` TMDB already returns.

> **Licensing caveat.** IMDb's public datasets are licensed for personal and
> non-commercial use. That is fine for a personal project, and it is a blocker
> for a paid or ad-supported release — commercialising Watch Guru means either
> licensing IMDb Essential Metadata or dropping the IMDb ratings column and
> showing TMDB ratings only. Decide this before the first paid release, not
> after. Tracked in the roadmap.

TMDB attribution ("This product uses the TMDB API but is not endorsed or
certified by TMDB") is required in-app by their terms.

## Repository layout

```
backend/    Spring Boot API — the source of truth
ios/        SwiftUI app (not yet scaffolded)
android/    Jetpack Compose app (not yet scaffolded)
docs/       Roadmap and design notes
```

## Backend layout

```
backend/src/main/java/com/dhuelin/dev/watchguru/
├── catalog/     Title, Season, Episode, Genre — metadata mirrored from TMDB
│                with TTL-based refresh (details 7d, availability 1d)
├── tracking/    AppUser, WatchlistItem, EpisodeWatch, WatchEvent, stats
├── streaming/   Streaming services, per-region availability, linked accounts
├── provider/    MetadataProvider abstraction, TMDB implementation, and the
│                retry / circuit-breaker layer every provider call runs through
├── imdb/        Ratings enrichment from IMDb's published bulk datasets
├── security/    OIDC resource server, token-to-user resolution
├── api/         REST controllers and DTOs
└── config/      Typed configuration properties
```

Three concerns kept apart because they change at different rates: catalog data
is a cache of somebody else's database, tracking data is the user's and must
never be lost, and streaming availability goes stale within a day.

### Data model notes

- `watch_event` is **append-only**. It is what answers "how much have I
  watched, when, on which service, was it a rewatch". `watchlist_item` and
  `episode_watch` hold only current state and can be rebuilt from it.
- `episode_watch` is one row per user/episode; a rewatch bumps `watch_count`
  rather than inserting a duplicate.
- `origin` / `origin_ref` on both watchlist items and watch events record where
  a record came from, and a partial unique index makes re-running an import
  idempotent.
- Linked streaming accounts store a `credential_ref` pointing at a secret
  manager entry — never the credentials themselves, so tokens stay out of the
  application database and its backups.

### API surface

All endpoints require an OIDC bearer token. `/api/v1/me/*` acts on the user the
token identifies; there is no way to name a different one.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/titles/search` | Combined film and series search |
| `GET` | `/api/v1/titles/{titleId}` | Title detail, seasons, episodes |
| `POST` | `/api/v1/titles/import` | Import a provider title into the catalog |
| `GET` | `/api/v1/me` | Own profile |
| `PATCH` | `/api/v1/me` | Display name, region, language, time zone |
| `DELETE` | `/api/v1/me` | Delete the account and everything in it |
| `GET` | `/api/v1/me/watchlist` | Library, filterable by status |
| `POST` | `/api/v1/me/watchlist` | Add a title |
| `PATCH` | `/api/v1/me/watchlist/{itemId}` | Status, rating, notes, favourite |
| `DELETE` | `/api/v1/me/watchlist/{itemId}` | Remove |
| `GET` | `/api/v1/me/watchlist/titles/{titleId}/progress` | Per-title progress |
| `POST` | `/api/v1/me/watch-events/movie` | Mark a film watched |
| `POST` | `/api/v1/me/watch-events/episode` | Mark an episode watched |
| `GET` | `/api/v1/me/history` | Viewing history |
| `GET` | `/api/v1/me/stats` | Totals and streaks |
| `GET` | `/api/v1/me/streaming-accounts` | Linked accounts |
| `GET` | `/api/v1/streaming-services` | Known services |

There is no endpoint for creating a user. An account comes into existence the
first time a valid token arrives, so there is nothing for a client to call.

The contract is published as OpenAPI at `/v3/api-docs` and committed to
`backend/src/main/resources/openapi/openapi.json`. **Both mobile clients are
generated from that file**, so it is the real interface between three
codebases. A test fails the build when it drifts from the code. After an
intentional API change, regenerate it:

```bash
cd backend && ./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.write=true
```

### Authentication

Sign in with Apple and Google, validated as an OIDC resource server. Four
things worth knowing before changing any of it:

- **Subjects are namespaced** as `<issuer>|<sub>`. Provider subjects are only
  unique within the issuer that minted them, and a collision means one user
  reading another's history.
- **Audience is validated.** Apple and Google issue tokens to any registered
  client, so a valid signature says nothing about who a token was *for*. Set
  `WATCH_GURU_AUTH_AUDIENCES` before exposing the API; without it, a token
  obtained by an unrelated app whose users also sign in with Google validates
  here perfectly.
- **Cross-provider account linking** requires a verified email *and* an issuer
  configured as trusted to assert verification (`trust-email-verification`,
  which defaults to false). Linking on email is an account-takeover primitive
  if a provider hands out addresses it never checked.
- **There is no bypass profile.** An empty issuer list fails startup rather
  than quietly serving unauthenticated traffic.

## Local development

Requirements: **JDK 25**, Docker, and a TMDB API read access token.

> Java 25 is the current LTS. The project previously targeted Java 26, a
> non-LTS release whose update window closes as 27 ships — which also meant it
> would not compile on any runner carrying an older JDK.

```bash
cd backend
export TMDB_API_TOKEN=<your TMDB v4 read access token>
./mvnw spring-boot:run
```

Authentication needs at least one reachable OIDC issuer. The defaults point at
Apple and Google; for local work against neither, point
`watch-guru.auth.issuers[0].uri` at a mock OIDC issuer.

Spring Boot's Docker Compose support starts Postgres from `compose.yaml` and
wires the datasource to its mapped port — no fixed host port, nothing to
configure. Flyway migrates on startup; Hibernate is `ddl-auto: validate`, so
the schema is owned by the migrations in
`src/main/resources/db/migration` and mapping drift fails fast at boot.

```bash
cd backend && ./mvnw test   # unit tests + Testcontainers integration tests (needs Docker)
```

Actuator health, info, metrics, flyway and caches endpoints are exposed under
`/actuator`. Provider health is under `watchguru.provider.call`, tagged by
operation and outcome; the search cache hit ratio is under `/actuator/caches`.

### Upstream resilience

Every TMDB call runs through one retry policy and one circuit breaker, so an
endpoint added later inherits both. Retries cover rate limiting, 5xx and
transport failures only — a 404 or a bad token is a real answer, and retrying
it spends the rate-limit budget arriving at the same place. `Retry-After` is
honoured but clamped, since TMDB can legitimately ask for ten minutes and
obeying that would pin a request thread for ten minutes.

Search results are cached in-memory for 60 seconds and queries shorter than two
characters never leave the building. Both exist because search-as-you-type from
two apps is what generates 429s. Title reads fall back to stale local metadata
when the provider is unreachable: the user's watchlist, progress and history are
local and entirely unaffected by TMDB being down.

### IMDb ratings

Disabled by default (`WATCH_GURU_IMDB_ENABLED`). When on, a nightly job streams
IMDb's published `title.ratings.tsv.gz` and fills `imdb_rating` /
`imdb_vote_count` for titles already in the catalog, joined on the `imdb_id`
TMDB provides. It is enrichment, never insertion.

## Contributing

Work is tracked in GitHub issues, grouped by `phase-0` / `phase-1` / `phase-2`
and by component (`backend`, `ios`, `android`). Start with the phase-0
foundations — the API contract and authentication need to settle before either
app is worth building against.
