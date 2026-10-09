#!/usr/bin/env python3
"""Checks every translation against the English it is a translation of.

A missing or mistyped translation is invisible. Both platforms fall back to the
base language for a key they cannot find, so a German build with half its
catalogue missing looks like a German build with some English in it -- and on
iOS, where the key *is* the English sentence, a single changed character in the
key is indistinguishable from no translation at all. Nothing in either build
says a word about it.

Three things are checked, and the third is the one that crashes:

- Every key in the base catalogue has a translation. Extra keys are reported
  too: they are almost always a key that was reworded on one side only, which
  means one real string is now falling back silently.
- Android plural entries carry the same quantity classes as the base, because
  a missing `other` is a runtime failure for every count but one.
- Format specifiers match, as a multiset. "%1$d von %2$d" may reorder, and in
  many languages must, but it may not lose a specifier or gain one: on iOS a
  format with more specifiers than arguments reads past the end of the argument
  list, which is a crash rather than bad copy.

    python3 tools/check-translations.py
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import NamedTuple

ROOT = Path(__file__).resolve().parent.parent


class Catalogue(NamedTuple):
    """One bundle's copy, in one language."""

    name: str
    base: Path
    translations: dict[str, Path]


CATALOGUES = (
    Catalogue(
        name="android",
        base=ROOT / "android/app/src/main/res/values/strings.xml",
        translations={"de": ROOT / "android/app/src/main/res/values-de/strings.xml"},
    ),
    Catalogue(
        name="ios app",
        base=ROOT / "ios/WatchGuru/Resources/en.lproj/Localizable.strings",
        translations={"de": ROOT / "ios/WatchGuru/Resources/de.lproj/Localizable.strings"},
    ),
    Catalogue(
        name="ios widget",
        base=ROOT / "ios/WatchGuruWidgets/Resources/en.lproj/Localizable.strings",
        translations={"de": ROOT / "ios/WatchGuruWidgets/Resources/de.lproj/Localizable.strings"},
    ),
)

# Android's positional form and the plain form, plus iOS's %@ and %lld. Matched
# together because a translation may legitimately move from one form to the
# other, and what matters is which conversions are present, not their order.
SPECIFIER = re.compile(r"%(?:\d+\$)?(?:lld|ld|[sdf@])")

STRINGS_ENTRY = re.compile(r'^\s*"((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;\s*$')


def android_entries(path: Path) -> dict[str, str]:
    """Strings and plural items, keyed so a plural's classes are comparable."""
    entries: dict[str, str] = {}
    for element in ET.parse(path).getroot():
        name = element.get("name")
        if element.tag == "string":
            entries[name] = "".join(element.itertext())
        elif element.tag == "plurals":
            for item in element:
                entries[f"{name}[{item.get('quantity')}]"] = "".join(item.itertext())
    return entries


def strings_entries(path: Path) -> dict[str, str]:
    """A .strings file, with its comments and blank lines skipped."""
    entries: dict[str, str] = {}
    inside_comment = False
    for raw in path.read_text().splitlines():
        line = raw.strip()
        if inside_comment:
            inside_comment = "*/" not in line
            continue
        if not line or line.startswith("//"):
            continue
        if line.startswith("/*"):
            inside_comment = "*/" not in line
            continue
        match = STRINGS_ENTRY.match(line)
        if match:
            key = match.group(1).replace('\\"', '"').replace("\\\\", "\\")
            entries[key] = match.group(2)
    return entries


def read(path: Path) -> dict[str, str]:
    return android_entries(path) if path.suffix == ".xml" else strings_entries(path)


def compare(catalogue: Catalogue, language: str, path: Path) -> list[str]:
    where = f"[{catalogue.name} {language}]"
    if not path.exists():
        return [f"{where} no catalogue at {path.relative_to(ROOT)}"]

    base = read(catalogue.base)
    translated = read(path)
    problems: list[str] = []

    for key in sorted(base.keys() - translated.keys()):
        problems.append(f"{where} {key!r} has no translation")
    for key in sorted(translated.keys() - base.keys()):
        problems.append(
            f"{where} {key!r} is translated but is not in the base catalogue "
            f"— reworded on one side only?"
        )

    for key in sorted(base.keys() & translated.keys()):
        expected = sorted(SPECIFIER.findall(base[key]))
        actual = sorted(SPECIFIER.findall(translated[key]))
        if expected != actual:
            problems.append(
                f"{where} {key!r} has specifiers {actual} but the base has "
                f"{expected}"
            )
    return problems


def main() -> int:
    problems: list[str] = []
    summary: list[str] = []

    for catalogue in CATALOGUES:
        if not catalogue.base.exists():
            problems.append(f"No base catalogue at {catalogue.base}")
            continue
        count = len(read(catalogue.base))
        for language, path in sorted(catalogue.translations.items()):
            problems += compare(catalogue, language, path)
        languages = ", ".join(sorted(catalogue.translations)) or "none"
        summary.append(f"{catalogue.name}: {count} entries × {languages}")

    for problem in problems:
        print(problem, file=sys.stderr)
    if problems:
        return 1

    print("every translation matches its base — " + "; ".join(summary))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
