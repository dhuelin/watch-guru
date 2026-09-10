# Watch Guru for iOS

SwiftUI, iOS 17+, `@Observable`, structured concurrency with typed throws.
Talks to the Watch Guru API through the generated client in `WatchGuruAPI/`.

## Building

The Xcode project is generated rather than committed — a `.pbxproj` is a large
machine-managed file that merges badly and cannot be reviewed. `project.yml`
holds the same information in a form a human can read in a pull request.

```bash
brew install xcodegen
cd ios
xcodegen generate
open WatchGuru.xcodeproj
```

Requires macOS and Xcode 16+ (Swift 6). The debug build points at
`http://localhost:8080`, which is where the simulator reaches a backend started
with `cd backend && ./mvnw spring-boot:run`. A physical device will not resolve
`localhost` — that is the usual first surprise; point it at your machine's LAN
address.

## What has actually been verified

| | State |
|---|---|
| The app | **Builds.** `xcodebuild build` passes in CI — every source compiles, links, and packages into `WatchGuru.app`. |
| `WatchGuru/Tests/` | **Pass.** `xcodebuild test` runs them on a simulator. |
| `project.yml` | **Valid.** XcodeGen generates the project, and the generated Info.plist and entitlements are consumed by the build. |

That is a real green build, and it is not the same as a working app.

**Nobody has run it.** No screen has been looked at. Compiling proves the code
is well-formed; it says nothing about whether a list lays out, whether
navigation lands where it should, or how the offline queue behaves across a
real network transition.

**Sign in with Apple has never executed.** It needs an Apple Developer account
and a device, so the one path that gates every other screen is unproven beyond
compiling.

The first six builds found real defects that reading had missed: a generated
enum named `Status_listWatchlist` rather than `StatusListWatchlist`, an
initialiser whose argument order Swift enforces, `Session` not being
`@MainActor` under strict concurrency, a `Codable` envelope split across
`Encodable`/`Decodable` halves, a non-`Sendable` result crossing an actor
boundary, five closures whose thrown type could not be narrowed, `Tab` being
iOS 18 in an iOS 17 app, a missing `Info.plist`, an inert App Transport
Security key, and a pure function pinned to the main actor.

## Layout

```
WatchGuru/Sources/
├── Data/         API client wrapper, Keychain token store, failure mapping
├── Design/       Status styles, poster placeholder, shared state views
├── Navigation/   The tab shell
└── Features/
    ├── Search/    Debounced search, swipe to add
    ├── Library/   Status-filtered library, optimistic removal
    ├── Detail/    Title detail and episode progress
    ├── Home/      Up Next — placeholder, see below
    └── Profile/   Profile
```

Each feature is a `@MainActor @Observable` model plus a view. The model holds
the state and does the awaiting; the view renders it. `WatchGuruClient` is an
`actor` because the bearer token is mutable shared state that changes while
requests may be in flight.

## Deliberately not like the Android app

Both apps implement `docs/DESIGN.md`, which shares *meaning* and not pixels.
Where they differ, they differ on purpose:

- `NavigationStack` per tab, so each tab keeps its own back stack.
- Swipe actions for add and remove, rather than buttons on the row.
- `ContentUnavailableView` for empty and error states, so they inherit the
  system's layout and Dynamic Type behaviour.
- Semantic system colours, so dark mode and increased contrast come free —
  where Android uses Material 3 dynamic colour drawn from the wallpaper.

## Sign-in

Sign in with Apple, via `AuthenticationServices`. There is no registration
step: the backend provisions an account from the first valid identity token,
so the screen has one button.

To run it you need:

1. An Apple Developer account, with the **Sign in with Apple** capability
   enabled on the App ID. `project.yml` declares the entitlement; the
   capability has to exist on the developer portal side too.
2. The app's bundle id in the backend's `WATCH_GURU_AUTH_AUDIENCES` — that is
   what Apple puts in the token's `aud` — and `https://appleid.apple.com` in
   `WATCH_GURU_AUTH_ISSUERS`. The backend refuses to start with either unset,
   by design; see `docs/AUTHENTICATION.md`.

Two Apple-specific details the code depends on:

- The user's **name is returned only once**, in the first authorisation
  response, and never again in any token or later sign-in. `SignInModel` sends
  it to `PATCH /me` at that moment because there is no second chance.
- `email_verified` arrives as the **string** `"true"`, not a boolean.
  `CurrentUserService` on the backend accepts both.

Sign in with Google is not implemented on iOS; Android has it. Adding it means
`ASWebAuthenticationSession` or Google's SDK, and it is deliberately left until
Apple's flow has actually been run once.

### Known limitation

The identity token is used directly as the bearer token and expires in about an
hour. There is no refresh, so a long-lived session eventually returns 401s;
recovering means signing out and back in. The same limitation applies on
Android, and the fix belongs on the backend rather than in each app: #26.

## Working without a signal (#14)

Mirrors the Android design exactly, including the rules that matter: reads fall
back to a snapshot only when the failure was `offline`; writes fall back to a
persisted queue and report success, because queued *is* success; replay is
strictly in order and stops at the first offline failure; a 4xx is dropped as
permanent and a 5xx kept as transient; a later change to the same target
replaces the earlier one; and the watched-at timestamp travels with the
mutation so a replay does not file last night's viewing as this morning's.

Storage is JSON files rather than SwiftData or Core Data. Nothing here queries,
so a model container would buy schema migrations for no benefit. `OfflineStore`
is the seam to swap if that ever changes. Snapshots live in Caches, where the
system may reclaim them; the pending queue does not, because losing it loses
work the user believes is saved.

Those rules are **tested on Android and not here**, for the usual reason. The
Swift is a transliteration of logic that passes, which is evidence about the
design and none at all about this code.

## Not built yet

- **Stats (#17), notifications (#19), widgets (#20).**

## Regenerating the API client

`WatchGuruAPI/Sources` is generated. Do not edit it:

```bash
./tools/generate-clients.sh    # from the repository root
```
