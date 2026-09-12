# Connecting Trakt

Trakt is the integration worth having, and the reason has nothing to do with
Trakt itself: other people already solved the hard part. Anybody scrobbling
from Plex, Kodi, Infuse, a Jellyfin plugin or half a dozen apps already has
their viewing in Trakt, so one connection here picks up every source they use.

## How it works, and how it differs from Plex

[Plex](PLEX.md) calls us and we hold nothing of theirs. Trakt is the other way
round: you authorise Watch Guru to read your history, and this service then
holds an access token belonging to your Trakt account.

That token is the first credential of somebody else's that this project has
ever stored, so it is stored the way one should be:

- **Sealed with AES-GCM** under a key that lives in the deployment's
  configuration, never in the database. A dump of the database, without the
  key, yields nothing usable.
- **Never sent to either app.** The OAuth exchange happens on the server
  because it needs the client secret, and a secret shipped inside two app
  binaries is not a secret.
- **Revoked at Trakt when you disconnect**, before it is deleted here — the
  other order would leave a live token at Trakt that nobody can revoke.
- **Authenticated as well as encrypted**, so a row somebody edited in the
  database fails to open rather than decrypting to something subtly different.

## Setting it up

1. **Profile → Connect Trakt.** The app opens trakt.tv in a browser rather than
   a web view, so you can see whose address bar you are typing a password into.
2. Approve. Trakt sends you back to a page that says you can close it.
3. History starts arriving within a couple of hours, or immediately if you tap
   **Sync now**.

The first sync reaches back through your history in bounded runs rather than
one enormous read: each run reads a fixed number of pages and moves a cursor,
and the next run continues from there. A long history therefore fills in over
several runs instead of holding one connection open for minutes.

## What gets recorded

- **Films and episodes.** Trakt's `scrobble`, `checkin` and `watch` actions all
  mean the same thing — it was watched — and differ only in how it was
  recorded.
- **Matched by IMDb id where there is one.** Trakt carries the film's own id
  and, for an episode, the *show's*, which is exactly what this catalogue holds.
  That makes a match exact rather than a guess from a name and a year.
- **Every viewing, including rewatches.** A viewing is identified by Trakt's own
  history id, which is unique for ever, so re-reading an overlapping window
  writes nothing twice while a genuine second viewing still counts.
- **Nothing is guessed at.** A title this catalogue has never held is looked up
  once with the metadata provider, and if it still cannot be placed it is named
  on the connection rather than dropped.

## When it stops working

**Profile → Connect Trakt** shows when the last sync ran and what it did.
Trakt's authorisations expire, and a token that can no longer be renewed shows
as *Trakt access has expired* with a Connect button — reconnecting keeps the
cursor, so it resumes rather than re-reading everything.

## What this needs from a deployment

Two settings, both in [`DEPLOYMENT.md`](DEPLOYMENT.md):

- `WATCH_GURU_TRAKT_CLIENT_ID` and `WATCH_GURU_TRAKT_CLIENT_SECRET` from a
  free [Trakt application registration](https://trakt.tv/oauth/applications),
  whose redirect URI must be `<public base url>/api/v1/streaming/trakt/callback`.
- `WATCH_GURU_CREDENTIALS_SECRET`, the key that seals the tokens.

Without the first pair, connecting Trakt is refused up front with a message
saying so — rather than after the user has already approved, which is the worst
possible place to discover a server is not configured. Without the key, it is
refused for the same reason: a token that cannot be stored safely is not stored
at all.
