#!/usr/bin/env python3
"""Checks the Android resource XML the way aapt does, without an Android SDK.

There is no SDK in every environment this repository is worked on from, so the
first thing to see these files is often CI. Two classes of mistake have reached
it that way, and both are cheap to catch here:

- XML that is not well formed, including a comment containing "--", which is
  legal-looking and which aapt rejects outright.
- Two resources with the same name in one file. The XML is perfectly valid;
  the merger fails with "Found item String/x more than one time".

    python3 tools/check-android-resources.py

Exits non-zero and names the file and resource on the first problem found.
"""

from __future__ import annotations

import sys
import xml.dom.minidom
from collections import defaultdict
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "android/app/src/main/res"


def problems_in(path: Path) -> list[str]:
    try:
        document = xml.dom.minidom.parse(str(path))
    except Exception as error:  # noqa: BLE001 - the parser's message is the useful part
        return [f"{path}: not well formed: {error}"]

    root = document.documentElement
    if root is None or root.tagName != "resources":
        return []

    # Keyed by (tag, name): aapt allows a string and a plural to share a name,
    # but not two strings.
    seen: dict[tuple[str, str], int] = defaultdict(int)
    for node in root.childNodes:
        if node.nodeType != node.ELEMENT_NODE:
            continue
        name = node.getAttribute("name")
        if name:
            seen[(node.tagName, name)] += 1

    return [
        f"{path}: {tag}/{name} is declared {count} times"
        for (tag, name), count in sorted(seen.items())
        if count > 1
    ]


def main() -> int:
    files = sorted(RES.rglob("*.xml"))
    if not files:
        print(f"No resource files under {RES}", file=sys.stderr)
        return 1

    found = [problem for path in files for problem in problems_in(path)]
    for problem in found:
        print(problem, file=sys.stderr)

    if found:
        return 1
    print(f"{len(files)} resource files are well formed with no duplicate names")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
