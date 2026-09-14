#!/usr/bin/env python3
"""Draws the iOS app icon.

The repository keeps its artwork as source rather than as exported images: the
Android launcher icon is a vector, reviewable in a diff. An iOS app icon cannot
be -- the asset catalogue takes a PNG and nothing else -- so the source of truth
is this script, and the PNG it writes is a build product that happens to be
committed because Xcode needs it there.

The mark is the same one the Android icon draws, in the same colours and the
same proportions: a screen with a play triangle knocked out of it, on the flat
blue that survives being shrunk, masked and parallaxed.

    python3 tools/make-ios-icon.py

Two rules an iOS icon must follow, both of which the App Store checks: it is
fully opaque (no alpha channel at all) and it has square corners. The system
applies its own superellipse mask, and an icon that rounds its own corners is
visibly cropped twice.
"""

from __future__ import annotations

import struct
import zlib
from pathlib import Path

SIZE = 1024
OUT = Path(__file__).resolve().parent.parent / (
    "ios/WatchGuru/Resources/Assets.xcassets/AppIcon.appiconset/icon-1024.png"
)

BACKGROUND = (0x1B, 0x5E, 0x9B)
FOREGROUND = (0xFF, 0xFF, 0xFF)

# The Android icon's 108-unit viewport, mapped onto a smaller visible window so
# the mark fills more of the tile. The launcher gives its artwork a wide safe
# zone because the mask is unpredictable; iOS crops a known superellipse, so the
# same drawing can sit larger without risking a clipped corner.
VIEWPORT = 88.0
SCALE = SIZE / VIEWPORT
OFFSET = (108.0 - VIEWPORT) / 2.0  # centre the 108-unit drawing in the window


# The drawing is not vertically centred in its own viewport: screen and stand
# together span 36 to 78, whose middle is 57 rather than 54. Left alone the mark
# sits a visible 35px low in the tile, which reads as a mistake rather than as a
# choice.
VERTICAL_NUDGE = 3.0


def to_px(value: float) -> float:
    """One horizontal coordinate of the Android vector, in icon pixels."""
    return (value - OFFSET) * SCALE


def to_py(value: float) -> float:
    """One vertical coordinate, optically centred."""
    return (value - OFFSET - VERTICAL_NUDGE) * SCALE


class Shape:
    """A filled shape that can report how much of a pixel it covers."""

    def coverage(self, x: float, y: float) -> float:
        raise NotImplementedError


def _coverage_from_distance(distance: float) -> float:
    """Anti-aliasing: a pixel is as covered as the edge is far inside it.

    Sampling the shape at pixel centres alone gives a staircase; a signed
    distance turns the same maths into a one-pixel ramp, which is what the eye
    reads as a smooth edge.
    """
    return min(1.0, max(0.0, 0.5 - distance))


class RoundedRect(Shape):

    def __init__(self, left: float, top: float, right: float, bottom: float, radius: float):
        self.cx = (left + right) / 2
        self.cy = (top + bottom) / 2
        self.half_w = (right - left) / 2
        self.half_h = (bottom - top) / 2
        self.radius = radius

    def coverage(self, x: float, y: float) -> float:
        dx = abs(x - self.cx) - (self.half_w - self.radius)
        dy = abs(y - self.cy) - (self.half_h - self.radius)
        outside = (max(dx, 0.0) ** 2 + max(dy, 0.0) ** 2) ** 0.5
        inside = min(max(dx, dy), 0.0)
        return _coverage_from_distance(outside + inside - self.radius)


class Triangle(Shape):
    """Convex polygon as the largest of its edge distances."""

    def __init__(self, points: list[tuple[float, float]]):
        self.points = points

    def coverage(self, x: float, y: float) -> float:
        worst = -1e9
        count = len(self.points)
        for index in range(count):
            ax, ay = self.points[index]
            bx, by = self.points[(index + 1) % count]
            ex, ey = bx - ax, by - ay
            length = (ex * ex + ey * ey) ** 0.5
            # Outward normal of a clockwise edge, so distance is positive
            # outside the shape and negative within it.
            distance = ((x - ax) * ey - (y - ay) * ex) / length
            worst = max(worst, distance)
        return _coverage_from_distance(worst)


def blend(under: tuple[int, int, int], over: tuple[int, int, int], alpha: float) -> tuple[int, int, int]:
    return tuple(round(u + (o - u) * alpha) for u, o in zip(under, over))


def render() -> bytearray:
    screen = RoundedRect(to_px(26), to_py(36), to_px(82), to_py(70), 4 * SCALE)
    stand = RoundedRect(to_px(46), to_py(74), to_px(62), to_py(78), 1.5 * SCALE)
    # Clockwise, matching the edge-normal convention in Triangle.
    play = Triangle([(to_px(49), to_py(45)), (to_px(65), to_py(53)), (to_px(49), to_py(61))])

    rows = bytearray()
    for py in range(SIZE):
        y = py + 0.5
        rows.append(0)  # PNG filter: none. The shapes are flat, so filtering
        # would cost time and save little.
        row = bytearray()
        for px in range(SIZE):
            x = px + 0.5
            pixel = BACKGROUND
            white = max(screen.coverage(x, y), stand.coverage(x, y))
            if white > 0:
                pixel = blend(pixel, FOREGROUND, white)
            knockout = play.coverage(x, y)
            if knockout > 0:
                pixel = blend(pixel, BACKGROUND, knockout)
            row += bytes(pixel)
        rows += row
    return rows


def write_png(path: Path, pixels: bytearray) -> None:
    def chunk(kind: bytes, payload: bytes) -> bytes:
        return (
            struct.pack(">I", len(payload))
            + kind
            + payload
            + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
        )

    # Colour type 2 is RGB with no alpha channel, which is what the App Store
    # requires of an icon; 8 bits a channel, no interlacing.
    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 2, 0, 0, 0)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(bytes(pixels), 9))
        + chunk(b"IEND", b"")
    )


if __name__ == "__main__":
    write_png(OUT, render())
    print(f"Wrote {OUT} ({OUT.stat().st_size} bytes)")
