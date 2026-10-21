#!/usr/bin/env python3
"""Finds user-visible text in Compose that is a literal rather than a resource.

    python3 tools/check-compose-strings.py

The sibling of check-ios-strings.py, and it exists for the same reason: the
apps claim to ship in German, nothing about a hardcoded English string fails a
build, and so the only thing standing between the claim and a screen full of
English is somebody remembering. Seven had already slipped through when this
was written.

### What counts

Only the text a Compose control is actually given -- the argument of `Text(`
and anything assigned to `text =`. That is deliberately narrower than every
string literal in the file: a resource key, a log line, a JSON filename and a
test tag are all literals too, and flagging them would make this noisy enough
to start ignoring.

A literal is reported when it still holds two consecutive letters after its
`${...}` interpolations are removed. That is what separates prose from the
punctuation that genuinely does not translate -- `" / "` between two counts,
`" · "` between a code and a name -- and it is why those are left alone.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
UI = ROOT / "android/app/src/main/kotlin"

# A Compose control's own text, wherever the call happens to wrap.
TEXT_CALL = re.compile(r'\bText\s*\(')
DESCRIPTION = re.compile(r'\bcontentDescription\s*=\s*(?!null)')
SIMPLE_INTERPOLATION = re.compile(r'\$[A-Za-z_][A-Za-z0-9_]*')
LETTERS = re.compile(r'[A-Za-z]{2}')


def literals(expression):
    """Every double-quoted literal in a stretch of Kotlin, interpolations gone.

    The interpolations are dropped here rather than afterwards because they
    are the only way to scan this correctly: `"${a.let { " x " }}"` holds a
    string inside a template inside a string, and anything that just looks for
    the next quote character stops in the wrong place and reports the wreckage
    as prose. Brace depth is tracked, and quotes inside a template are skipped
    along with everything else in it.
    """
    out, i = [], 0
    while i < len(expression):
        if expression[i] != '"':
            i += 1
            continue
        i += 1
        buf = []
        while i < len(expression):
            c = expression[i]
            if c == "\\":
                buf.append(expression[i:i + 2])
                i += 2
                continue
            if c == '"':
                i += 1
                break
            if c == "$" and i + 1 < len(expression) and expression[i + 1] == "{":
                depth, i = 0, i + 1
                while i < len(expression):
                    if expression[i] == "{":
                        depth += 1
                    elif expression[i] == "}":
                        depth -= 1
                        if depth == 0:
                            i += 1
                            break
                    elif expression[i] == '"':
                        # A string inside the template. Skip it whole, or its
                        # closing quote is mistaken for this literal's.
                        i += 1
                        while i < len(expression) and expression[i] != '"':
                            i += 2 if expression[i] == "\\" else 1
                    i += 1
                continue
            buf.append(c)
            i += 1
        out.append(SIMPLE_INTERPOLATION.sub("", "".join(buf)))
    return out


def call_span(source, open_paren):
    """From an opening parenthesis to its match, strings skipped.

    Whole-call rather than one line, because a Text( often wraps and the
    literal is rarely on the first line of it -- `season.name ?: "Season N"`
    sits three lines inside the call that displays it.
    """
    depth, i = 0, open_paren
    while i < len(source):
        c = source[i]
        if c == '"':
            i += 1
            while i < len(source) and source[i] != '"':
                i += 2 if source[i] == "\\" else 1
        elif c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return source[open_paren:i + 1]
        i += 1
    return source[open_paren:]


def findings():
    for path in sorted(UI.rglob("*.kt")):
        source = path.read_text()
        spans = []
        for m in TEXT_CALL.finditer(source):
            spans.append((m.start(), call_span(source, m.end() - 1)))
        for m in DESCRIPTION.finditer(source):
            spans.append((m.start(), source[m.end():source.find("\n", m.end())]))
        for offset, span in spans:
            if "stringResource" in span or "pluralStringResource" in span:
                continue
            for literal in literals(span):
                if LETTERS.search(literal):
                    line = source.count("\n", 0, offset) + 1
                    yield path.relative_to(ROOT), line, literal
                    break


# What a fix for the above needs in scope. Checked because the fix is
# mechanical and the omission is not caught by anything else here: these are
# library functions, so check-kotlin-imports.py -- which resolves calls to
# this project's own top-level functions -- does not see them, and the result
# is a compile error introduced by the act of fixing a string.
NEEDED = {
    "stringResource(": "androidx.compose.ui.res.stringResource",
    "pluralStringResource(": "androidx.compose.ui.res.pluralStringResource",
    "R.string.": "dev.huelin.watchguru.R",
    "R.plurals.": "dev.huelin.watchguru.R",
}


def missing_imports():
    for path in sorted(UI.rglob("*.kt")):
        source = path.read_text()
        for call, symbol in NEEDED.items():
            if call in source and "import %s\n" % symbol not in source:
                yield path.relative_to(ROOT), call, symbol


def main():
    found = list(findings())
    for path, number, literal in found:
        print("%s:%d: user-visible literal %r" % (path, number, literal))
    for path, call, symbol in missing_imports():
        print("%s: uses %s without `import %s`" % (path, call, symbol))
    unimported = list(missing_imports())
    if found:
        print("\n%d string%s shown to the user is not a resource, so it cannot be"
              % (len(found), "" if len(found) == 1 else "s"))
        print("translated. Move it to res/values/strings.xml and read it with")
        print("stringResource, or pluralStringResource where it counts something.")
    if found or unimported:
        return 1
    print("every string Compose displays comes from a resource, and every file"
          " that reads one imports what it needs")
    return 0


if __name__ == "__main__":
    sys.exit(main())
