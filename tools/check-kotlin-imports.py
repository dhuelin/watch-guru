#!/usr/bin/env python3
"""Checks that every top-level Kotlin function is called with its import.

Kotlin needs an import per top-level function, not per package: importing
`UiState` does not bring in `contentOrNull`, even though they are declared in
the same file. Miss one and the compiler fails with "unresolved reference" --
which is cheap to fix and expensive to find, because the only thing that says
so is a full Gradle build. This caught exactly that on the notification work
after it had already cost a CI cycle.

Extension functions are the ones that get missed, because the call site reads
like a method on the receiver -- `state.contentOrNull()` looks like it belongs
to `UiState` and needs nothing. So they are the main subject here, and the
first version of this check missed them for that very reason: it excluded any
call preceded by a dot, to avoid matching ordinary member calls, which
excluded every extension call too.

Deliberately syntactic, not a parser. It reads top-level `fun` declarations
(anchored at column 0, so members are out of scope) and looks for calls to
them. That makes two kinds of mistake possible:

- A member function sharing a name with a non-private top-level one is
  reported when it needs nothing. Rare, and the fix is to rename one of them,
  which is worth doing anyway. A *declaration* sharing a name is not reported:
  `data class LogFilmWatched(` is a name being introduced, not a call.
- A function reached through a star import of its package is accepted, which
  is correct, and this project does not use star imports.

    python3 tools/check-kotlin-imports.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "android/app/src/main/kotlin"

# A top-level `fun`, anchored at column 0 so indented members are excluded.
# Group 1 is the receiver type when the function is an extension; group 2 is
# the name. `@Composable` sits on its own line above and so does not interfere.
#
# `private` is deliberately NOT matched. A private top-level function cannot be
# imported from anywhere, so a call to that name in another file is necessarily
# something else -- a constructor, a member, a private function of its own. Two
# of this project's screens each have a private `EpisodeRow`, and including
# private declarations reported both of them.
DECLARATION = re.compile(
    r"^(?:internal |public )?fun (?:<[^>]+> )?"
    r"(?:([A-Za-z_][\w.<>, ?]*)\.)?([A-Za-z_]\w*)\s*\(",
    re.M,
)

PACKAGE = re.compile(r"^package (\S+)", re.M)
IMPORT = re.compile(r"^import (\S+)", re.M)


def declarations() -> dict[str, tuple[set[str], bool]]:
    """Every top-level function: name to its packages and whether it extends."""
    found: dict[str, tuple[set[str], bool]] = {}
    for path in sorted(SOURCES.rglob("*.kt")):
        text = path.read_text()
        package = PACKAGE.search(text).group(1)
        for receiver, name in DECLARATION.findall(text):
            packages, extension = found.get(name, (set(), False))
            packages.add(package)
            found[name] = (packages, extension or bool(receiver))
    return found


def call_pattern(name: str, extension: bool) -> re.Pattern[str]:
    """How a call to this function looks.

    An extension is called through a dot, so the dot must be *allowed* here.
    Anything else is called bare, and there the dot must be excluded or every
    `something.name()` member call would match.
    """
    if extension:
        return re.compile(r"\." + re.escape(name) + r"\s*\(")
    return re.compile(r"(?<![\w.])" + re.escape(name) + r"\s*\(")


# What introduces a name rather than using one. Without this, a nested
# `data class LogFilmWatched(` reads as a call to the composable of the same
# name two packages away -- which is how this check first reported a file that
# imports nothing and calls nothing.
INTRODUCES = re.compile(
    r"\b(?:class|object|interface|fun|val|var|typealias|enum class|"
    r"annotation class|value class)\s+$"
)


def is_declaration(text: str, start: int) -> bool:
    """Whether the match at `start` is a name being declared, not called."""
    line_start = text.rfind("\n", 0, start) + 1
    return bool(INTRODUCES.search(text[line_start:start]))


def problems_in(path: Path, known: dict[str, tuple[set[str], bool]]) -> list[str]:
    text = path.read_text()
    package = PACKAGE.search(text).group(1)
    imports = set(IMPORT.findall(text))
    relative = path.relative_to(ROOT)

    found: list[str] = []
    for name, (packages, extension) in known.items():
        # Same package: visible without an import.
        if package in packages:
            continue
        calls = [
            match for match in call_pattern(name, extension).finditer(text)
            if not is_declaration(text, match.start())
        ]
        if not calls:
            continue
        if any(f"{p}.{name}" in imports or f"{p}.*" in imports for p in packages):
            continue
        kind = "extension" if extension else "function"
        found.append(
            f"{relative}: calls the {kind} {name}() declared in "
            f"{', '.join(sorted(packages))} with no matching import"
        )
    return found


def main() -> int:
    if not SOURCES.is_dir():
        print(f"No Kotlin sources at {SOURCES}", file=sys.stderr)
        return 1

    known = declarations()
    files = sorted(SOURCES.rglob("*.kt"))

    problems: list[str] = []
    for path in files:
        problems += problems_in(path, known)

    for problem in sorted(problems):
        print(problem, file=sys.stderr)
    if problems:
        return 1

    print(
        f"every call to the {len(known)} top-level functions resolves "
        f"to an import, across {len(files)} files"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
