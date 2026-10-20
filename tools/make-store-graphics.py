#!/usr/bin/env python3
"""Cut the Play Console's listing graphics from the artwork in docs/brand/.

    python3 tools/make-store-graphics.py
    python3 tools/make-store-graphics.py --check

Play asks for a 512x512 icon and a 1024x500 feature graphic, both PNG. It does
not ask for screenshots to be generated, and they cannot be: a screenshot has
to come off a running app, and nothing here has run on a device yet. See #35.

The icon is the shipped iOS icon at half size rather than a second cut of the
artwork, so the two can never disagree -- Play masks it to a rounded square
itself, which is why it stays a full-bleed square with its own corners square.

The feature graphic is composed rather than cropped. Its parts are lifted off
the wordmark artwork with a luminance key: that artwork sits on a nearly flat
near-black, so keying on brightness takes the mark and the lettering with
their glow intact and leaves the field behind, which a rectangular paste onto
a different background would not.
"""
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
BRAND = ROOT / "docs/brand"
ICON_1024 = ROOT / "ios/WatchGuru/Resources/Assets.xcassets/AppIcon.appiconset/icon-1024.png"
WORDMARK_SRC = BRAND / "wordmark-source.webp"

# Where the wordmark artwork splits: the mark above, the lettering below. Found
# by the empty band of rows between them rather than guessed.
SPLIT = 832
# The field is not quite flat -- it carries a faint vignette that peaks
# around luminance 14 -- so the floor sits above that. Below it, the vignette
# keys in as a dim rectangle of the source's own corners.
KEY_FLOOR, KEY_CEIL = 17, 36

FEATURE = (1024, 500)
BACKDROP = ((0x24, 0x10, 0x55), (0x0C, 0x05, 0x1C))   # top, bottom


def luminance(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def keyed(im):
    """The artwork with its near-black field turned transparent.

    A soft key rather than a threshold, so the glow around the mark fades out
    instead of ending on a hard circle.
    """
    out = Image.new("RGBA", im.size)
    px, op = im.load(), out.load()
    for y in range(im.height):
        for x in range(im.width):
            c = px[x, y]
            a = (luminance(c) - KEY_FLOOR) / (KEY_CEIL - KEY_FLOOR)
            op[x, y] = (*c, max(0, min(255, round(a * 255))))
    return out


def trim(im):
    """Crop to what the key left behind."""
    box = im.getbbox()
    return im.crop(box) if box else im


def gradient(size, top, bottom):
    g = Image.new("RGB", size)
    gp = g.load()
    for y in range(size[1]):
        f = y / max(size[1] - 1, 1)
        row = tuple(round(top[i] + (bottom[i] - top[i]) * f) for i in range(3))
        for x in range(size[0]):
            gp[x, y] = row
    return g


def render():
    icon512 = Image.open(ICON_1024).convert("RGB").resize((512, 512), Image.LANCZOS)

    art = Image.open(WORDMARK_SRC).convert("RGB")
    mark = trim(keyed(art.crop((0, 0, art.width, SPLIT))))
    lettering = trim(keyed(art.crop((0, SPLIT, art.width, art.height))))

    w, h = FEATURE
    canvas = gradient(FEATURE, *BACKDROP).convert("RGBA")

    # The mark on the left, the lettering beside it, both on the centre line.
    # Sized to a margin rather than to fixed pixels: Play crops this graphic on
    # some surfaces, and the two parts have to stay inside whatever it leaves.
    MARGIN, GAP = 64, 40
    mark_h, letter_w = 370, 430
    mark = mark.resize((round(mark.width * mark_h / mark.height), mark_h), Image.LANCZOS)
    lettering = lettering.resize(
        (letter_w, round(lettering.height * letter_w / lettering.width)), Image.LANCZOS)

    room = w - 2 * MARGIN
    block = mark.width + GAP + lettering.width
    if block > room:
        f = room / block
        mark = mark.resize((round(mark.width * f), round(mark.height * f)), Image.LANCZOS)
        lettering = lettering.resize(
            (round(lettering.width * f), round(lettering.height * f)), Image.LANCZOS)
        block = mark.width + GAP + lettering.width

    x = (w - block) // 2
    canvas.alpha_composite(mark, (x, (h - mark.height) // 2))
    canvas.alpha_composite(lettering, (x + mark.width + GAP, (h - lettering.height) // 2))

    # Play rejects a feature graphic with an alpha channel.
    return icon512, canvas.convert("RGB")


def main(argv):
    check = "--check" in argv
    icon512, feature = render()
    targets = [(BRAND / "play-icon-512.png", icon512),
               (BRAND / "play-feature-graphic.png", feature)]
    stale = []
    for path, img in targets:
        if check:
            if not path.exists() or Image.open(path).convert("RGBA").tobytes() \
                    != img.convert("RGBA").tobytes():
                stale.append(path.relative_to(ROOT))
        else:
            img.save(path)
            print("wrote", path.relative_to(ROOT), img.size)
    if stale:
        print("stale, re-run without --check:", *stale, sep="\n  ")
        return 1
    if check:
        print("store graphics match the artwork")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
