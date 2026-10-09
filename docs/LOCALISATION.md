# Localisation

Watch Guru ships in **English and German**. This is the shape of that, and the
decisions inside it that are not obvious.

## Where the copy lives

| Bundle | Base | German |
|---|---|---|
| Android app | `android/app/src/main/res/values/strings.xml` | `values-de/strings.xml` |
| iOS app | `ios/WatchGuru/Resources/en.lproj/Localizable.strings` | `de.lproj/` |
| iOS widget | `ios/WatchGuruWidgets/Resources/en.lproj/Localizable.strings` | `de.lproj/` |

Three catalogues, not two, and the third is not an oversight: a widget
extension is its own bundle, a literal is looked up in the bundle it was
compiled into, and so the widget cannot read the app's catalogue. Translating
it is a separate job every time.

## The two checkers

Nothing in either build complains about a missing translation — both platforms
fall back to the base language, silently. On iOS it is worse than silent,
because the key *is* the English sentence: change one character of the key and
the result is indistinguishable from never having translated it.

So two scripts stand in for the compiler neither platform provides:

- **`tools/check-ios-strings.py`** — every localisable literal in the Swift
  sources has a catalogue entry, for both iOS bundles. Catches copy added to a
  screen and never added to the catalogue, which is how a string stops being
  translatable with nothing to say so.
- **`tools/check-translations.py`** — every base key has a translation, no
  translation exists for a key that does not, Android plural classes match, and
  **format specifiers match as a multiset**. The last of those is the one that
  crashes rather than merely reading badly: a format with more specifiers than
  arguments reads past the end of the argument list.

Both run in CI on both platforms, before anything is compiled, because they
take under a second and a full build takes minutes.

Specifiers may be *reordered* — that is what `%1$s` is for, and some languages
need it — but not lost or gained. German happens to want English's order in
every interpolated string here, so none of them are numbered.

## Decisions worth knowing about

**"Benachrichtigungen", not "Mitteilungen".** Apple's German house term for
notifications is *Mitteilungen*; Android's is *Benachrichtigungen*. This
project's rule is that the copy does not vary by platform — a status called one
thing on one phone and another on the other is the same product contradicting
itself — so one word had to win, and it is the one a reader understands on
either platform. The cost is that the iOS app does not match Apple's own
Settings app. That is the right trade here and would be the wrong one in an
iOS-only product.

**"du", not "Sie".** The English copy is plain and speaks directly — "Know what
you've watched and where you left off". *Sie* turns those same sentences into a
bank's. German software is split on this and both are defensible; this is a
personal media tracker, so *du*.

**Standard German orthography, including ß.** The locale is `de`, not `de-CH`.
If a Swiss variant is ever wanted it is `values-de-rCH` and `de-CH.lproj`, and
the only systematic change is ß to ss.

**Brand names are entries, not omissions.** "Watch Guru", "Trakt", "TMDB",
"IMDb" appear in the German catalogues with their values unchanged. Leaving
them out would work — the fallback would supply them — but then the checker
would need an exemption list, and an exemption list is where a genuinely
missing translation hides.

**The TMDB and JustWatch attributions are translated.** They are notices aimed
at the reader, and a German reader should be able to read them. Worth
confirming against TMDB's current attribution terms before a public release, in
case they require the English wording verbatim.

## One word in English, two in German

`"Series"` was used for two different things, and English hid it:

- the history filter's category, beside "Everything" and "Films" — *Serien*
- a single search result's type, beside "Film" — *Serie*

One key cannot be both. The fix was to give the second one its own English
wording, **"TV series"**, which reads at least as well in context, matches the
API's own `TV_SERIES`, and cannot collide. The alternative — a second strings
table, which is Apple's mechanism for exactly this — would have been two more
files for two words.

Finding that was the direct result of translating. It is the usual way these
come to light: English collapses a distinction, and the first other language
pulls it apart.

## Two bugs German exposed

Both pre-dated the translation and were fixed with it. Both were a label that
reached the UI as a `String` **variable** rather than a literal, which means
neither platform localises it:

- **The search result's type badge**, on *both* platforms, hardcoded "Film" and
  "Series". It would have stayed English in every language.
- **The iOS history filter**'s three labels were literals returned from
  `HistoryType.label`. Android localised these from the start, so this was also
  the two apps quietly disagreeing.

`Text("Save")` localises. `Text(someString)` does not, and looks identical in
review. On iOS the fix is `String(localized:)` — Foundation's own, not a
project helper — and on Android `stringResource`, hoisted out of `buildString`
because it is a composable and that lambda is not.

## Adding a language

1. Copy `values/strings.xml` to `values-<code>/strings.xml` and translate the
   values, leaving every `name` alone.
2. Copy both `en.lproj` directories to `<code>.lproj` and translate the right
   hand side of each entry, leaving every key byte-identical.
3. Add the code to `tools/check-translations.py`'s `CATALOGUES`, and to
   `res/xml/locales_config.xml` so Android offers it in the per-app language
   picker.
4. Run `python3 tools/check-translations.py`.

Plural classes are the one thing that is not mechanical. German, French,
Italian, Spanish and Portuguese use the same `one`/`other` pair as English, so
the existing `<plurals>` entries port across directly. Polish, Russian and
Czech need three or four forms each, which means adding `few` and `many` items
rather than translating the two that are there — and the checker will say so,
because a quantity class present in the base and missing in the translation is
reported by name.

## Formatted by the platform

Dates, times and numbers are formatted from the device locale by the platform,
which is why none of them are assembled by hand anywhere in either app. A
German device gets `27.09.2026` with no code of ours involved.

## Still English: everything that comes from TMDB

**The app's own copy is translated. The catalogue's is not.** Titles, plot
overviews, genre names and episode names all come from TMDB, and today they
arrive in TMDB's default language whatever the device is set to. A German user
reads a German interface around an English plot summary.

This is not an oversight in the translation, and it is not a client fix:

- `GET /titles/search` and `GET /titles/trending` **do** take a `language`
  parameter, and the backend's cache keys already include it, so sending it is
  safe. Android currently passes `language = null` explicitly; iOS omits it.
- `GET /titles/{id}` — the detail screen, and the longest prose anybody reads
  — **does not take a language at all.**

So sending the language from the clients today would make search results German
and leave the screen you reach by tapping one of them English. That is worse
than consistent English, so neither app sends it and both are consistent.

Doing it properly is one change across four places, in this order:

1. `TitleController.getTitle` and `CatalogService.title` take a `language`, and
   it joins the title cache key — exactly as `search` and `trending` already do.
2. Regenerate `backend/src/main/resources/openapi/openapi.json`.
3. Regenerate both API clients from it.
4. Have both apps pass the device language on all three calls, together.

Steps 2 and 3 need the OpenAPI generator and a populated Maven repository, so
this is not a change that can be made from an environment without them.
