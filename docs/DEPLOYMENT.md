# Build, test and deployment

Five workflows. Each is path-filtered so a change to one component does not
spin up another's toolchain — which matters most for iOS, since macOS runner
minutes cost several times a Linux minute.

| Workflow | Trigger | Does |
|---|---|---|
| `backend.yml` | push/PR touching `backend/**` | Build, full test suite against a Testcontainers Postgres, OpenAPI drift check |
| `android.yml` | push/PR touching `android/**` | `assembleDebug` and unit tests |
| `ios.yml` | push/PR touching `ios/**` | XcodeGen, build and test on a simulator |
| `deploy-backend.yml` | push to `main`, published release | Test → build image → publish to GHCR → deploy |
| `release-android.yml` | published release | Signed App Bundle |

## Backend image

`backend/Dockerfile` is a multi-stage build:

- **Java 25**, matching what `pom.xml` targets.
- Dependencies resolved in a separate layer from sources, so a code-only change
  skips the download entirely.
- The fat jar is **unpacked into layers** (`-Djarmode=tools ... extract --layers`)
  so dependencies and application classes are separate image layers. Without
  this, changing one class re-pushes the whole ~70MB jar.
- Runs as an **unprivileged user**, writing nothing outside `/tmp`.
- `MaxRAMPercentage` rather than a fixed heap, so one image behaves sensibly on
  a 512MB and a 4GB allocation.

There is deliberately **no `HEALTHCHECK`**: the JRE image carries no curl or
wget, and installing one purely to call a URL the orchestrator can already
reach is not worth it. Point the platform's probe at `/actuator/health`, which
is exposed and unauthenticated.

Images are published to `ghcr.io/<owner>/watch-guru/backend`, tagged by branch,
by full commit SHA, and by semver on a published release. `latest` moves **only
on a release**, never on a push to main — otherwise "which image is running"
becomes unanswerable after the fact. Each push is attested with build
provenance, so an image can be traced to the commit and workflow that made it.

## Deploying

The target is **Fly.io**, configured in [`backend/fly.toml`](../backend/fly.toml).
The `deploy` job rolls out the image by digest — the exact one the tests in the
same run passed against, not a tag that could have moved since.

It runs against a GitHub `production` environment, so the deploy can require a
manual approval and the runtime secrets live on the environment rather than on
the repository. Until `FLY_API_TOKEN` is set there, the job reports what is
missing and succeeds, rather than turning every push to main red.

### Why the machine never sleeps

`fly.toml` sets `auto_stop_machines = false` and `min_machines_running = 1`,
which is not the cheap default. Three jobs run on cron inside this process —
the Trakt sync, the new-episode scan and the IMDb ratings import. A suspended
machine runs none of them, and the failure is invisible: the API answers
normally, because a request wakes it, while nothing has synced for days.

### First-time setup

Once, from a machine with [flyctl](https://fly.io/docs/flyctl/install/)
installed and `flyctl auth login` done.

**1. Create the app and its database.**

Fly has two Postgres offerings and they are not close in price. `fly postgres`
is the *unmanaged* one: a Postgres machine Fly provisions and then leaves to
you, at roughly the cost of the machine and its volume, with no support and no
backups you did not arrange. `fly mpg` is the managed one, with automatic
backups, failover and support, starting around ten times that. For a prototype
nobody is relying on yet, unmanaged is the honest choice — but it is a choice,
and the day somebody's viewing history is in there it should be revisited.

```bash
flyctl apps create watch-guru-api
flyctl postgres create --name watch-guru-db --region fra \
  --vm-size shared-cpu-1x --volume-size 10 --initial-cluster-size 1
flyctl postgres attach watch-guru-db --app watch-guru-api
```

`attach` sets `DATABASE_URL` on the app in libpq's URL form. Spring does not
read that variable — it wants `spring.datasource.url` in JDBC form with the
credentials separate — so `DatabaseUrlEnvironmentPostProcessor` translates it
at startup. Nothing further is needed, and an explicit `SPRING_DATASOURCE_URL`
still wins if you ever set one.

**2. Generate the two secrets the service will not start without.**

```bash
openssl rand -base64 48   # WATCH_GURU_AUTH_SESSION_SECRET
openssl rand -base64 32   # WATCH_GURU_CREDENTIALS_SECRET
```

Keep both. Losing the first signs everyone out; losing the second makes every
stored third-party token permanently unreadable.

**3. Set the secrets.**

```bash
flyctl secrets set --app watch-guru-api \
  WATCH_GURU_AUTH_SESSION_SECRET='...' \
  WATCH_GURU_CREDENTIALS_SECRET='...' \
  WATCH_GURU_AUTH_AUDIENCES='<google-web-client-id>' \
  TMDB_API_TOKEN='...'
```

Of those four, two are needed for the service to start at all —
`WATCH_GURU_AUTH_SESSION_SECRET` and `WATCH_GURU_AUTH_AUDIENCES`, both of
which fail closed rather than defaulting to something permissive.
`TMDB_API_TOKEN` is not: without it the service boots and answers, and every
catalogue lookup comes back empty. So it can be added afterwards, with one
command, whenever the key arrives:

```bash
flyctl secrets set --app watch-guru-api TMDB_API_TOKEN='eyJ...'
```

`WATCH_GURU_AUTH_AUDIENCES` is the one to get right. The **same** Google *web*
client id has to appear in three places or sign-in fails:

| Place | How it gets there |
|---|---|
| This secret | `flyctl secrets set` above |
| The Android build | `GOOGLE_WEB_CLIENT_ID` secret on the `android-release` environment |
| Google Cloud Console | The OAuth client it came from |

A mismatch produces a rejected token, not a helpful message. It is the single
most common way a first deployment fails.

**4. Make the image pullable.**

Fly pulls the image from GHCR itself, and a package is private even when its
repository is public. Open
`https://github.com/users/dhuelin/packages/container/watch-guru%2Fbackend/settings`
and change the visibility to public — once. Otherwise `flyctl deploy` fails on
an unauthorized pull, which reads like a Fly problem and is not one.

**5. Give CI a deploy token.**

```bash
flyctl tokens create deploy --app watch-guru-api --expiry 8760h
```

Put it on the repository's `production` environment as the secret
`FLY_API_TOKEN`. The next push to `main` that touches `backend/` deploys, or
run the workflow by hand with **Run workflow**.

**6. Point the apps at it.**

The Android release build bakes the API URL in, so set
`ANDROID_API_BASE_URL` on the `android-release` environment to
`https://watch-guru-api.fly.dev/` — with the trailing slash. The release
workflow refuses to build without it.

### Changing the hostname

`WATCH_GURU_PUBLIC_BASE_URL` in `fly.toml` must match wherever the service
actually answers. The Plex webhook URLs users paste into their servers and the
Trakt OAuth redirect are both built from it, and both fail silently when it is
wrong. If a custom domain goes in front, change it there, and change the
redirect URI registered with Trakt to match.

### If you would rather not use Fly

The image is a plain OCI image with no Fly-specific anything in it, and
`DATABASE_URL` is read the same way everywhere, so the alternatives are each
one step in place of the `Roll out` step:

```yaml
# Kubernetes
- run: kubectl set image deployment/watch-guru-api api=$IMAGE
# Cloud Run
- run: gcloud run deploy watch-guru --image $IMAGE
```

### Required configuration

| Variable | Why |
|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Postgres. Flyway migrates on startup. |
| `TMDB_API_TOKEN` | Without it, search and title detail return 503. The user's own library still works. |
| `WATCH_GURU_AUTH_AUDIENCES` | **Required.** The OAuth client ids of the apps. Startup fails without it: an empty list would accept any token from Apple or Google, including one minted for a different application. |
| `WATCH_GURU_AUTH_SESSION_SECRET` | **Required.** At least 32 random bytes, the HMAC key for the access tokens this service signs. Startup fails without it. There is no generated fallback on purpose — one would appear to work, then sign every user out on each restart and reject its own tokens across instances behind a load balancer. Rotating it invalidates every live access token, which is the intended emergency lever; refresh tokens survive, so clients recover on their next refresh. |
| `WATCH_GURU_IMDB_ENABLED` | Off by default, for licensing reasons — see the roadmap. |
| `WATCH_GURU_NOTIFICATIONS_ENABLED` | On by default, but nothing is delivered — see below. |
| `WATCH_GURU_PUSH_PROVIDER` | `log` is the only value that exists today. |
| `WATCH_GURU_TRAKT_CLIENT_ID` / `_SECRET` | A free [Trakt application](https://trakt.tv/oauth/applications), whose redirect URI must be `<public base url>/api/v1/streaming/trakt/callback`. Without both, connecting Trakt is refused up front rather than after the user has approved — see [`TRAKT.md`](TRAKT.md). |
| `WATCH_GURU_CREDENTIALS_SECRET` | Base64 of at least 32 random bytes (`openssl rand -base64 32`): the AES-GCM key that seals third-party access tokens. No generated fallback, for the same reason as the session secret — a key invented at boot would lose every connection on the next restart, and every user would have to reconnect without being told why. Only needed where Trakt is offered. |
| `WATCH_GURU_PUBLIC_BASE_URL` | Where this service is reachable from outside, e.g. `https://api.watch-guru.dev`. The media-server webhook URLs and the Trakt OAuth redirect are built from it. Unset, it is derived from the incoming request, which is right in development and wrong behind a proxy that does not forward the original host — and a webhook URL that is wrong in that way fails silently, because Plex reports nothing for a URL that resolves to nothing. |

### Webhook URLs are credentials, and they are in the path

None of the media servers sends headers of our choosing or offers a signing secret,
so the token that identifies a connection is in the URL path. Only its SHA-256
hash is stored, and holding one lets its bearer write watch events into exactly
one account and read nothing — but anything that logs full request URLs, a
proxy or CDN included, is logging a credential. Either scrub
`/api/v1/webhooks/` from access logs or treat them as sensitive. A user can
retire theirs at any time from the app, which is also the remedy if one leaks.

### Notifications deliver nothing yet

The new-episode scan is complete and running: it finds newly aired episodes of
series a user follows, respects their global switch, their per-series mutes,
their time zone and a daily cap, and records what it has announced so nothing
is announced twice. What it does not have is a way to reach a phone. APNs and
FCM both need credentials this project has never had, so the only sender that
ships logs the message and writes **NOT DELIVERED** in the line.

That is deliberate rather than unfinished-and-quiet: an empty implementation
that reported success silently would leave a deployment looking like it was
notifying people. Registering the credentials is tracked as a manual-setup
issue, and until it is done the honest description of this feature is that it
decides correctly and sends nothing.

## Android signing

`release-android.yml` reads the keystore from an `android-release` GitHub
environment, base64-encoded because secrets are text, and writes it outside the
workspace so no build step or artifact upload can pick it up. An environment
rather than repository secrets means a fork's pull request can never reach the
key.

| Secret | |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | |
| `ANDROID_KEY_ALIAS` | |
| `ANDROID_KEY_PASSWORD` | |

The workflow stops at uploading the `.aab` as an artifact. Publishing to Play
needs a service account and a created listing, and a half-configured publish
step that fails on every release is worse than an explicit gap.

## What has been verified, and what has not

Verified:

- The backend, Android and iOS workflows run on every pull request and pass.
  Both apps compile and their unit tests run.
- `DatabaseUrlEnvironmentPostProcessor` is covered by tests, including the
  escaped-password and `sslmode` cases a real host produces.
- `fly.toml` and every workflow parse.

Not verified, and worth knowing before trusting any of it:

- **Nothing has ever been deployed.** The `deploy` job has never reached Fly,
  because no `FLY_API_TOKEN` exists. The flyctl invocation, the digest
  hand-off from the publish job, and the health check's grace period are all
  first-run-untested.
- **No image has been built.** Docker Hub is unreachable from the environment
  these changes were written in, so `backend/Dockerfile` has only been checked
  as far as base-image resolution.
- **`release-android.yml` has never run.** No release has been published and
  no keystore exists.
- **Neither app has run on a device or a simulator.** That is
  [#35](https://github.com/dhuelin/watch-guru/issues/35), and it is also why
  there are no store screenshots.
