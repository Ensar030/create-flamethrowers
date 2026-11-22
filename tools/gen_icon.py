# Generates the small square icon of Create: Flamethrowers (for Modrinth / CurseForge and the mod's pack.png).
# Run: python tools/gen_icon.py  ->  docs/icon.png (512x512) and src/main/resources/pack.png (128x128)
import math
import os
import random
import struct
import zlib

N = 64
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
rnd = random.Random(42)
img = [[(0, 0, 0, 0) for _ in range(N)] for _ in range(N)]


def put(x, y, c):
    if 0 <= x < N and 0 <= y < N:
        img[y][x] = c if len(c) == 4 else c + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])


_grid = {}


def _g(ix, iy):
    if (ix, iy) not in _grid:
        _grid[(ix, iy)] = rnd.random()
    return _grid[(ix, iy)]


def noise(x, y):
    ix, iy = math.floor(x), math.floor(y)
    fx, fy = x - ix, y - iy
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = _g(ix, iy) + (_g(ix + 1, iy) - _g(ix, iy)) * fx
    b = _g(ix, iy + 1) + (_g(ix + 1, iy + 1) - _g(ix, iy + 1)) * fx
    return a + (b - a) * fy


def fbm(x, y):
    return 0.6 * noise(x, y) + 0.3 * noise(x * 2.1 + 7, y * 2.1 + 3) + 0.1 * noise(x * 4.3 + 1, y * 4.3 + 9)


FIRE = [(0.80, (255, 252, 222)), (0.62, (255, 226, 110)), (0.44, (255, 160, 45)),
        (0.27, (236, 92, 28)), (0.12, (170, 42, 22)), (0.0, (100, 24, 18))]


def fire_color(h):
    for limit, c in FIRE:
        if h >= limit:
            return c
    return FIRE[-1][1]


# ---------------------------------------------------------------- rounded dark badge with a warm glow
R = 9
for y in range(N):
    for x in range(N):
        cx = min(max(x, R), N - 1 - R)
        cy = min(max(y, R), N - 1 - R)
        if (x - cx) ** 2 + (y - cy) ** 2 > R * R:
            continue
        g = max(0.0, 1 - math.hypot(x - 46, y - 22) / 44)
        base = (26 + 70 * g, 20 + 22 * g, 22 + 6 * g)
        edge = x in (0, N - 1) or y in (0, N - 1) or (x - cx) ** 2 + (y - cy) ** 2 > (R - 1) ** 2
        put(x, y, shade(base, 0.6) if edge else tuple(int(v) for v in base))

# charred forest silhouette at the bottom right
for x in range(30, 62):
    top = 50 + int(4 * fbm(x / 5.0, 3))
    for y in range(top, 61):
        if img[y][x][3]:
            put(x, y, (38, 26, 24))
for tx, h in ((50, 22), (57, 30)):
    for y in range(h, 58):
        for x in (tx, tx + 1):
            put(x, y, (52, 34, 28) if x == tx else (40, 28, 24))

# ---------------------------------------------------------------- flame: leaves the nozzle and rises to the top right
NX, NY = 34, 38
for x in range(NX, N - 2):
    t = (x - NX) / (N - 3 - NX)
    axis = NY - 8 * t - 14 * t * t
    w = 2.0 + 17 * t ** 0.7
    for y in range(2, N - 2):
        if not img[y][x][3]:
            continue
        n = fbm(x / 5.0, y / 3.5 + x / 15.0)
        d = abs(y - axis) / (w * (0.7 + 0.6 * n))
        if y < axis:
            d *= 0.85
        if d < 1:
            h = (1 - d) ** 0.8 * (1.05 - 0.5 * t) + (n - 0.5) * 0.25
            if t > 0.88:
                h -= (t - 0.88) * 3
            if h > 0.06:
                put(x, y, fire_color(h))
for _ in range(26):
    x, y = rnd.randint(30, 60), rnd.randint(3, 26)
    if img[y][x][3] and img[y][x][0] < 120:
        put(x, y, rnd.choice([(255, 210, 90), (255, 150, 40), (255, 240, 170)]))

# ---------------------------------------------------------------- flamethrower
STONE, STONE_D = (150, 150, 142), (90, 90, 86)
WOOD, WOOD_D = (150, 108, 64), (112, 78, 44)
COPPER, COPPER_L, COPPER_D = (206, 118, 74), (238, 162, 112), (148, 74, 44)
STEEL, STEEL_L, STEEL_D = (72, 74, 84), (116, 120, 132), (36, 36, 44)
# shaft
for y in range(37, 42):
    for x in range(3, 9):
        c = (176, 174, 166) if y == 38 else (136, 134, 126)
        if y in (37, 41):
            c = (88, 86, 80)
        put(x, y, c)
# casing
BX0, BY0, BX1, BY1 = 8, 26, 30, 53
for y in range(BY0, BY1):
    for x in range(BX0, BX1):
        if x in (BX0, BX1 - 1) or y in (BY0, BY1 - 1):
            c = STONE_D
        elif x < BX0 + 3 or x >= BX1 - 3 or y < BY0 + 3 or y >= BY1 - 3:
            c = shade(STONE, 1.12) if y == BY0 + 1 else STONE
        else:
            c = WOOD if (x - BX0) % 4 else WOOD_D
        put(x, y, c)
# copper tank + lava window
for y in range(30, 49):
    for x in range(12, 26):
        if x in (12, 25) and y in (30, 48):
            continue
        c = COPPER
        if x <= 13 or y <= 31:
            c = COPPER_L
        if x >= 24 or y >= 47:
            c = COPPER_D
        put(x, y, c)
for y in range(35, 43):
    for x in range(15, 23):
        if x in (15, 22) or y in (35, 42):
            put(x, y, (60, 48, 44))
        else:
            k = (y - 36) / 5
            put(x, y, (255, int(222 - 140 * k), int(70 - 50 * k)))
# fuel pipe
for y in range(19, 26):
    for x in range(17, 21):
        put(x, y, COPPER_L if x == 18 else (COPPER_D if x in (17, 20) else COPPER))
for x in range(15, 23):
    put(x, 18, COPPER_D)
    put(x, 19, COPPER if 15 < x < 22 else COPPER_D)
# nozzle
for y in range(35, 43):
    for x in range(30, 35):
        c = STEEL_L if y == 35 else (STEEL_D if y == 42 else STEEL)
        put(x, y, c)
for y in range(34, 44):
    put(34, y, STEEL_D if y in (34, 43) else STEEL)
for y in range(36, 42):
    put(35, y, (255, 214, 110))


# ---------------------------------------------------------------- write
def write_png(path, scale):
    size = N * scale
    rows = []
    for y in range(size):
        row = bytearray(b"\x00")
        src = img[y // scale]
        for x in range(size):
            row += bytes(src[x // scale])
        rows.append(bytes(row))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)

    data = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(b"".join(rows), 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)
    print(path, size, "x", size)


write_png(os.path.join(ROOT, "docs", "icon.png"), 8)
write_png(os.path.join(ROOT, "src", "main", "resources", "pack.png"), 2)
