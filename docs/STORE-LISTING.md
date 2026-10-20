# Play Store listing

Everything the Play Console asks for that can be written down in advance, so
the console session is copy-and-paste rather than composition.

What is **not** here, because it cannot be: **screenshots**. Play wants at
least two phone screenshots and they have to come off a running app. Nothing
here has run on a device yet — see
[#35](https://github.com/dhuelin/watch-guru/issues/35). The concept render in
`docs/brand/promo.jpg` is not a screenshot and must not be submitted as one.

## Graphics

| Asset | File | Spec |
|---|---|---|
| App icon | `docs/brand/play-icon-512.png` | 512×512 PNG. Full-bleed, square corners — Play rounds it itself. |
| Feature graphic | `docs/brand/play-feature-graphic.png` | 1024×500 PNG, no alpha. |
| Phone screenshots | **missing** | 2–8, 16:9 or 9:16, min 320px. Needs a device. |

Both are regenerated from the artwork by:

```bash
python3 tools/make-store-graphics.py          # rewrite
python3 tools/make-store-graphics.py --check  # fail if drifted
```

## Store listing — English

**App name** (30 max)

```
Watch Guru
```

**Short description** (80 max)

```
Track every film and episode you watch, and always know what to start next.
```

**Full description** (4000 max)

```
Watch Guru remembers what you have watched, so you do not have to.

Streaming apps answer three questions badly: what have I already seen, what
episode am I on, and what should I watch next. Watch Guru answers all three in
one place, across every service you use, because your progress lives with you
rather than with whichever app happened to be playing the episode.

WHAT IT DOES

• Mark a film or an episode watched in one tap, from the title screen or the
  home-screen widget.
• Up next — the exact episode you are on for every series you have started,
  without you having to remember a season number.
• Your library, by status: watching, on the watchlist, finished, on hold,
  dropped.
• Rate what you have seen and keep a history you can filter, search and edit.
• Statistics and streaks: what you watched this month, your longest run of
  days, how your viewing has changed.

WHERE TO WATCH

See which streaming services carry a title in your country, for subscription,
rental, purchase, free or ad-supported viewing. Tell Watch Guru which services
you already pay for and those are listed first — nothing is ever hidden, so
you still see a title the week before it arrives on your service.

BRING YOUR HISTORY WITH YOU

• Connect Plex, Jellyfin or Emby, and what you finish there is recorded here.
• Connect Trakt and your existing history comes across, along with anything
  scrobbled by the dozen other apps that already write to it.
• Import a viewing history from a CSV file.

BUILT PROPERLY

• A real home-screen widget that marks an episode watched without opening the
  app.
• Works offline — changes you make are queued and replayed when you are back.
• Material 3 throughout, with dynamic colour on Android 12 and later.
• English and German.
• No advertising. No analytics. No tracking. Delete your account, and
  everything in it, from inside the app.

Catalogue data from TMDB. Watch Guru is not endorsed or certified by TMDB.
```

## Store listing — German

**App name**

```
Watch Guru
```

**Short description** (80 max)

```
Behalte jeden Film und jede Folge im Blick – und weiß, was als Nächstes kommt.
```

**Full description**

```
Watch Guru merkt sich, was du gesehen hast – damit du es nicht musst.

Streaming-Apps beantworten drei Fragen schlecht: Was habe ich schon gesehen,
bei welcher Folge bin ich, und was soll ich als Nächstes schauen? Watch Guru
beantwortet alle drei an einem Ort und über alle Dienste hinweg, weil dein
Fortschritt dir gehört und nicht der App, die zufällig gerade lief.

WAS DIE APP KANN

• Film oder Folge mit einem Tippen als gesehen markieren – im Titel oder
  direkt im Widget auf dem Startbildschirm.
• Als Nächstes: genau die Folge, bei der du stehen geblieben bist, für jede
  begonnene Serie – ohne dass du dir eine Staffelnummer merken musst.
• Deine Bibliothek nach Status: Schaue ich, Merkliste, Abgeschlossen,
  Pausiert, Abgebrochen.
• Bewerte, was du gesehen hast, und führe einen Verlauf, den du filtern,
  durchsuchen und bearbeiten kannst.
• Statistiken und Serien-Streaks: was du diesen Monat gesehen hast, deine
  längste Folge von Tagen, wie sich dein Sehverhalten verändert.

WO LÄUFT ES

Sieh, welche Streamingdienste einen Titel in deinem Land anbieten – im Abo,
zur Leihe, zum Kauf, kostenlos oder werbefinanziert. Sag Watch Guru, welche
Dienste du bereits bezahlst, und sie stehen oben. Nichts wird ausgeblendet:
Du siehst einen Titel auch in der Woche, bevor er bei deinem Dienst erscheint.

BRING DEINEN VERLAUF MIT

• Verbinde Plex, Jellyfin oder Emby – was du dort zu Ende siehst, landet hier.
• Verbinde Trakt, und dein bestehender Verlauf kommt mit, samt allem, was die
  vielen anderen Apps dorthin schreiben.
• Importiere einen Verlauf aus einer CSV-Datei.

ORDENTLICH GEBAUT

• Ein echtes Widget, das eine Folge als gesehen markiert, ohne die App zu
  öffnen.
• Funktioniert offline – Änderungen werden gespeichert und später übertragen.
• Durchgehend Material 3, mit dynamischen Farben ab Android 12.
• Deutsch und Englisch.
• Keine Werbung. Keine Analyse. Kein Tracking. Dein Konto und alles darin
  löschst du in der App.

Katalogdaten von TMDB. Watch Guru wird von TMDB weder unterstützt noch
zertifiziert.
```

## Required URLs

| Field | Value |
|---|---|
| Privacy policy | The published URL of [`docs/PRIVACY.md`](PRIVACY.md) — GitHub Pages, or its `blob` URL on github.com. |
| Account deletion | The same page's **Deleting your account and your data** section. Play accepts an anchor on the privacy policy when deletion is also available in-app, which it is. |
| Support email | `support@huelin.dev` — shown publicly on the listing, and the same address the privacy policy gives. It has to actually receive mail before the Google account can be created on it. |

## Data safety — answers for the form

Derived from what the backend actually stores, not from a template. Sources:
`AppUser`, `WatchlistItem`, `WatchEvent`, `EpisodeWatch`, `DeviceToken`,
`LinkedStreamingAccount`, and the Android dependency list in
`android/app/build.gradle.kts`, which contains no analytics, crash-reporting
or advertising library.

**Does your app collect or share any of the required user data types?** Yes,
collects. **Shares:** no.

| Category → type | Collected | Shared | Required | Purpose |
|---|---|---|---|---|
| Personal info → Email address | Yes | No | Required | Account management |
| Personal info → Name | Yes | No | Required | Account management, App functionality |
| App activity → Other user-generated content | Yes | No | Required | App functionality (the watchlist, history and ratings *are* the app) |
| App activity → App interactions | No | — | — | Nothing is logged for analytics |
| Device or other IDs | Yes | No | Optional | App functionality — the push token, only once notifications are switched on, deleted when they are switched off or the user signs out |
| Location | No | — | — | Country is a setting the user picks, not a location reading |
| Financial, Health, Messages, Photos, Contacts, Files, Calendar, Audio | No | — | — | None are requested or received |

**Security practices**

- Data is encrypted in transit: **yes** (HTTPS; cleartext is permitted only for
  `10.0.2.2`, the emulator's host loopback, in debug builds — see
  `network_security_config.xml`).
- Users can request that data be deleted: **yes** — in-app, Profile → Delete
  account, which is immediate and irreversible.
- Committed to the Play Families Policy: not applicable; the app is not
  directed at children.
- Independent security review: **no**. Do not claim one.

## Content rating questionnaire

Category: **Reference, News, or Educational** (a utility, not a media player —
it plays nothing). No violence, no sexual content, no profanity, no
gambling, no user-to-user communication, no sharing of location, no digital
purchases. The app displays film and TV artwork and descriptions supplied by
TMDB, which can include mature titles; answer the "does the app display
user-generated or third-party content" question honestly as **yes, third-party
catalogue content**.

## From nothing to an internal test

In order. Later steps genuinely depend on earlier ones, and two of the
dependencies are not obvious until they bite — both are flagged below.

### 1. The address, before anything else

`support@huelin.dev` has to actually receive mail. Any host, or a forwarder;
Google sends a verification code there and the account cannot be created
without it. It is also the public support address on the listing and the one
the privacy policy gives, so it is the address, not an address.

### 2. The Google account

Create a Google Account **using that existing address** rather than a new
Gmail. Account *type* on Play (personal vs organization) is a separate
question and is unaffected by this: a personal developer account can be owned
by a Google account at a custom domain, with no company anywhere.

Turn on two-factor authentication and save the recovery codes before going
further. This account owns the app, the owner's email address cannot be
changed afterwards, and moving the app to another account later is a reviewed
transfer request, not a setting.

### 3. The developer account

`play.google.com/console/signup`, personal, $25 once, then identity
verification with a government document. Verification can take days, which is
the only reason to do this before anything else is ready.

### 4. Publish the privacy policy

Fill in `<OPERATOR>` in [`PRIVACY.md`](PRIVACY.md) — it must match the
developer name Google verified — and publish it at a public URL. GitHub Pages
on this repository, or the file's `blob` URL. Both the Play listing and the
OAuth consent screen want it, so it has to exist before either.

### 5. Google Cloud, for sign-in

In a project under the **same** Google account:

- **OAuth consent screen** — external, app name `Watch Guru`, support email
  `support@huelin.dev`, the privacy policy URL from step 4.
- **A Web client.** Its id is `GOOGLE_WEB_CLIENT_ID`, and the same value goes
  in the backend's `WATCH_GURU_AUTH_AUDIENCES`. Credential Manager mints an ID
  token whose audience is the *web* client, which is why the Android client is
  not the one to use here.
- **An Android client**, package `dev.huelin.watchguru`, with the SHA-1 of the
  signing certificate.

> **Trap 1.** That Android client needs the SHA-1 of the certificate the
> installed app is actually signed with. Under Play App Signing, Google
> re-signs the upload with a key you only see *after* the first bundle is
> uploaded (Play Console → Test and release → App integrity). So sign-in fails
> for testers until you come back and add that second SHA-1 alongside your
> upload key's. It is not a code problem and it produces no useful error.

### 6. The upload key

```bash
keytool -genkeypair -v -keystore release.jks \
  -keyalg RSA -keysize 4096 -validity 10000 -alias watchguru
base64 -w0 release.jks
```

Put the base64 and the three passwords on the `android-release` environment as
`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`
and `ANDROID_KEY_PASSWORD`. Keep `release.jks` somewhere you will still have
it in a year.

### 7. The backend

The six steps in [`DEPLOYMENT.md`](DEPLOYMENT.md#first-time-setup), ending
with `ANDROID_API_BASE_URL` set on the `android-release` environment. Nothing
below works without this: the app signs in against the backend, and a build
pointed at the placeholder domain installs and then does nothing.

### 8. Create the app and upload the first bundle by hand

Play Console → Create app. Name `Watch Guru`, package
`dev.huelin.watchguru`, free, app not game.

The first bundle has to go through the console by hand — the API can upload to
a track but cannot bring an app into existence. Get one by publishing a GitHub
release, or running **Release Android** manually, and downloading the
`watch-guru-release-aab` artifact. Afterwards `PLAY_SERVICE_ACCOUNT_JSON` on
the `android-release` environment makes the workflow do it.

### 9. The console's own checklist

Store listing from this document, the two graphics from `docs/brand/`, Data
safety from the table above, content rating, target audience, ads declaration
(there are none), and the privacy policy URL.

> **Trap 2.** **App access.** The app is useless without signing in, so Google
> asks how its reviewers get past that. Answer it: either give them
> instructions saying any Google account works, or provide a test account. An
> unanswered App access section holds up review, and a wrong answer gets the
> build rejected for something that is not a defect.

### 10. Internal testing, then the 14 days

Internal testing reaches testers in minutes and needs no review — the right
place for a build nobody has run. Then closed testing: **12 testers opted in
for 14 continuous days** before you can apply for production. Recruiting
twelve people with Google accounts who will stay opted in is usually the real
bottleneck, so start asking now rather than on day one.

## Still blocking, whatever else is done

Two things the runbook cannot resolve, because neither is a configuration
step:

1. **Nobody has launched either app.** Not on a device, not on a simulator.
   That is [#35](https://github.com/dhuelin/watch-guru/issues/35). Every
   screen is unverified against a real screen, and the first person to find
   out will be a tester unless it is you.
2. **There are no screenshots,** and Play requires at least two. They come off
   a running app, which is the same blocker as above. The concept render in
   `docs/brand/promo.jpg` is not one and must not be submitted as one.

And one that is nobody's fault: a personal developer account registered now
cannot reach production until it has run a closed test with 12 testers opted
in for 14 continuous days. Internal testing is available immediately, and is
where this should go first regardless.
