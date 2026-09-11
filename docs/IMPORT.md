# Importing an existing viewing history

Nobody starts from zero, and an empty library is the most common reason a
tracking app gets deleted on day one. Watch Guru reads four kinds of file.

Every import happens in two steps: a **preview** that adds nothing to your
library, and a **commit** that writes only the rows you accepted. An import
that silently marked four hundred titles watched — some of them wrongly —
would be worse than no import at all, and undoing it by hand is the worst
first hour an app can offer.

The preview does write one thing, and it is not yours: a title the metadata
provider knows and this server does not is added to the shared catalogue, so
the row can be shown to you with a real name and poster. That is a cache
everybody draws on and nobody owns. Your library, your history and your
ratings are untouched until you commit.

## What it reads

The format is worked out from the header row. You do not pick it, because
everybody knows which site they downloaded a file from and nobody knows which
of four radio buttons matches its columns.

| Source | Where to get it | What comes across |
|---|---|---|
| **Watch Guru** | Documented below | Everything, including episodes by number |
| **IMDb** | Your Lists or Ratings → Export | IMDb id, title, year, your rating, and the date you *rated* it |
| **Letterboxd** | Settings → Import & Export → Export | Films, watch dates, ratings out of five |
| **Netflix** | Account → Profile → Viewing activity → Download all | Titles and dates; episodes by name |

### Where each source is less than it looks

These are told to you in the preview too, because they are the places where a
file does not mean quite what it appears to.

- **IMDb exports have no watch date.** They carry the date you rated something,
  which is the closest thing available, and that is what gets imported.
- **Netflix does not number episodes.** Its export says
  `Breaking Bad: Season 5: Ozymandias`, so the episode is matched by name.
  Anything that cannot be found is listed for you rather than guessed at.
  Series whose own names contain a colon — `Star Trek: Strange New Worlds` —
  are read correctly: the season segment is searched for rather than assumed
  to be the second one.
- **Letterboxd rates out of five**, in halves. Ratings are doubled onto this
  app's ten-point scale.
- **Letterboxd is films only**, which is what keeps *Fargo* the film from
  matching *Fargo* the series.

## The Watch Guru format

A UTF-8 CSV with a header row. Only `title` is required.

```csv
title,year,type,imdb_id,season,episode,watched_at,rating
Fargo,1996,movie,tt0116282,,,2024-01-15,8
Breaking Bad,2008,series,tt0903747,5,14,2024-05-01,9.5
```

| Column | Meaning |
|---|---|
| `title` | **Required.** What it is called |
| `year` | Release year. The single most useful way to tell a remake from what it remade |
| `type` | `movie` or `series` |
| `imdb_id` | `tt…`. Worth more than title and year together, because it is exact |
| `season`, `episode` | Both, to record one episode |
| `watched_at` | `YYYY-MM-DD`. Missing means today |
| `rating` | 0–10, decimals allowed. Applied to the library entry, but never over a rating you gave in this app — that one was typed deliberately |

Quoted fields, commas inside titles, doubled quotes and embedded newlines are
all handled — they occur in real exports constantly.

## Matching

In order of how much the evidence is worth:

1. **An IMDb id**, which is exact.
2. **An exact name in your catalogue**, narrowed by type and then by year.
3. **A search against TMDB**, for rows the first two did not settle. Only an
   exact name match counts: the provider searches loosely and orders by
   popularity, so taking its first answer would file *Heat* under whatever is
   popular this month. Several exact matches are left for you rather than
   decided for you.

The third step is budgeted — 50 rows per file by default, see
`watch-guru.imports.provider-lookups-per-import`. A decade of Netflix history
is thousands of rows, and firing thousands of searches upstream on one button
press is how an API key gets suspended. Rows past the budget come back saying
they were **not looked up**, which you can act on by importing the file again,
rather than **not found**, which would be a claim the import had not earned.

A name that matches more than one title comes back as ambiguous with the
candidates, for you to choose between. Nothing ambiguous is imported by
default.

## Running the same file twice

Nothing is duplicated. Every row carries a reference derived from its contents
— the IMDb id, the Letterboxd URI, or a hash of the fields — and the database
holds a unique index on `(user, origin, origin_ref)`. The second run reports
the rows as already imported and writes nothing.

That reference is deliberately **not** the line number. Line numbers look
stable inside one file and are not stable between two, so a second export would
collide with the first and rows would vanish silently.

Nor is it the title alone. Two Letterboxd diary entries for two viewings of the
same film share a URI, so the watch date is part of the reference — otherwise a
rewatch would look like a duplicate of the original and disappear.

Dates are recorded at midday **in your own time zone**, which the app sends when
it registers for notifications. Midnight would land on the previous evening for
anyone west of Greenwich; midday UTC lands on the next day for anyone at
UTC+13.

## What is not here yet

**Trakt.** It is the highest-value source, and it needs OAuth credentials and
an app registration with Trakt — see #21. The file formats above cover the
common cases without asking anybody to authorise anything.

**Undo.** A committed import can be unpicked title by title, which is tedious
for four hundred rows. Preview is what stands in for it: look before you agree.
