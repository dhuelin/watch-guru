# Design system

Two native codebases risk two failures, and they pull in opposite directions.
Left alone, the apps drift into different products. Forced together, they
converge on a lowest-common-denominator UI that looks foreign on both
platforms.

The target is neither. **What is shared is meaning, not pixels.** What a status
badge signifies, how a poster is proportioned, what density a library row uses,
what a button says — those are product decisions and they are identical. How
any of it is drawn is each platform's business.

## The rule that keeps this honest

> Share the semantics. Write the components twice.

There is deliberately **no** cross-platform component spec. The moment a
`WatchGuruButton` contract exists that both platforms must satisfy, both stop
being native — the shared abstraction becomes the lowest common denominator of
UIKit and Android, and neither gets its own affordances.

If you find yourself adding a row to this document that describes a *component*
rather than a *meaning*, it belongs in one of the two codebases instead.

---

## Watch status

Five states, from `WatchStatus`. These are the most-repeated visual element in
the product — they appear on every library row, every search result already in
the library, and every title detail screen.

| Status | Means | Colour role | Icon meaning |
|---|---|---|---|
| `WATCHLIST` | Intends to watch, not started | Neutral / muted | Bookmark |
| `WATCHING` | In progress | **Accent** — the one status that draws the eye | Play |
| `COMPLETED` | Finished everything aired | Success | Check |
| `ON_HOLD` | Paused, intends to resume | Warning | Pause |
| `DROPPED` | Abandoned | Muted, de-emphasised | Stop / cross |

`WATCHING` is the only status with the accent colour. A library where five
statuses compete for attention tells the user nothing; the point of the screen
is *what am I in the middle of*.

**Never colour alone.** Every status carries an icon or text label too — for
colour-blind users, and because a coloured dot with no legend is a puzzle.

| Status | SF Symbol (iOS) | Material Symbol (Android) |
|---|---|---|
| `WATCHLIST` | `bookmark` | `bookmark` |
| `WATCHING` | `play.circle.fill` | `play_circle` |
| `COMPLETED` | `checkmark.circle.fill` | `check_circle` |
| `ON_HOLD` | `pause.circle` | `pause_circle` |
| `DROPPED` | `xmark.circle` | `cancel` |

## Colour

Roles, not values. Each platform resolves them its own way, and both must work
in light and dark.

| Role | Used for |
|---|---|
| `accent` | `WATCHING`, primary actions, progress fill |
| `success` | `COMPLETED`, positive confirmations |
| `warning` | `ON_HOLD`, stale-data notices |
| `danger` | Destructive actions — remove, delete account |
| `muted` | `DROPPED`, unaired episodes, secondary metadata |
| `surface` / `onSurface` | Card and sheet backgrounds and their content |

**iOS** resolves these to semantic system colours (`Color.accentColor`,
`.secondary`, `.red`) so dark mode and increased-contrast come free.
**Android** resolves them to Material 3 tokens (`primary`, `tertiary`,
`error`, `onSurfaceVariant`), with dynamic colour on Android 12+ — meaning the
Android app legitimately looks different on different devices. That is correct,
not a bug.

No screen may contain a hardcoded hex value.

## Ratings

Three rating sources appear in the product and they must never be confused.

| Source | Scale | Shown as |
|---|---|---|
| TMDB | 0–10, one decimal | Plain number with the TMDB attribution nearby |
| IMDb | 0–10, one decimal | Number badged as IMDb — only when non-null |
| The user's own | 0–10 | Visually distinct from both; this is *their* opinion |

A null rating is **omitted entirely**, never rendered as `0.0` or `—`. The
`imdbRating` field is optional in the API precisely so the UI can tell "not
rated" from "rated zero"; every title predates its IMDb import, and most titles
in a fresh catalog have no IMDb rating at all.

## Imagery

| Asset | Aspect | Notes |
|---|---|---|
| Poster | **2:3** | The universal film-poster ratio. Never crop to square. |
| Backdrop | **16:9** | Detail-screen header |
| Episode still | **16:9** | Episode rows |
| Service logo | Square-ish, variable | Never distort; letterbox instead |

Every image needs three states, and the *failed* state is the one that gets
forgotten: loading (a neutral placeholder at the correct aspect ratio, so
nothing reflows when it arrives), loaded, and failed or absent (a placeholder
carrying the title's initials — a large grey rectangle is indistinguishable
from a bug).

TMDB does not have artwork for everything. A title with no poster is normal,
not exceptional.

## Typography

Roles, mapped to each platform's own scale. Both platforms must honour the
user's text-size setting at every size, including the accessibility sizes.

| Role | Used for | iOS | Android |
|---|---|---|---|
| `screenTitle` | Navigation titles | `.largeTitle` | `headlineMedium` |
| `titlePrimary` | A title's name | `.headline` | `titleMedium` |
| `titleSecondary` | Episode names | `.subheadline` | `titleSmall` |
| `body` | Overviews | `.body` | `bodyMedium` |
| `metadata` | Year, runtime, S01E02 | `.caption` | `labelMedium` |
| `numeric` | Ratings, counts | Monospaced digits | Monospaced digits |

Monospaced digits for anything that updates in place — a progress counter that
shifts sideways as it counts from 9 to 10 looks broken.

## Density

| Context | Layout |
|---|---|
| Library grid | Posters, 2–3 columns depending on width |
| Library list | Row: poster thumbnail, title, progress bar, next episode |
| Search results | Row: poster thumbnail, title, year, type badge |
| Episode row | Number, name, air date, watched control |

The episode row is the highest-traffic control in the app. Its watched
affordance must be **at least 44pt (iOS) / 48dp (Android)**, which is larger
than it looks like it needs to be — people tap it repeatedly, often on a sofa,
often one-handed, often slightly drunk after four episodes.

## Copy

Identical wording on both platforms. Users switch devices; a status called
"Watching" on one and "In progress" on the other is the same product
contradicting itself.

| Concept | Text |
|---|---|
| `WATCHLIST` | Watchlist |
| `WATCHING` | Watching |
| `COMPLETED` | Completed |
| `ON_HOLD` | On hold |
| `DROPPED` | Dropped |
| Mark watched | Mark watched |
| Mark up to here | Mark all up to here |
| Empty library | Nothing tracked yet. Search for a film or series to get started. |
| Empty search | No results for "{query}". |
| Offline | Offline — changes will sync when you reconnect. |
| Stale data | Availability last checked {relative time} ago. |
| Provider down | Couldn't reach the catalogue. Your library is still up to date. |

That last one is the product's whole posture in one sentence: TMDB being down
is not the user's problem, and their own data is unaffected.

## Empty, loading and error states

Every list has four states and all four are designed. A spinner over a blank
screen is not a loading state; it is the absence of one.

- **Empty** — says why it is empty and offers the action that fills it.
- **Loading** — skeleton rows at the real row height, so nothing jumps when
  data lands. Cached content shows immediately and refreshes underneath.
- **Error** — says what failed and what still works. Distinguish "we couldn't
  reach the catalogue" from "you're offline".
- **Populated.**

## Motion

Motion communicates change; it does not decorate. Marking an episode watched
gets immediate visual feedback and a haptic, because it is the interaction the
product is judged on and it must feel *certain*.

Honour Reduce Motion / animator duration scale. Where an animation conveys
meaning, replace it with a cross-fade rather than removing the feedback.

## Accessibility

Non-negotiable, and covered in full by issue #23.

- Every control has a label saying what it *does*, not what it is. An episode
  checkbox that announces "button" tells a blind user nothing; it should
  announce "Mark season 1 episode 4 watched".
- Contrast meets WCAG AA in both themes. Status colours are the likeliest
  failure — check them first.
- Text scales to the largest accessibility size without clipping. Poster grids
  and episode rows break first.

## Platform divergence, on purpose

These should differ, and a reviewer should not file bugs about them:

| | iOS | Android |
|---|---|---|
| Navigation | `NavigationStack`, large titles | Bottom bar, predictive back |
| Row actions | Swipe actions, context menus | Swipe-to-dismiss with undo snackbar |
| Confirmation | `.confirmationDialog` | Snackbar with undo |
| Sharing | Share sheet | Android share sheet |
| Widgets | WidgetKit, Lock Screen, StandBy | Glance, resizable, dynamic colour |
| Theming | Semantic system colours | Material 3 dynamic colour |

Someone holding both phones should recognise the same product and, if they
know the platforms, see two apps that each belong where they are.
