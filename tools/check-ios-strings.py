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



# A computed property or function that produces a String, which is where the
# copy that never localises hides. `Text("Save")` localises; `Text(someString)`
# does not, and the two are indistinguishable in review -- so a literal
# returned from one of these reaches a view as a variable and stays English in
# every language. This has happened three times in this app: HistoryType.label,
# SearchView.subtitle and DateRange.label, each found only by translating.
#
# The fix at the call site is always the same: String(localized:), which is
# Foundation's own lookup. So this flags a literal that looks like copy inside
# a String-producing body and is not wrapped in it.
STRING_PRODUCER = re.compile(r"(?:var\s+\w+\s*:\s*String|->\s*String)\s*\{")

# Parameter labels whose argument is never user-facing copy.
NOT_COPY = re.compile(
    r"(?:systemImage|forKey|key|named|table|identifier|withIdentifier|path|"
    r"scheme|host|bundle|comment|rawValue|value)\s*:\s*$"
)

LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')



def without_comments(source: str) -> str:
    """Blanks out comments, keeping every byte offset intact.

    Needed because this project's comments quote the copy they are about -- a
    comment explaining why "TV series" is not "Series" was itself reported
    twice. Offsets are preserved by replacing with spaces rather than deleting,
    so line numbers computed afterwards still point at the real source.
    """
    out = list(source)
    index = 0
    length = len(source)
    while index < length:
        char = source[index]
        if char == '"':                       # skip the whole literal
            index += 1
            while index < length:
                if source[index] == "\\":
                    index += 2
                    continue
                if source[index] == '"':
                    index += 1
                    break
                index += 1
            continue
        if source.startswith("//", index):
            while index < length and source[index] != "\n":
                out[index] = " "
                index += 1
            continue
        if source.startswith("/*", index):
            while index < length and not source.startswith("*/", index):
                if source[index] != "\n":
                    out[index] = " "
                index += 1
            continue
        index += 1
    return "".join(out)

def string_producing_bodies(text: str) -> list[tuple[int, str]]:
    """Each String-producing body, as (line number of its start, source)."""
    bodies: list[tuple[int, str]] = []
    for match in STRING_PRODUCER.finditer(text):
        start = match.end() - 1          # the opening brace
        depth = 0
        for index in range(start, len(text)):
            if text[index] == "{":
                depth += 1
            elif text[index] == "}":
                depth -= 1
                if depth == 0:
                    bodies.append((text.count("\n", 0, start) + 1, text[start:index]))
                    break
    return bodies


def unlocalised_copy(bundle: Bundle) -> list[str]:
    found: list[str] = []
    for path in swift_files(bundle):
        real = path.read_text()
        text = without_comments(real)
        # Where String(localized:) already wraps a literal, so those are fine.
        localised = {
            m.start(1) for m in re.finditer(r'String\(\s*localized:\s*(")', text)
        }
        for line_number, body in string_producing_bodies(text):
            offset = text.index(body)
            for literal in LITERAL.finditer(body):
                absolute = offset + literal.start()
                if absolute in localised:
                    continue
                value = literal.group(1)
                # Only what is OUTSIDE the interpolations can be copy. A
                # literal like "\(code) · \(name)" is two values and a
                # separator -- there is nothing in it to translate, and
                # wrapping it would invent a key whose every part is a runtime
                # argument.
                outside = re.sub(r"\\\([^)]*\)", "", value)
                if not re.search(r"[A-Za-z]", outside):
                    continue
                # Copy starts with a capital or contains a space; a bare
                # lowercase word is an identifier or a symbol name.
                if not (value[0].isupper() or " " in value):
                    continue
                before = body[: literal.start()].rstrip()
                if NOT_COPY.search(before + ":") or NOT_COPY.search(before):
                    continue
                line = text.count("\n", 0, absolute) + 1
                found.append(
                    f"{path.relative_to(ROOT)}:{line}: {value!r} is returned as a "
                    f"String, so no view will localise it — wrap it in "
                    f"String(localized:)"
                )
    return found

def main() -> int:
    problems: list[str] = []
    notes: list[str] = []
    summary: list[str] = []

    for bundle in BUNDLES:
        found, said, keys, files = check(bundle)
        problems += found
        problems += unlocalised_copy(bundle)
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
