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

The `deploy` job is a deliberate stub. The image is built, tested and
published; what is missing is a host. Wiring it up is one step:

```yaml
# Kubernetes
- run: kubectl set image deployment/watch-guru-api api=$IMAGE
# Fly.io
- run: flyctl deploy --image $IMAGE
# Cloud Run
- run: gcloud run deploy watch-guru --image $IMAGE
```

It runs against a GitHub `production` environment, so the deploy can require a
manual approval and the runtime secrets live on the environment rather than on
the repository.

### Required configuration

| Variable | Why |
|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Postgres. Flyway migrates on startup. |
| `TMDB_API_TOKEN` | Without it, search and title detail return 503. The user's own library still works. |
| `WATCH_GURU_AUTH_AUDIENCES` | **Required.** The OAuth client ids of the apps. Startup fails without it: an empty list would accept any token from Apple or Google, including one minted for a different application. |
| `WATCH_GURU_AUTH_SESSION_SECRET` | **Required.** At least 32 random bytes, the HMAC key for the access tokens this service signs. Startup fails without it. There is no generated fallback on purpose — one would appear to work, then sign every user out on each restart and reject its own tokens across instances behind a load balancer. Rotating it invalidates every live access token, which is the intended emergency lever; refresh tokens survive, so clients recover on their next refresh. |
| `WATCH_GURU_IMDB_ENABLED` | Off by default, for licensing reasons — see the roadmap. |

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

## What has been verified

The workflow YAML parses, and `backend/Dockerfile` was checked as far as this
environment allows — the build reached base-image resolution, so the multi-stage
structure and the cache mounts are syntactically sound, but no image was ever
built because Docker Hub is unreachable from here.

**No workflow has run.** The first push is their first execution, and the iOS
and Android jobs in particular are compiling sources that have never been near
a compiler.
