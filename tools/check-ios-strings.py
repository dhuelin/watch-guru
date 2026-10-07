#!/usr/bin/env python3
"""Checks the iOS string catalogue against the code that uses it.

SwiftUI localises a literal passed to `Text`, `Button`, `Label` and friends by
treating the literal itself as the key. That makes the catalogue additive and
safe -- a missing key falls back to the literal, so nothing breaks visibly --
which is exactly why it rots silently. A string added to a screen and never
added here simply stops being translatable, and nobody finds out until an app
ships half in English.

So this checks the two directions that matter:

- Every localisable literal in the sources has an entry. Otherwise that copy is
  untranslatable and no build will say so.
- The file parses, with no duplicate keys. A `.strings` file with the same key
  twice is ambiguous, and the copy of it that loses is invisible.

Entries with no literal are reported too, but only as a note: a string may
legitimately be looked up at runtime by a key the source does not spell out.

    python3 tools/check-ios-strings.py
"""

from __future__ import annotations

import re
import sys
from collections import Counter
from pathlib import Path
from typing import NamedTuple

ROOT = Path(__file__).resolve().parent.parent


class Bundle(NamedTuple):
    """One thing that ships with its own catalogue.

    There are two, and there have to be: a literal is looked up in the bundle it
    was compiled into, so the widget extension cannot read the app's catalogue.
    Checking only the app would leave every string on the home screen
    untranslatable with nothing to say so -- which is the exact failure this
    script exists to prevent, one bundle over.
    """

    name: str
    sources: Path
    catalogue: Path


BUNDLES = (
    Bundle(
        name="app",
        sources=ROOT / "ios/WatchGuru/Sources",
        catalogue=ROOT / "ios/WatchGuru/Resources/en.lproj/Localizable.strings",
    ),
    Bundle(
        name="widget",
        sources=ROOT / "ios/WatchGuruWidgets",
        catalogue=ROOT / "ios/WatchGuruWidgets/Resources/en.lproj/Localizable.strings",
    ),
)

# The initialisers that take a LocalizedStringKey, so their literal is a key.
# A literal carrying interpolation is deliberately not matched: it is not a
# constant key, and each such case needs its own decision rather than a guess.
LITERAL_PATTERNS = [
    r'\bText\("([^"\\]+)"\)',
    r'\bnavigationTitle\("([^"\\]+)"\)',
    r'\bButton\("([^"\\]+)"',
    r'\bLabel\("([^"\\]+)"',
    r'\bToggle\("([^"\\]+)"',
    r'\bPicker\("([^"\\]+)"',
    r'\bDatePicker\(\s*"([^"\\]+)"',
    r'\bSection\("([^"\\]+)"\)',
    r'\bContentUnavailableView\(\s*\n?\s*"([^"\\]+)"',
    r'\baccessibilityLabel\("([^"\\]+)"\)',
    r'\bTextField\("([^"\\]+)"',
    r'prompt: "([^"\\]+)"',
    r'description: Text\("([^"\\]+)"\)',
    # A NavigationLink's title is a LocalizedStringKey like any other, and the
    # Profile screen is almost entirely made of them -- this pattern's absence
    # is how "Viewing history" sat outside the catalogue unnoticed.
    r'\bNavigationLink\("([^"\\]+)"',
    # The one lookup that is explicit rather than implicit: a sentence a model
    # produces cannot be a literal in a view, so it asks for its own key.
    r'\bString\(localized: "([^"\\]+)"\)',
    # The widget's own. A widget's copy appears in three places the app's never
    # does -- the widget gallery, the Shortcuts app, and a Siri phrase -- and all
    # three take a LocalizedStringResource built from a literal.
    r'\bconfigurationDisplayName\("([^"\\]+)"\)',
    r'\.description\("([^"\\]+)"\)',
    r'\bIntentDescription\("([^"\\]+)"\)',
    r'\bLocalizedStringResource = "([^"\\]+)"',
    r'@Parameter\(title: "([^"\\]+)"\)',
    # This project's own wrapper around the empty and signed-out states. It takes
    # a LocalizedStringKey, so its argument is a key like any other.
    r'\bMessage\("([^"\\]+)"\)',
]

ENTRY = re.compile(r'^\s*"((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;\s*$')


def literals_in(bundle: Bundle) -> set[str]:
    found: set[str] = set()
    for path in swift_files(bundle):
        text = path.read_text()
        for pattern in LITERAL_PATTERNS:
            found.update(re.findall(pattern, text, re.S))
    return found


def swift_files(bundle: Bundle) -> list[Path]:
    """This bundle's own sources.

    Not the app files the widget target also compiles: those carry no user-facing
    copy, and the app's scan already covers them. If one ever grows a literal,
    the app's catalogue is where it belongs -- the widget compiles them for their
    logic, not their words.
    """
    return sorted(bundle.sources.rglob("*.swift"))


def entries_in(catalogue: Path) -> tuple[Counter[str], list[str]]:
    """Declared keys with their counts, and any lines that are not entries."""
    keys: Counter[str] = Counter()
    unparsed: list[str] = []
    inside_comment = False

    for number, raw in enumerate(catalogue.read_text().splitlines(), start=1):
        line = raw.strip()
        if inside_comment:
            inside_comment = "*/" not in line
            continue
        if not line or line.startswith("//"):
            continue
        if line.startswith("/*"):
            inside_comment = "*/" not in line
            continue

        match = ENTRY.match(line)
        if match:
            keys[match.group(1).replace('\\"', '"').replace("\\\\", "\\")] += 1
        else:
            unparsed.append(f"{catalogue}:{number}: not an entry: {line}")
    return keys, unparsed


def check(bundle: Bundle) -> tuple[list[str], list[str], int, int]:
    """Problems, notes, key count and file count for one bundle."""
    if not bundle.catalogue.exists():
        return [f"No catalogue at {bundle.catalogue}"], [], 0, 0

    keys, problems = entries_in(bundle.catalogue)
    problems += [
        f"{bundle.catalogue}: {key!r} is declared {n} times"
        for key, n in keys.items()
        if n > 1
    ]

    literals = literals_in(bundle)
    problems += [
        f"[{bundle.name}] {key!r} is shown in the app but has no entry in "
        f"{bundle.catalogue.parent.parent.parent.name}/{bundle.catalogue.name}"
        for key in sorted(literals - set(keys))
    ]

    notes = [
        f"note: [{bundle.name}] {key!r} is in the catalogue but no literal uses it"
        for key in sorted(set(keys) - literals)
    ]
    return problems, notes, len(keys), len(swift_files(bundle))


def main() -> int:
    problems: list[str] = []
    notes: list[str] = []
    summary: list[str] = []

    for bundle in BUNDLES:
        found, said, keys, files = check(bundle)
        problems += found
        notes += said
        summary.append(f"{bundle.name}: {keys} keys over {files} source files")

    for problem in problems:
        print(problem, file=sys.stderr)
    if problems:
        return 1

    for note in notes:
        print(note)
    print("every literal accounted for — " + "; ".join(summary))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
