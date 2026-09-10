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
   │  3. POST /api/v1/auth/session { providerToken }               │
   ├──────────────────────────────────────────────────────────────>│
   │                                 │   4. Fetch JWKS (first use, │
   │                                 │<─────── then cached) ───────┤
   │  5. { accessToken, refreshToken }                             │
   │<──────────────────────────────────────────────────────────────┤
   │                                 │                             │
   │  6. Every request, Authorization: Bearer <accessToken>        │
   ├──────────────────────────────────────────────────────────────>│
   │                                 │                             │
   │  7. On expiry: POST /api/v1/auth/refresh { refreshToken }     │
   ├──────────────────────────────────────────────────────────────>│
   │     → a new pair; the old refresh token is now dead           │
   │<──────────────────────────────────────────────────────────────┤
```

There is no separate registration call. An account comes into existence the
first time a valid provider token is exchanged.

**Why the exchange exists.** Provider ID tokens expire in about an hour and no
app can renew one without putting a sheet in front of the user. They are also
unrevocable by us — nobody can revoke Google's token but Google. Exchanging one
for a token of our own fixes both.

A provider token presented directly as a bearer token still works. Both apps
shipped that way, and breaking them from the server side is not an upgrade
path; but it inherits the one-hour limit, so clients should exchange.

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
| `WATCH_GURU_AUTH_AUDIENCES` | Comma-separated OAuth client ids for the iOS and Android apps. **Startup fails without it** — an empty list means no check on who a token was minted for |
| `WATCH_GURU_AUTH_SESSION_SECRET` | At least 32 random bytes; the HMAC key for the access tokens this service signs. **Startup fails without it** |
| `watch-guru.auth.issuers` | Defaults to Apple and Google; both are configured as trusted to assert email verification |

There is deliberately no bypass profile. Each of those fails startup rather
than degrading quietly. For local development,
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

### Sessions this service issues

| | |
|---|---|
| Access token | A JWT signed here with HS256, `iss` `https://watch-guru.dev`, `aud` `watch-guru-api`, subject the **internal** user id. 15 minutes. Not stored anywhere: a table of live access tokens is a table worth stealing. |
| Refresh token | 256 bits of CSPRNG output, returned once and stored only as a SHA-256 hash. 30 days. |

HMAC rather than RSA because the only party that verifies these is this
service, so a public key buys nothing and a private key would have to be
managed and rotated. If a second service ever needs to verify them, that is the
moment to switch to RS256 and publish a JWK set — not before.

SHA-256 rather than bcrypt for the stored hash because the input is 256 bits of
random, not a password: there is nothing guessable to slow an attacker down,
and refresh runs on every app launch.

### Rotation, and what happens when a token is stolen

Every refresh burns the presented token and issues a successor in the same
**family** — the set of tokens descended from one sign-in.

Presenting a token that has already been exchanged means two parties hold it.
There is no way to tell which one is legitimate, so the entire family is
revoked and both are signed out. That is the intended outcome: a stolen refresh
token is worth at most one refresh, and using it is what reveals the theft.

Sign-out revokes the family too, not just the presented token — but families
are per sign-in, so signing out on the phone leaves the tablet alone.

Deleting an account removes its refresh tokens by `ON DELETE CASCADE`. An
access token issued moments earlier still verifies for up to 15 minutes, so
`CurrentUserService` rejects a session token whose user no longer exists rather
than provisioning a replacement for someone who asked to be deleted.

### Access tokens cannot be revoked

Revocation acts on the refresh token. The 15-minute access-token lifetime is
therefore the window in which a stolen access token still works, and shortening
that window is the only lever. Making access tokens revocable would mean a
database lookup on every request, which is the cost this design deliberately
avoids.

### Nonces are still not used

Neither app sets a nonce. With the exchange in place a nonce finally *could* be
meaningful — the backend would issue a challenge, the app would pass it to the
provider, and the backend would reject any token whose `nonce` claim is not the
challenge it issued. That is not built yet; #26 carries the design. Until then,
replay protection is TLS plus the provider token's own short expiry, and the
exchange narrows the window in which a captured provider token is useful,
because the app stops sending it on every request.
