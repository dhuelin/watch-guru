# Authentication

Sign in with Apple and Google, validated by the backend as an OIDC resource
server. The apps obtain an ID token from the platform and send it as a bearer
token; the backend verifies it and resolves it to an account.

## The flow

```
  App                          Apple / Google                Watch Guru API
   │                                 │                             │
   │  1. Sign in (platform SDK)      │                             │
   ├────────────────────────────────>│                             │
   │  2. ID token (JWT)              │                             │
   │<────────────────────────────────┤                             │
   │                                 │                             │
   │  3. Any request, Authorization: Bearer <ID token>             │
   ├──────────────────────────────────────────────────────────────>│
   │                                 │   4. Fetch JWKS (first use, │
   │                                 │<─────── then cached) ───────┤
   │  5. Response                    │                             │
   │<──────────────────────────────────────────────────────────────┤
```

There is no separate registration call and no token exchange. An account comes
into existence the first time a valid token arrives.

## What the backend checks

Every one of these must pass, and each exists for a reason worth stating:

| Check | Why |
|---|---|
| Signature, against the issuer's JWKS | The obvious one |
| `iss` is a configured trusted issuer | Otherwise a token could name a JWK set the attacker controls |
| `exp` | |
| **`aud` is one of ours** | Apple and Google issue tokens to *any* registered client. "Signed by Google" says nothing about who the token was for; without this, a token obtained by an unrelated app whose users also sign in with Google validates here perfectly |

The subject is stored as `<issuer>|<sub>`, not the bare `sub`. Provider subjects
are only unique within the issuer that minted them, and a collision would mean
one user reading another's history.

## Configuration you must supply

| Setting | Notes |
|---|---|
| `WATCH_GURU_AUTH_AUDIENCES` | Comma-separated OAuth client ids for the iOS and Android apps. **The API is not safe without this** — the server logs a warning at startup when it is unset |
| `watch-guru.auth.issuers` | Defaults to Apple and Google; both are configured as trusted to assert email verification |

There is deliberately no bypass profile. An empty issuer list fails startup
rather than quietly serving unauthenticated traffic. For local development,
point `watch-guru.auth.issuers[0].uri` at a mock OIDC issuer.

## Account linking, and why it is restrictive

Signing in with a second provider adopts an existing account **only** when both
of these hold:

1. The token asserts `email_verified`, and
2. the issuer is configured with `trust-email-verification: true`.

Linking on an email address is an account-takeover primitive. A provider that
hands out addresses it has not actually checked can be used to walk into any
account registered with that address. Apple and Google both verify, so both are
trusted; anything added later starts untrusted and someone has to decide,
deliberately, to change that.

Where linking is not permitted, the sign-in returns **409** rather than silently
creating a second account — to the user, a duplicate account is
indistinguishable from having lost everything they ever tracked.

## Two provider quirks that will bite

**Apple returns the user's name only once.** It appears in the initial
authorisation response and never in the token. Capture it at that moment and
`PATCH /api/v1/me` with it, or the account keeps a placeholder display name
forever. There is no way to ask Apple again.

**Apple's `email_verified` is sometimes the string `"true"`, not the boolean.**
The backend reads both. Reading only the boolean would silently break linking
for every Apple user.

Apple may also issue a private relay address. That is fine — it is stable per
user per app, and is treated like any other verified address.

## Client responsibilities

- Store the token in the Keychain (iOS) or Keystore-backed encrypted
  preferences (Android). Never `UserDefaults` or plain `SharedPreferences`.
- Send it on every request; read it per request rather than capturing once, so
  sign-out takes effect immediately.
- On 401, re-authenticate rather than showing an error.
- On sign-out, clear the token **and** the local cache. A shared device must not
  leak the previous user's watch history.
- Offer account deletion (`DELETE /api/v1/me`) — both app stores require it for
  any app that supports account creation, and it is a real cascading delete.

## Status

The backend half is done and tested: 401 without a token on every route, and
expired, wrong-key and wrong-audience tokens all rejected, verified against a
mock issuer with a controlled signing key.

The client half is **written but never compiled or run**. iOS signs in with
Apple (`SignInWithAppleButton` plus the entitlement in `project.yml`); Android
signs in with Google through Credential Manager. Both gate the whole app on
having a token, and both offer sign-out and account deletion.

It is unverified for a concrete reason rather than a vague one: Sign in with
Apple needs an Apple Developer account and a Mac, and Credential Manager needs
AndroidX, which lives on Google's Maven — none of which was reachable where
this was written.

### What you must set up before it can work

| | |
|---|---|
| Apple | Enable **Sign in with Apple** on the App ID. Put the bundle id (`dev.dhuelin.watchguru`) in `WATCH_GURU_AUTH_AUDIENCES` and `https://appleid.apple.com` in `WATCH_GURU_AUTH_ISSUERS`. |
| Google | Create **two** OAuth clients in one project: an Android client (package name + signing SHA-1, never referenced in code but required for Credential Manager to return anything) and a **web** client. Build with `-Pwatchguru.googleWebClientId=<web id>`, and put that same web id in `WATCH_GURU_AUTH_AUDIENCES`, with `https://accounts.google.com` in `WATCH_GURU_AUTH_ISSUERS`. |

The backend refuses to start with either variable unset. That is deliberate: an
empty audience list would otherwise accept any token from a trusted issuer,
including one minted for a different application.

### Refresh is not implemented

Both apps use the provider's ID token directly as the bearer token, and those
expire in about an hour. There is no refresh, so a long-lived session ends in
401s and the user has to sign in again. `## Client responsibilities` above says
"on 401, re-authenticate" — today that means the user does it manually from the
profile screen. Doing it properly means a token-exchange endpoint here that
issues a session token of our own, which is a backend change rather than an app
one. Tracked as #26.
