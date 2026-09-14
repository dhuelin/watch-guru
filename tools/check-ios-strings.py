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

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "ios/WatchGuru/Sources"
CATALOGUE = ROOT / "ios/WatchGuru/Resources/en.lproj/Localizable.strings"

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
]

ENTRY = re.compile(r'^\s*"((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;\s*$')


def literals_in_sources() -> set[str]:
    found: set[str] = set()
    for path in sorted(SOURCES.rglob("*.swift")):
        text = path.read_text()
        for pattern in LITERAL_PATTERNS:
            found.update(re.findall(pattern, text, re.S))
    return found


def entries_in_catalogue() -> tuple[Counter[str], list[str]]:
    """Declared keys with their counts, and any lines that are not entries."""
    keys: Counter[str] = Counter()
    unparsed: list[str] = []
    inside_comment = False

    for number, raw in enumerate(CATALOGUE.read_text().splitlines(), start=1):
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
            unparsed.append(f"{CATALOGUE}:{number}: not an entry: {line}")
    return keys, unparsed


def main() -> int:
    if not CATALOGUE.exists():
        print(f"No catalogue at {CATALOGUE}", file=sys.stderr)
        return 1

    keys, problems = entries_in_catalogue()
    problems += [f"{CATALOGUE}: {key!r} is declared {n} times" for key, n in keys.items() if n > 1]

    literals = literals_in_sources()
    problems += [
        f"{key!r} is shown in the app but has no entry in {CATALOGUE.name}"
        for key in sorted(literals - set(keys))
    ]

    for problem in problems:
        print(problem, file=sys.stderr)
    if problems:
        return 1

    unused = sorted(set(keys) - literals)
    for key in unused:
        print(f"note: {key!r} is in the catalogue but no literal uses it")
    print(f"{len(keys)} keys, every literal in {len(list(SOURCES.rglob('*.swift')))} "
          f"source files accounted for")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
