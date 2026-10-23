# App Store listing

What App Store Connect asks for that can be written down in advance. The Play
equivalent, and the account and backend steps both stores share, are in
[`STORE-LISTING.md`](STORE-LISTING.md). Code signing and TestFlight are
[#34](https://github.com/dhuelin/watch-guru/issues/34) and need the Apple
Developer account, so they are not here.

## Already in the build

These used to be console questions or upload failures, and are now answered by
the project itself:

| What | Where | Effect |
|---|---|---|
| Version and build number | `ios/project.yml`, `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` | Now actually reach the Info.plist. Before, every build said 1.0 (1), and App Store Connect refuses a build number it has seen. A release workflow should pass `CURRENT_PROJECT_VERSION=${{ github.run_number }}`, as the Android one does for `versionCode`. |
| Export compliance | `ITSAppUsesNonExemptEncryption = NO` in the Info.plist | The "does your app use encryption" question is not asked per build. HTTPS through the system is exempt. |
| Privacy manifest | `ios/WatchGuru/Resources/PrivacyInfo.xcprivacy` | Declares no tracking and the three data types under App Privacy below. |
| App icon | `ios/WatchGuru/Resources/Assets.xcassets/AppIcon.appiconset` | 1024×1024, no alpha. |

## Screenshots

In [`store/screenshots/`](store/screenshots/README.md). Upload the framed ones;
the raw ones are valid too.

| Slot | Files | Size |
|---|---|---|
| iPhone 6.9" display | `framed/iphone-6.9/*.png` (6) | 1320×2868 |

App Store Connect scales the 6.9" set down for every smaller iPhone, so it is
the only slot required.

v1 is iPhone-only (`TARGETED_DEVICE_FAMILY = 1`), so no iPad screenshots are
needed; iPads run the app in the iPhone frame. The app had no iPad layout, only
the phone one stretched to 13 inches. Real iPad support is
[#65](https://github.com/dhuelin/watch-guru/issues/65), and once shipped it
cannot be taken back, so it waits for a layout worth shipping.

## Listing — English

**Name** (30 max)

```
Watch Guru
```

**Subtitle** (30 max)

```
Track films and TV episodes
```

**Promotional text** (170 max, can change without a new build)

```
Always know which episode you are on. Mark films and episodes watched in one tap, see where to stream them, and keep your whole viewing history in one place.
```

**Keywords** (100 max, comma-separated, no spaces)

```
tv,tracker,episodes,series,movies,films,watchlist,shows,progress,binge,history,widget,streaming,log
```

Trakt, Plex, Jellyfin and Emby are deliberately absent: other products' names
in the keyword field are a common rejection under guideline 2.3.7, even for an
app that integrates with them. They are fine in the description.

**Description** (4000 max)

The Play description from [`STORE-LISTING.md`](STORE-LISTING.md), with one
change, because the rest describes the iOS app equally well: under **BUILT
PROPERLY**, replace the Material 3 line with
`• Built for iOS, with a home-screen widget and Sign in with Apple.`

## Listing — German

**Subtitle**

```
Filme und Serien im Blick
```

**Promotional text**

```
Immer wissen, bei welcher Folge du bist. Filme und Folgen mit einem Tippen markieren, sehen, wo sie laufen, und den ganzen Verlauf an einem Ort.
```

**Keywords**

```
serien,filme,tracker,folgen,merkliste,staffel,fortschritt,streaming,verlauf,widget,tv,kino
```

**Description:** the German Play description, with the Material 3 line replaced
by `• Für iOS gebaut, mit Widget und „Mit Apple anmelden“.`

## App information

| Field | Value |
|---|---|
| Bundle ID | `dev.huelin.watchguru` (widget: `dev.huelin.watchguru.widgets`) |
| SKU | `watchguru-ios` — any string; never shown |
| Primary category | Entertainment |
| Secondary category | Utilities |
| Content rights | Answer **yes**, the app shows third-party content (TMDB catalogue data and artwork), and that you have the rights to use it under TMDB's API terms. |
| Privacy policy URL | `https://dhuelin.github.io/watch-guru/PRIVACY.html` |
| Support URL | `https://huelin.dev` — must be a page that shows a way to get in touch; a bare `mailto:` is not accepted |
| Copyright | `2026 Huelin.dev` |

## App Privacy — answers for the form

The same facts as the Play Data safety table and the privacy manifest.

**Do you or your third-party partners collect data from this app?** Yes.

| Data type | Linked to the user | Used for tracking | Purpose |
|---|---|---|---|
| Contact Info → Email Address | Yes | No | App Functionality |
| Contact Info → Name | Yes | No | App Functionality |
| User Content → Other User Content | Yes | No | App Functionality |

Everything else, including Identifiers, Usage Data, Diagnostics and Location:
**not collected**. The push token is not an Identifier in Apple's sense (that
category means advertising and vendor IDs), and the region is a setting, not a
location reading.

## Age rating

Answer every content question **None**, except the ones about third-party
content the app displays: titles, synopses and posters from TMDB include
mature films. Answering "Infrequent/Mild" for mature or suggestive themes is
the honest answer and is expected to give **12+**; that is an inference from
how the questionnaire scores, not something checked against App Store Connect.
No user-to-user communication, no web browsing, no gambling, no purchases.

## App Review information

> **Sign-in required.** Reviewers get past it with their own Apple ID: Sign in
> with Apple creates an account on first use, so no demo account is needed.
> Say so in the notes, or review stalls asking for credentials.

Notes for the reviewer:

```
Watch Guru tracks the films and TV episodes you watch. Sign in with Apple
creates an account on first use; any Apple ID works and no demo account is
needed. Search for any title, add it to your library, and mark episodes
watched to see Up Next. Account deletion is in Profile > Delete account.
Connections to Plex, Jellyfin, Emby and Trakt are optional and need the
reviewer's own server or account; nothing else depends on them.
```

Contact: the account holder's name, phone and `support@huelin.dev`.
