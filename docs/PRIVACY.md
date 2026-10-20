# Watch Guru — Privacy Policy

**Last updated: 20 October 2026**

> **The operator name has to match the developer name on the Play listing** —
> for a personal account, the name on the identity document Google verified.
> If what Play Console shows differs from the name below, change the name
> below rather than the other way round: a policy naming someone the store
> does not is worse than no policy.
>
> The URL to give Play can be this file on GitHub Pages, or its `blob` URL on
> github.com. Either is public and stable.

Watch Guru keeps a record of what you have watched. This policy describes what
that means for your data: what is collected, why, who else sees it, and how to
get rid of it.

The service is operated by **Denis Huelin**. Contact: **support@huelin.dev**.

## The short version

- Your viewing history, your account, and nothing else.
- No advertising, no analytics SDK, no crash-reporting SDK, no tracking across
  other apps or websites, no advertising identifier, no location, no contacts.
- Nothing is sold, and nothing is shared for anyone else's marketing.
- You can delete your account, and everything in it, from inside the app.

## What is collected

**Account details, from your sign-in provider.** When you sign in with Google
or Apple, Watch Guru receives your email address, your display name, and an
identifier for your account with that provider. It never receives your
password. Watch Guru has no password of its own.

**Your settings.** Your country (two letters, used to work out which streaming
services carry a title where you are), your language, and your time zone.

**What you watch.** The films and episodes you mark as watched and when, your
watchlist, your ratings, and the status you give a title — watching, finished,
on hold, dropped. This is the substance of the app; without it there is nothing
to show you.

**A notification token, if you turn notifications on.** An identifier issued by
Google so that a new-episode notice can reach your device. It identifies the
installation, not you, and it is deleted when you turn notifications off or
sign out.

**Connections you set up yourself.** If you connect a media server (Plex,
Jellyfin, Emby) the service stores the webhook address it calls, which is
secret and should be treated as one. If you connect Trakt, the service stores
an access token for your Trakt account, encrypted with AES-GCM under a key held
in the server's configuration and not in its database.

**Ordinary server logs.** Requests to the API are logged with their time, path
and outcome, for diagnosing faults and detecting abuse.

## What is not collected

No advertising identifier. No location. No contacts, photos, calendar, or
files. No microphone or camera. No analytics or crash-reporting library is
built into either app — the Android build's dependencies are listed in
`android/app/build.gradle.kts` and contain none. Nothing you watch is used to
target advertising, because there is no advertising.

## Who else sees it

**TMDB** supplies the catalogue — titles, artwork, episodes, and which
streaming services carry what. The server asks TMDB about *titles*. It does not
send your identity, your account, or your history, so TMDB cannot see who asked
or what you watched.

**Trakt**, only if you connect it, and only your own account there. The
connection reads your Trakt history into Watch Guru.

**Your hosting and infrastructure providers**, in the ordinary way any online
service's provider handles data passing through it.

That is the complete list. Nothing is sold. Nothing is shared with a data
broker, an advertising network, or anyone else's marketing.

## Where it is kept, and for how long

Data is held in the service's own PostgreSQL database for as long as your
account exists. Delete your account and it goes with it.

## Deleting your account and your data

In the app: **Profile → Delete account**. This removes your account record,
your watchlist, your entire viewing history, your ratings, your notification
tokens, and any connection you had set up. Third-party tokens are revoked with
the provider before they are deleted here, so disconnecting at Trakt does not
depend on you remembering to do it separately.

It is immediate and it cannot be undone. There is no grace period and no
archived copy; backups age out on the hosting provider's own schedule.

If you cannot reach the app — you have lost the device, or you cannot sign in —
write to **support@huelin.dev** from the email address on the account and ask for it
to be deleted.

## Children

Watch Guru is not directed at children and does not knowingly collect anything
from a child under 13 (or the equivalent age in your country).

## Your rights

Depending on where you live, you may have the right to see a copy of your data,
correct it, have it deleted, or object to how it is handled. Deletion is built
into the app. For anything else, write to **support@huelin.dev**.

If you are in the EU or the UK: the lawful basis for handling your account and
viewing history is performance of the contract between you and the service —
it is the service. For server logs it is legitimate interest in keeping the
service working and unabused. You may complain to your national data protection
authority.

## Changes

If this policy changes in a way that affects you, the date at the top changes
and the app says so before the change takes effect.
