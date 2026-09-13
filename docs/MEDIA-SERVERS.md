# Connecting a media server

Watching an episode on your own Plex, Jellyfin or Emby server marks it watched
here, without anybody opening the app. All three work the same way, and this
page covers all three; [Trakt](TRAKT.md) is a different shape and has its own.

## How it works

Your server calls Watch Guru, not the other way round. Watch Guru issues a URL
with a secret in it, you paste that URL into your own server's settings, and
from then on it posts an event every time something plays, pauses or finishes.

That direction is the whole security argument. Watch Guru never holds a
password, a server token, or your server's address, so there is nothing stored
here that could be used to read your library. The connection carries exactly
one capability, and it points inward.

## Setting it up

In the app, open **Profile → Connect Plex / Jellyfin / Emby**. Copy the URL it
shows you — it is shown **once**, because only a hash of it is stored, the way
a password is. Lose it and you reconnect, which issues a new one and retires
the old.

| Server | Where to paste it | Notes |
|---|---|---|
| **Plex** | Settings → Webhooks → Add Webhook | Needs Plex Pass; webhooks are a Plex Pass feature |
| **Jellyfin** | Dashboard → Plugins → Webhook → Add Generic Destination | Install the Webhook plugin first, and tick **Playback Stop** |
| **Emby** | Settings → Notifications → Add Notification → Webhooks | Choose the playback events |

Then watch something. The app's connection screen shows when your server last
called and what came of it — an integration that silently stops is worse than
one that was never offered.

### Jellyfin and Emby need your username

Their webhook is configured once for the whole server by whoever administers
it, and it fires for **everybody** on that server, with nothing in the payload
saying whose webhook it is. The username is the only thing separating your
viewing from your housemate's, so connecting either of them asks for it and
refuses without it.

Plex is the exception: it says whose account played something, so it can be
connected without a name — though on a shared server it is still worth giving.

## What gets recorded, and what does not

- **Only a finished viewing.** Plex sends a `media.scrobble` at about 90% of
  the runtime; Jellyfin and Emby send a playback-stop that says it played to
  completion. A stop halfway through is somebody giving up, which is the
  opposite of watched, and a *start* would mark an episode watched for anybody
  who opened it and changed their mind a minute later.
- **Films and episodes only.** Music and photos come through the same webhook
  and are ignored.
- **Only your own viewing**, per the username rules above.
- **A repeat is not a duplicate.** A viewing is identified by the item's id on
  that server and the day you watched it, so a replayed or repeated delivery
  writes nothing the second time. The trade is deliberate: none of these
  servers sends a session id, so watching the same episode twice on one day is
  recorded once.
- **A title the catalogue has never held is fetched.** One metadata lookup per
  delivery, so starting a new series pulls it in rather than reporting it as
  unknown.
- **Anything that cannot be matched is reported, not dropped.** It shows on the
  connection with the reason — most often an episode the catalogue has not
  fetched yet, which opening the series once fixes.

An episode's own year and IMDb id are deliberately ignored, whichever server
sent them. A media server's `year` on an episode is that episode's air year —
Breaking Bad's *Ozymandias* says 2013, and the series began in 2008 — and its
IMDb id is the episode's, where this catalogue holds the series'. Both would
turn a good match into a wrong one.

## Disconnecting

**Disconnect** retires the URL immediately; deliveries from then on are
rejected, including from a URL still pasted into your server. The connection's
history stays, because when it last worked is worth keeping. Reconnecting
issues a new URL and retires the old one, which is also the way out if you
pasted yours somewhere public.

## One caveat worth knowing

The secret is in the URL, because none of these servers sends headers of our
choosing or offers a signing secret of its own. Anything that logs full request
URLs — a proxy, a CDN, an access log — therefore logs a credential that can
write watch events into one account. It cannot read anything, and you can
retire it from the app at any time, but a deployment's logs should be treated
accordingly.

## Why not Netflix, Prime Video or Apple TV

They have no public API for viewing activity. Netflix retired theirs in 2014,
and none of the big subscription services offers a third party any way to ask
what you watched. The only ways in would be storing your password and
scraping, or an accessibility service that reads what other apps display —
both of which would mean this project holds credentials it has no business
holding, and neither survives the service changing its markup.

What those services do offer is an export you request yourself, and Watch Guru
already reads it: see [`IMPORT.md`](IMPORT.md). Re-importing is idempotent, so
doing it every few months costs one file and duplicates nothing. That is the
honest version of "connect Netflix", and it is the one that will still work
next year.

If you scrobble to [Trakt](TRAKT.md) from any of these, connecting Trakt
instead brings all of it in at once.
