#!/usr/bin/env python3
"""Cut both platforms' launcher icons from docs/brand/app-icon-source.webp.

    python3 tools/make-app-icon.py           # writes into the two app trees
    python3 tools/make-app-icon.py --check   # fails if what is committed differs

Needs Pillow, and nothing else. It is not wired into CI: the icons change when
the artwork does, which is rarely, and a build that redraws them on every push
would be five binary files churning for no reason.

### What the artwork is, and why this is not a crop

The source is a rounded square on a pure black field. Its backdrop is a dark
vertical gradient with a rim light hugging the border, and its corners are
rounder than the squircle iOS masks with — so iOS shows a band of field
*outside* the artwork's rounding that has to be filled with something. A flat
fill cannot work: pick any one colour and the rounded square shows through it
as a ghost, because the backdrop it has to match is a gradient.

What does work is reflecting the backdrop outward across its own border. That
is continuous at the seam by construction, and it carries the rim light around
the corner instead of smearing one sample across it. Two details matter:

  * the artwork's outermost ring is antialiased against the black field and so
    is darker than the rim just inside it. Reflected, it survives as a thin
    dark outline — the very ghost this is avoiding. The fill treats that ring
    as field; the Android cut-out keeps it, because it is the real edge.

  * along the bottom the film reel reaches the border, so the nearest inside
    pixel is reel rather than backdrop. The reflection is capped at the depth
    the rim takes to decay and walks inward past anything too bright to be
    backdrop, which keeps the reel inside the icon.

The rounded square stays faintly visible as a glow where the rim meets its
reflection. Removing that would mean reconstructing the backdrop under the rim
as well, which is inpainting; at the size either platform draws this, the glow
reads as deliberate.
"""
import sys
from collections import deque
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "docs/brand/app-icon-source.webp"
IOS = ROOT / "ios/WatchGuru/Resources/Assets.xcassets/AppIcon.appiconset/icon-1024.png"
ANDROID = ROOT / "android/app/src/main/res"

FIELD = 6        # at or below this luminance a pixel is the black field
CONTENT = 45     # above this a pixel is the artwork, not its backdrop
RIM_DEPTH = 26   # how far in the rim light reaches, in source pixels
EDGE = 3         # the antialiased ring at the artwork's edge
FALLBACK = (12, 5, 32)   # the backdrop at its darkest, for the rare dead end

# The adaptive icon safe zone: a launcher's mask can show no more than the
# middle 72 of the 108dp canvas, so artwork sized to exactly that fills every
# mask and is cropped by none of them.
DENSITIES = (("mdpi", 108), ("hdpi", 162), ("xhdpi", 216),
             ("xxhdpi", 324), ("xxxhdpi", 432))


def luminance(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def field_mask(px, w, h):
    """The black field, flooded from the corners.

    Flooding rather than thresholding so that black *inside* the artwork — the
    film perforations, the guru's eyes — is never mistaken for the field.
    """
    field = bytearray(w * h)
    q = deque()
    for seed in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        q.append(seed)
        field[seed[1] * w + seed[0]] = 1
    while q:
        x, y = q.popleft()
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h and not field[ny * w + nx] \
                    and luminance(px[nx, ny]) <= FIELD:
                field[ny * w + nx] = 1
                q.append((nx, ny))
    return field


def grow(mask, w, h, by):
    for _ in range(by):
        grown = bytearray(mask)
        for y in range(h):
            row = y * w
            for x in range(w):
                if mask[row + x]:
                    continue
                if ((x and mask[row + x - 1]) or (x + 1 < w and mask[row + x + 1])
                        or (y and mask[row - w + x]) or (y + 1 < h and mask[row + w + x])):
                    grown[row + x] = 1
        mask = grown
    return mask


def nearest_inside(mask, w, h):
    """For every masked pixel, the nearest unmasked one, by a sweep outward."""
    src = [None] * (w * h)
    q = deque()
    for y in range(h):
        row = y * w
        for x in range(w):
            if mask[row + x]:
                continue
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < w and 0 <= ny < h and mask[ny * w + nx]:
                    src[row + x] = (x, y)
                    q.append((x, y))
                    break
    while q:
        x, y = q.popleft()
        s = src[y * w + x]
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h and mask[ny * w + nx] and src[ny * w + nx] is None:
                src[ny * w + nx] = s
                q.append((nx, ny))
    return src


def backdrop_at(px, mask, w, h, s, dx, dy):
    """The backdrop a distance in from boundary point `s`, along (dx, dy).

    Walks rather than sampling one point: capped at the rim depth, and taking
    the first pixel dark enough to be backdrop rather than artwork.
    """
    n = max(abs(dx), abs(dy))
    if n == 0:
        return FALLBACK
    for i in range(min(n, RIM_DEPTH), -1, -1):
        rx, ry = s[0] + round(dx * i / n), s[1] + round(dy * i / n)
        if 0 <= rx < w and 0 <= ry < h and not mask[ry * w + rx]:
            c = px[rx, ry]
            if luminance(c) <= CONTENT:
                return c
    return FALLBACK


def render():
    im = Image.open(SOURCE).convert("RGB")
    w, h = im.size
    px = im.load()

    field = field_mask(px, w, h)
    fill = grow(field, w, h, EDGE)
    src = nearest_inside(fill, w, h)

    filled = Image.new("RGB", (w, h))
    fp = filled.load()
    for y in range(h):
        row = y * w
        for x in range(w):
            if not fill[row + x]:
                fp[x, y] = px[x, y]
                continue
            s = src[row + x]
            fp[x, y] = FALLBACK if s is None else \
                backdrop_at(px, fill, w, h, s, s[0] - x, s[1] - y)

    # A square box centred on the artwork, so neither platform inherits the
    # black margin and the scale stays uniform.
    xs = [x for x in range(w) if any(not field[y * w + x] for y in range(h))]
    ys = [y for y in range(h) if any(not field[y * w + x] for x in range(w))]
    side = max(xs[-1] - xs[0] + 1, ys[-1] - ys[0] + 1)
    cx, cy = (xs[0] + xs[-1]) // 2, (ys[0] + ys[-1]) // 2
    box = (cx - side // 2, cy - side // 2, cx - side // 2 + side, cy - side // 2 + side)

    # iOS wants one opaque 1024 square and no alpha channel at all: App Store
    # submission rejects an icon that has one, even a fully opaque one.
    ios = filled.crop(box).resize((1024, 1024), Image.LANCZOS)

    # Android wants the artwork itself, with the field transparent.
    shape = Image.new("RGBA", (w, h))
    sp = shape.load()
    for y in range(h):
        row = y * w
        for x in range(w):
            sp[x, y] = (*px[x, y], 0 if field[row + x] else 255)
    shape = shape.crop(box)

    android = {}
    for name, dp in DENSITIES:
        safe = dp * 72 // 108
        canvas = Image.new("RGBA", (dp, dp), (0, 0, 0, 0))
        canvas.paste(shape.resize((safe, safe), Image.LANCZOS), ((dp - safe) // 2,) * 2)
        android[name] = canvas
    return ios, android


def main(argv):
    check = "--check" in argv
    ios, android = render()
    targets = [(IOS, ios)] + [
        (ANDROID / ("mipmap-%s" % name) / "ic_launcher_foreground.png", img)
        for name, img in android.items()
    ]
    stale = []
    for path, img in targets:
        if check:
            if not path.exists() or Image.open(path).convert("RGBA").tobytes() \
                    != img.convert("RGBA").tobytes():
                stale.append(path.relative_to(ROOT))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            img.save(path)
            print("wrote", path.relative_to(ROOT))
    if stale:
        print("stale, re-run without --check:", *stale, sep="\n  ")
        return 1
    if check:
        print("icons match the source artwork")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
