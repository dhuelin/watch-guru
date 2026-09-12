# Connecting Plex

Watching an episode on your own Plex server marks it watched here, without
anybody opening the app. Plex is the first service Watch Guru can genuinely
connect to, and the rest of this page explains both how to set it up and why
the list of services that work this way is so short.

## How it works

Your Plex server calls Watch Guru, not the other way round. Watch Guru issues
a URL with a secret in it, you paste that URL into your own Plex settings, and
from then on your server posts an event every time something plays, pauses or
finishes.

That direction is the whole security argument. Watch Guru never holds a Plex
password, a Plex token, or your server's address, so there is nothing stored
here that could be used to read your library. The connection carries exactly
one capability, and it points inward.

## Setting it up

**You need Plex Pass.** Webhooks are a Plex Pass feature; without one, your
server cannot call anything.

1. In the app, open **Profile → Connect Plex**. Give it your Plex username if
   other people watch things on the same server — more on that below.
2. Copy the URL it shows you. It is shown **once**: only a hash of it is
   stored, the way a password is, so nobody, including this server, can show it
   to you again. Lose it and you reconnect, which issues a new one.
3. In Plex: **Settings → Webhooks → Add Webhook**, paste, save.
4. Watch something. When it passes about 90% of its runtime, Plex sends a
   `media.scrobble` event and the episode appears in your history.

**Profile → Connect Plex** afterwards shows when your server last called and
what came of it. An integration that silently stops is worse than one that was
never offered.

## What gets recorded, and what does not

- **Only `media.scrobble`.** Plex also sends play, pause, resume, stop and
  rate. Recording a play event would mark an episode watched for anybody who
  opened it and changed their mind a minute later.
- **Films and episodes only.** Music and photos come through the same webhook
  and are ignored.
- **Only your own viewing.** A Plex server owner receives events for everybody
  who watches anything on their server. If you set a Plex username when
  connecting, only that account's viewing is recorded; without one, only
  deliveries Plex marks as the webhook owner's own are. On a shared server,
  set the name.
- **A repeat is not a duplicate.** A viewing is identified by the item's Plex
  rating key and the day you watched it, so a replayed or repeated delivery
  writes nothing the second time. The trade is deliberate: Plex sends no
  session id, so watching the same episode twice on one day is recorded once.
- **A title the catalogue has never held is fetched.** One metadata lookup per
  scrobble, so starting a new series on Plex pulls it in rather than reporting
  it as unknown.
- **Anything that cannot be matched is reported, not dropped.** It shows on the
  connection with the reason — most often an episode the catalogue has not
  fetched yet, which opening the series once fixes.

## Disconnecting

**Profile → Disconnect** retires the URL immediately; deliveries from then on
are rejected, including from a URL still pasted into Plex. The connection's
history stays, because when it last worked is worth keeping. Reconnecting
issues a new URL and retires the old one, which is also the way out if you
pasted yours somewhere public.

## One caveat worth knowing

The secret is in the URL, because Plex sends no headers of our choosing and
offers no signing secret of its own. Anything that logs full request URLs — a
proxy, a CDN, an access log — therefore logs a credential that can write watch
events into one account. It cannot read anything, and you can retire it from
the app at any time, but a deployment's logs should be treated accordingly.

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

Jellyfin, Emby and Trakt work the same way Plex does and are next; Trakt in
particular brings in everything you already scrobble from anywhere else. See
issue #39.
