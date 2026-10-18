"""Build BloodHelper's own scale-independent UI icon atlas (Python stdlib only).

All icons share a 24-unit view box and two-unit round stroke. Geometry is encoded
as a signed distance field, so the game can antialias the final on-screen contour
instead of enlarging a collection of one-pixel rectangles. No third-party assets.
"""
from __future__ import annotations

import math
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parent.parent
CELL = 64
COLS = 6
RANGE = 16.0  # Total distance range in atlas texels, also used by the shader.
STROKE = 2.0


def segment(a, b):
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    length = dx * dx + dy * dy

    def distance(x, y):
        t = min(1, max(0, ((x - ax) * dx + (y - ay) * dy) / length)) if length else 0
        return math.hypot(x - ax - t * dx, y - ay - t * dy)

    return distance


def union(*shapes):
    return lambda x, y: min(shape(x, y) for shape in shapes)


def line(*points, width=STROKE, closed=False):
    if closed:
        points = (*points, points[0])
    distances = [segment(a, b) for a, b in zip(points, points[1:])]
    return lambda x, y: min(d(x, y) for d in distances) - width / 2


def circle(x0, y0, radius, filled=False, width=STROKE):
    if filled:
        return lambda x, y: math.hypot(x - x0, y - y0) - radius
    return lambda x, y: abs(math.hypot(x - x0, y - y0) - radius) - width / 2


def box(x0, y0, x1, y1, radius=1.3, filled=False, width=STROKE):
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    hx, hy = (x1 - x0) / 2 - radius, (y1 - y0) / 2 - radius

    def distance(x, y):
        qx, qy = abs(x - cx) - hx, abs(y - cy) - hy
        d = math.hypot(max(qx, 0), max(qy, 0)) + min(max(qx, qy), 0) - radius
        return d if filled else abs(d) - width / 2

    return distance


def polygon(*points):
    edges = [(a, b, segment(a, b)) for a, b in zip(points, (*points[1:], points[0]))]

    def distance(x, y):
        inside = False
        result = float('inf')
        for a, b, edge in edges:
            result = min(result, edge(x, y))
            if ((a[1] > y) != (b[1] > y)) and x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]:
                inside = not inside
        return -result if inside else result

    return distance


def arc(cx, cy, radius, start, end):
    steps = max(8, round(abs(end - start) / 5))
    return line(*[(cx + radius * math.cos(math.radians(start + (end - start) * n / steps)),
                   cy + radius * math.sin(math.radians(start + (end - start) * n / steps)))
                  for n in range(steps + 1)])


def curve(a, b, c, d, steps=18):
    return [((1-t)**3*a[0] + 3*(1-t)**2*t*b[0] + 3*(1-t)*t*t*c[0] + t**3*d[0],
             (1-t)**3*a[1] + 3*(1-t)**2*t*b[1] + 3*(1-t)*t*t*c[1] + t**3*d[1])
            for t in [n / steps for n in range(steps + 1)]]


shield = [(5, 4.6), (12, 2.7), (19, 4.6), (19, 10.2)]
shield += curve((19, 10.2), (19, 15.0), (16, 18.6), (12, 21))
shield += curve((12, 21), (8, 18.6), (5, 15), (5, 10.2))

heart = curve((12, 20), (10, 18), (3, 13), (3, 8.7))
heart += curve((3, 8.7), (3, 3), (9, 2.7), (12, 7))
heart += curve((12, 7), (15, 2.7), (21, 3), (21, 8.7))
heart += curve((21, 8.7), (21, 13), (14, 18), (12, 20))

# Ordering is deliberately stable: UiIcons uses the same atlas cell indices.
ICONS = {
    'modules': union(*(box(x, y, x + 6, y + 6, 1.2)
                       for x, y in [(3.5, 3.5), (14.5, 3.5), (3.5, 14.5), (14.5, 14.5)])),
    'palette': union(line((4, 5), (20, 5)), line((4, 12), (20, 12)), line((4, 19), (20, 19)),
                     line((9, 3), (9, 7)), line((16, 10), (16, 14)), line((8, 17), (8, 21))),
    'profiles': union(line((3.5, 8), (3.5, 5), (10, 5), (12, 8), (20.5, 8),
                           (20.5, 19.5), (3.5, 19.5), (3.5, 8)), line((4, 8), (13, 8))),
    'about': union(circle(12, 12, 8.5), circle(12, 7.8, 1, True), line((12, 11.5), (12, 16.5))),
    'music': union(line((9, 17), (9, 6), (20, 4), (20, 15)),
                   circle(6.5, 17.3, 2.7, True), circle(17.5, 15.3, 2.7, True)),
    'clock': union(circle(12, 12, 8.5), line((12, 6.8), (12, 12), (15.7, 14.2))),
    'shield': line(*shield, closed=True),
    'search': union(circle(10.2, 10.2, 6.7), line((15, 15), (20.5, 20.5))),
    'close': union(line((6, 6), (18, 18)), line((18, 6), (6, 18))),
    'chevron': line((9, 5.5), (15.5, 12), (9, 18.5)),
    'back': union(line((10, 5), (3, 12), (10, 19)), line((3.5, 12), (21, 12))),
    'reset': union(arc(12, 12, 8.1, 218, 550), line((3.4, 4.1), (3.4, 9.5), (8.8, 9.5))),
    'edit': union(line((4, 20), (5.2, 14.9), (16.7, 3.4), (20.6, 7.3), (9.1, 18.8),
                      (4, 20)), line((13.7, 6.4), (17.6, 10.3))),
    'previous': union(box(4, 5, 6.5, 19, .5, True), polygon((18.5, 4.7), (7.5, 12), (18.5, 19.3))),
    'play': polygon((7.5, 4.3), (20, 12), (7.5, 19.7)),
    'pause': union(box(6.5, 4.5, 10.5, 19.5, .8, True), box(13.5, 4.5, 17.5, 19.5, .8, True)),
    'next': union(box(17.5, 5, 20, 19, .5, True), polygon((5.5, 4.7), (16.5, 12), (5.5, 19.3))),
    'check': line((4, 12), (9.4, 17.4), (20.5, 6.3)),
    'headphones': union(arc(12, 12, 8, 180, 360), box(3, 11, 7, 19, 1.5),
                        box(17, 11, 21, 19, 1.5)),
    'grid': union(box(3.5, 3.5, 20.5, 20.5, 1.6), line((3.5, 10), (20.5, 10)),
                  line((10, 10), (10, 20.5))),
    'heart': line(*heart, closed=True),
    'volume': union(line((4, 9), (8, 9), (13, 5), (13, 19), (8, 15), (4, 15), (4, 9)),
                    arc(12, 12, 6, -45, 45), arc(12, 12, 9.5, -45, 45)),
    'external': union(line((11, 4), (4, 4), (4, 20), (20, 20), (20, 13)),
                      line((14, 3), (21, 3), (21, 10)), line((11, 13), (21, 3))),
    'sun': union(circle(12, 12, 4), *(line((12 + 7.5 * math.cos(t), 12 + 7.5 * math.sin(t)),
                                          (12 + 10 * math.cos(t), 12 + 10 * math.sin(t)))
                                     for t in [n * math.pi / 4 for n in range(8)])),
}


def png_chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)


def write_png(path, width, height, pixels):
    rows = b''.join(b'\0' + pixels[y * width * 4:(y + 1) * width * 4] for y in range(height))
    path.write_bytes(b'\x89PNG\r\n\x1a\n' +
                     png_chunk(b'IHDR', struct.pack('>2I5B', width, height, 8, 6, 0, 0, 0)) +
                     png_chunk(b'IDAT', zlib.compress(rows, 9)) + png_chunk(b'IEND', b''))


def main():
    rows = math.ceil(len(ICONS) / COLS)
    width, height = COLS * CELL, rows * CELL
    pixels = bytearray(width * height * 4)
    for index, (name, shape) in enumerate(ICONS.items()):
        for y in range(CELL):
            for x in range(CELL):
                distance = shape((x + .5) * 24 / CELL, (y + .5) * 24 / CELL) * CELL / 24
                value = round(min(1, max(0, .5 - distance / RANGE)) * 255)
                offset = (((index // COLS) * CELL + y) * width + (index % COLS) * CELL + x) * 4
                pixels[offset:offset + 4] = bytes((value, value, value, 255))
        print(f'{index:2d} {name}')
    target = ROOT / 'src/main/resources/assets/bloodhelper/textures/gui/icons_sdf.png'
    target.parent.mkdir(parents=True, exist_ok=True)
    write_png(target, width, height, pixels)
    print(f'Wrote {target.relative_to(ROOT)} ({width}×{height})')


if __name__ == '__main__':
    main()
