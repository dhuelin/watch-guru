#!/usr/bin/env python3
"""Checks Swift calls to generated models against their own initialisers.

Swift requires named arguments in the order the initialiser declares them, and
the generator declares model fields alphabetically -- so an argument list that
reads naturally ("episodeId, titleId, watchedAt, clientRef") is a compile error
and an argument list that reads like a filing cabinet is correct. There is no
way to tell by looking, and the compiler that would say so is macOS-only, which
means the first thing to see the mistake is CI.

So this reads each generated model's `public init(...)`, then every call to it
in the app's own sources, and reports:

- an argument the initialiser has no parameter for, and
- arguments given out of declaration order.

Kotlin is deliberately not checked: named arguments there may appear in any
order, so the same call site cannot be wrong in this way.

    python3 tools/check-generated-call-sites.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODELS = ROOT / "ios/WatchGuruAPI/Sources/WatchGuruAPI/Models"
SOURCES = ROOT / "ios/WatchGuru/Sources"

INIT = re.compile(r"public init\((.*?)\)\s*\{", re.S)
PARAMETER = re.compile(r"(?:^|,)\s*(\w+)\s*:")


def parameters_by_model() -> dict[str, list[str]]:
    """Each generated model's initialiser parameters, in declaration order."""
    declared: dict[str, list[str]] = {}
    for path in sorted(MODELS.glob("*.swift")):
        match = INIT.search(path.read_text())
        if match:
            declared[path.stem] = PARAMETER.findall(match.group(1))
    return declared


def arguments(text: str, start: int) -> list[str] | None:
    """Top-level argument labels of the call whose "(" is at `start`.

    Nested calls, subscripts and string interpolation all contain their own
    labels, so depth is tracked rather than the text being split on commas.
    Returns None for an unbalanced call, which is not this script's business
    to diagnose.
    """
    labels: list[str] = []
    depth = 0
    index = start
    segment_start = start + 1

    while index < len(text):
        character = text[index]
        if character in "([{":
            depth += 1
        elif character in ")]}":
            depth -= 1
            if depth == 0:
                break
        elif character == '"':
            # Skip the whole literal: an interpolation inside it is not an
            # argument of this call.
            index += 1
            while index < len(text) and text[index] != '"':
                index += 2 if text[index] == "\\" else 1
        elif character == "," and depth == 1:
            labels.append(text[segment_start:index])
            segment_start = index + 1
        index += 1
    else:
        return None

    labels.append(text[segment_start:index])
    return [
        label.split(":", 1)[0].strip()
        for label in labels
        if ":" in label.split("\n")[0] or ":" in label
    ]


def problems() -> list[str]:
    declared = parameters_by_model()
    found: list[str] = []

    for path in sorted(SOURCES.rglob("*.swift")):
        text = path.read_text()
        for model, parameters in declared.items():
            for match in re.finditer(rf"\b{model}\(", text):
                labels = arguments(text, match.end() - 1)
                if labels is None:
                    continue
                labels = [label for label in labels if label.isidentifier()]
                line = text.count("\n", 0, match.start()) + 1

                unknown = [label for label in labels if label not in parameters]
                if unknown:
                    found.append(
                        f"{path}:{line}: {model} has no parameter "
                        f"{', '.join(sorted(unknown))}"
                    )
                    continue

                positions = [parameters.index(label) for label in labels]
                if positions != sorted(positions):
                    found.append(
                        f"{path}:{line}: {model} arguments are out of declaration order: "
                        f"got {', '.join(labels)}; "
                        f"expected {', '.join(p for p in parameters if p in labels)}"
                    )
    return found


def main() -> int:
    if not MODELS.is_dir():
        print(f"No generated models at {MODELS}", file=sys.stderr)
        return 1

    found = problems()
    for problem in found:
        print(problem, file=sys.stderr)
    if found:
        return 1

    print(
        f"every call to the {len(parameters_by_model())} generated models "
        f"names parameters that exist, in declaration order"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
