# Watch Guru

A guide to what you have watched and where you left off, for films and TV series.

Watch Guru answers three questions the streaming apps themselves answer badly:
**what have I already seen**, **what episode am I on**, and **what should I
watch next**. Progress lives with the user, not with whichever service happened
to be playing the episode.

## Status

| Component | State |
|---|---|
| Backend (Spring Boot) | Working. Catalog, watchlist, episode progress, history, stats, streaming availability. No authentication yet. |
| iOS app (SwiftUI) | Not started. |
| Android app (Jetpack Compose) | Not started. |

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

## Backend layout

```
src/main/java/com/dhuelin/dev/watchguru/
├── catalog/     Title, Season, Episode, Genre — metadata mirrored from TMDB
│                with TTL-based refresh (details 7d, availability 1d)
├── tracking/    AppUser, WatchlistItem, EpisodeWatch, WatchEvent, stats
├── streaming/   Streaming services, per-region availability, linked accounts
├── provider/    MetadataProvider abstraction + TMDB implementation
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

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/titles/search` | Combined film and series search |
| `GET` | `/api/titles/{titleId}` | Title detail, seasons, episodes |
| `POST` | `/api/users` | Create user |
| `GET` | `/api/users/{userId}` | User profile |
| `GET` | `/api/users/{userId}/watchlist` | Library, filterable by status |
| `POST` | `/api/users/{userId}/watchlist` | Add a title |
| `PATCH` | `/api/users/{userId}/watchlist/{itemId}` | Status, rating, notes, favourite |
| `DELETE` | `/api/users/{userId}/watchlist/{itemId}` | Remove |
| `GET` | `/api/users/{userId}/watchlist/titles/{titleId}/progress` | Per-title progress |
| `POST` | `/api/users/{userId}/watch-events/movie` | Mark a film watched |
| `POST` | `/api/users/{userId}/watch-events/episode` | Mark an episode watched |
| `GET` | `/api/users/{userId}/history` | Viewing history |
| `GET` | `/api/users/{userId}/stats` | Totals and streaks |
| `GET` | `/api/streaming-services` | Known services |
| `GET` | `/api/users/{userId}/streaming-accounts` | Linked accounts |

> **`userId` in the path is temporary.** There is no authentication yet, so
> every endpoint trusts a caller-supplied user id. This is a prototype-only
> arrangement and blocks any public release. The replacement is OIDC — Sign in
> with Apple and Google — resolving the user from a validated token into the
> `auth_subject` column the schema already reserves.

## Local development

Requirements: **JDK 26**, Docker, and a TMDB API read access token.

> The pom targets Java 26 and Spring Boot 4.1.1. Build environments on older
> JDKs will not compile it; pin a toolchain or lower `java.version`.

```bash
export TMDB_API_TOKEN=<your TMDB v4 read access token>
./mvnw spring-boot:run
```

Spring Boot's Docker Compose support starts Postgres from `compose.yaml` and
wires the datasource to its mapped port — no fixed host port, nothing to
configure. Flyway migrates on startup; Hibernate is `ddl-auto: validate`, so
the schema is owned by the migrations in
`src/main/resources/db/migration` and mapping drift fails fast at boot.

```bash
./mvnw test        # unit tests + Testcontainers integration tests (needs Docker)
```

Actuator health, info, metrics and flyway endpoints are exposed under
`/actuator`.

## Contributing

Work is tracked in GitHub issues, grouped by `phase-0` / `phase-1` / `phase-2`
and by component (`backend`, `ios`, `android`). Start with the phase-0
foundations — the API contract and authentication need to settle before either
app is worth building against.
