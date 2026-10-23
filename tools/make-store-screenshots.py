#!/usr/bin/env python3
"""Frame the raw store screenshots with a caption, at the sizes each store wants.

    python3 tools/make-store-screenshots.py

Reads docs/store/screenshots/raw/<set>/*.png and writes
docs/store/screenshots/framed/<set>/*.png. The raw captures are already valid
uploads on their own; the framed ones put a one-line caption from
CAPTIONS above a scaled-down screen on the brand gradient, which is what most
listings do and what reads at the size the stores show a thumbnail.

| Set           | Canvas      | Store slot                          |
|---------------|-------------|-------------------------------------|
| iphone-6.9    | 1320 x 2868 | App Store, iPhone 6.9" display      |
| android-phone | 1080 x 1920 | Play, phone screenshots (9:16)      |

How the raw captures were made, and why the titles in them are invented, is in
docs/store/screenshots/README.md. Needs Pillow. The caption font is looked up
from a short list of macOS and Linux paths; the first one present wins.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "docs/store/screenshots/raw"
OUT = ROOT / "docs/store/screenshots/framed"

CANVAS = {
    "iphone-6.9": (1320, 2868),
    "android-phone": (1080, 1920),
}

# Keyed on the part of the file name after the number, so a set can leave a
# screen out without the captions shifting.
CAPTIONS = {
    "up-next": "Always know what to watch next",
    "title": "One tap marks it watched",
    "episodes": "Every episode, every season",
    "where-to-watch": "See where it's streaming",
    "library": "Your whole library, by status",
    "search": "Find any film or series",
    "statistics": "Streaks and stats",
}

# The feature graphic's backdrop, so the listing reads as one set.
BACKDROP = ((0x24, 0x10, 0x55), (0x0C, 0x05, 0x1C))
ACCENT = (0xC9, 0xB6, 0xFF)

FONTS = [
    ("/System/Library/Fonts/Avenir Next.ttc", 8),        # macOS: Avenir Next Heavy
    ("/System/Library/Fonts/HelveticaNeue.ttc", 1),      # macOS: Helvetica Neue Bold
    ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 0),
]


def font(size):
    for path, index in FONTS:
        try:
            return ImageFont.truetype(path, size, index=index)
        except OSError:
            continue
    sys.exit("No caption font found; add one to FONTS.")


def gradient(size, top, bottom):
    g = Image.new("RGB", (1, size[1]))
    for y in range(size[1]):
        f = y / max(size[1] - 1, 1)
        g.putpixel((0, y), tuple(round(top[i] + (bottom[i] - top[i]) * f) for i in range(3)))
    return g.resize(size)


def rounded(im, radius):
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, im.width - 1, im.height - 1], radius, fill=255)
    out = im.convert("RGBA")
    out.putalpha(mask)
    return out


def wrap(draw, text, f, width):
    lines, cur = [], ""
    for word in text.split():
        trial = f"{cur} {word}".strip()
        if cur and draw.textlength(trial, font=f) > width:
            lines.append(cur)
            cur = word
        else:
            cur = trial
    return lines + [cur]


def frame(raw, size, caption):
    w, h = size
    canvas = gradient(size, *BACKDROP).convert("RGBA")
    draw = ImageDraw.Draw(canvas)

    # Caption: up to two lines in the top band.
    margin = round(w * 0.08)
    f = font(round(w * (0.072 if w < h * 0.8 else 0.056)))
    lines = wrap(draw, caption, f, w - 2 * margin)
    line_h = round(f.size * 1.18)
    top = round(h * 0.045)
    # Room for two lines whether or not the caption needs them, so every
    # screen in a set sits at the same size and height. One line is centred
    # in that band.
    first = top + (line_h / 2 if len(lines) == 1 else 0)
    for i, line in enumerate(lines):
        draw.text((w / 2, first + i * line_h), line, font=f, fill="white", anchor="mt")
    rule_y = top + 2 * line_h + round(f.size * 0.25)
    draw.rounded_rectangle([w / 2 - w * 0.05, rule_y, w / 2 + w * 0.05, rule_y + max(4, w // 270)],
                           radius=4, fill=ACCENT)

    # Screen: as large as fits below the caption, centred, with corners and a shadow.
    screen_top = rule_y + round(h * 0.035)
    avail_h = h - screen_top - round(h * 0.035)
    scale = min(avail_h / raw.height, (w - 2 * margin * 0.6) / raw.width)
    sw, sh = round(raw.width * scale), round(raw.height * scale)
    screen = rounded(raw.convert("RGB").resize((sw, sh), Image.LANCZOS), round(sw * 0.06))
    x = (w - sw) // 2

    shadow = Image.new("RGBA", size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([x, screen_top + 16, x + sw, screen_top + sh + 16],
                                             round(sw * 0.06), fill=(0, 0, 0, 150))
    canvas = Image.alpha_composite(canvas, shadow.filter(ImageFilter.GaussianBlur(28)))
    border = Image.new("RGBA", (sw + 8, sh + 8), (0, 0, 0, 0))
    ImageDraw.Draw(border).rounded_rectangle([0, 0, sw + 7, sh + 7], round(sw * 0.06) + 4,
                                             fill=(255, 255, 255, 40))
    canvas.alpha_composite(border, (x - 4, screen_top - 4))
    canvas.alpha_composite(screen, (x, screen_top))
    # Both stores reject an alpha channel on screenshots.
    return canvas.convert("RGB")


def main():
    made = 0
    for name, size in CANVAS.items():
        out_dir = OUT / name
        out_dir.mkdir(parents=True, exist_ok=True)
        for raw_path in sorted((RAW / name).glob("*.png")):
            key = raw_path.stem.split("-", 1)[1]
            if key not in CAPTIONS:
                sys.exit(f"No caption for {raw_path.relative_to(ROOT)}; add '{key}' to CAPTIONS.")
            frame(Image.open(raw_path), size, CAPTIONS[key]).save(out_dir / raw_path.name, optimize=True)
            made += 1
    print(f"Framed {made} screenshots into {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
