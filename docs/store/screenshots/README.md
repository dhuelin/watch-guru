# Store screenshots

Taken off the running apps on 23 October 2026: iOS on the iPhone 17 Pro Max
simulator, Android on a Pixel emulator set to 1080×1920 with
`adb shell wm size`, so the captures are 9:16 as Play requires (a Pixel's
native 20:9 is longer than Play's 2:1 limit). Status bars were
cleaned with `simctl status_bar` and Android's SystemUI demo mode.

| Folder | What | Use for |
|---|---|---|
| `raw/iphone-6.9/` | 6 captures, 1320×2868 | App Store, iPhone 6.9" |
| `raw/ipad-13/` | 5 captures, 2064×2752 | App Store, iPad 13" |
| `raw/android-phone/` | 6 captures, 1080×1920 | Play, phone |
| `framed/<same>/` | The same, captioned on the brand gradient, same sizes | Either store; these are the ones to upload |

```bash
python3 tools/make-store-screenshots.py   # rewrite framed/ from raw/
```

Captions live in `CAPTIONS` in that script, keyed on the file name after the
number, so reordering is renaming.

## Why every title in them is made up

None of the films and series exists. The app ran against a local backend whose
TMDB was a stand-in serving invented titles, with poster art drawn for them,
and the streaming services are invented too ("Streamly", "Cinebox"). Real
posters are studio artwork that TMDB's terms let the app display, not let us
use to advertise it, and other services' names and logos in a screenshot are a
common App Store rejection. Sign-in was a mock OIDC issuer; nothing here
touched a real account.

## Retaking them

Only worth doing after a visible UI change. What was needed:

- The backend from `main`, pointed at a mock OIDC issuer
  (`ghcr.io/navikt/mock-oauth2-server`) and the stand-in TMDB, with Postgres
  from Docker.
- A seeded account: a dozen titles across every status, partly watched series,
  and a week of daily viewing so the streak is not zero.
- A debug-only patch, never committed, that lets each app start with a session
  token handed to it, since neither store's sign-in can run against a mock.

None of that tooling is in the repository. If screenshots are retaken more
than once or twice, the stand-in TMDB and the seed script are worth adding
under `tools/`.

The iPad set was taken on 24 October 2026 on the iPad Pro 13-inch (M5)
simulator, in portrait, after the redesign and the iPad layout, so it shows
the newer look; the iPhone and Android sets predate the redesign and are worth
retaking. Not taken: German screenshots. Both stores fall back to the default
language's set, so the English ones serve the
German listing until a German set is made.
