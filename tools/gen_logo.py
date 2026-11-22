# Generates the pixel-art logo of Create: Flamethrowers (transparent PNG).
# Run: python tools/gen_logo.py  ->  docs/logo.png (README) and src/main/resources/logo.png (mod list)
import math
import os
import random
import struct
import zlib

W, H = 200, 94
SCALE = 4
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")

img = [[(0, 0, 0, 0) for _ in range(W)] for _ in range(H)]
rnd = random.Random(1337)


def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        if len(c) == 3:
            c = c + (255,)
        img[y][x] = c


def get(x, y):
    return img[y][x] if 0 <= x < W and 0 <= y < H else (0, 0, 0, 0)


def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(x, y, c)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])


# ---------------------------------------------------------------- value noise
_grid = {}


def _g(ix, iy):
    k = (ix, iy)
    if k not in _grid:
        _grid[k] = rnd.random()
    return _grid[k]


def noise(x, y):
    ix, iy = math.floor(x), math.floor(y)
    fx, fy = x - ix, y - iy
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = _g(ix, iy) + (_g(ix + 1, iy) - _g(ix, iy)) * fx
    b = _g(ix, iy + 1) + (_g(ix + 1, iy + 1) - _g(ix, iy + 1)) * fx
    return a + (b - a) * fy


def fbm(x, y):
    return 0.6 * noise(x, y) + 0.3 * noise(x * 2.1 + 7, y * 2.1 + 3) + 0.1 * noise(x * 4.3 + 1, y * 4.3 + 9)


FIRE = [
    (0.80, (255, 252, 222)),
    (0.62, (255, 226, 110)),
    (0.44, (255, 160, 45)),
    (0.27, (236, 92, 28)),
    (0.12, (170, 42, 22)),
    (0.00, (100, 24, 18)),
]


def fire_color(h):
    for limit, c in FIRE:
        if h >= limit:
            return c
    return FIRE[-1][1]


# ---------------------------------------------------------------- burning tree (right)
TX0, TX1 = 160, 168           # trunk
CROWN = (164, 15, 25, 13)     # cx, cy, rx, ry
for y in range(H):
    for x in range(W):
        cx, cy, rx, ry = CROWN
        d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
        d += (fbm(x / 4.0, y / 4.0) - 0.5) * 0.7
        if d < 1:
            leaf = (62, 128, 42) if (x * 7 + y * 3) % 5 else (44, 98, 32)
            if rnd.random() < 0.18:
                leaf = (86, 158, 58)
            # the side facing the flame is on fire
            burn = (cx - x) / rx * 0.9 + (y - cy) / ry * 0.5 + (fbm(x / 3.0 + 40, y / 3.0) - 0.5) * 1.2
            if burn > 0.35:
                h = min(1.0, (burn - 0.35) * 1.4 + rnd.random() * 0.25)
                put(x, y, fire_color(h))
            elif burn > 0.15:
                put(x, y, (40, 30, 24))  # charred
            else:
                put(x, y, leaf)
for y in range(18, 64):
    for x in range(TX0, TX1):
        bark = (104, 76, 46) if (x + (y // 3)) % 4 else (78, 56, 34)
        if x == TX0:
            bark = shade(bark, 0.75)
        glow = 22 <= y <= 50 and fbm(x / 2.0, y / 2.5 + 20) > 0.55 and x < TX1 - 2
        put(x, y, (255, 150, 40) if glow else bark)
# roots
for x, y in ((158, 63), (159, 62), (168, 62), (169, 63)):
    put(x, y, (78, 56, 34))

# ---------------------------------------------------------------- flame jet
NOZ_X, NOZ_Y = 46, 37
END_X = 168
for x in range(NOZ_X, END_X + 8):
    t = (x - NOZ_X) / (END_X - NOZ_X)
    axis = NOZ_Y - 7 * t * t
    w = 1.6 + 15 * t ** 0.85
    for y in range(H):
        n = fbm(x / 7.0, y / 4.0 + x / 20.0)
        we = w * (0.7 + 0.6 * n)
        d = abs(y - axis) / we
        if y < axis:
            d *= 0.85  # flames lick upwards
        if d < 1:
            h = (1 - d) ** 0.8 * (1.05 - 0.55 * t) + (n - 0.5) * 0.25
            if t > 0.85:
                h -= (t - 0.85) * 2.5
            if h > 0.05:
                put(x, y, fire_color(h))
# flame splashing around the trunk
for y in range(20, 56):
    for x in range(150, 172):
        n = fbm(x / 3.0 + 9, y / 3.0)
        dx = abs(x - 163) / 8.0
        dy = abs(y - 34) / 18.0
        if dx * dx + dy * dy + (0.5 - n) * 0.8 < 0.55 and get(x, y)[3] == 0:
            put(x, y, fire_color(0.25 + n * 0.5))

# embers and smoke
for _ in range(70):
    x = rnd.randint(70, 190)
    y = rnd.randint(0, 34)
    if get(x, y)[3] == 0:
        put(x, y, rnd.choice([(255, 210, 90), (255, 150, 40), (255, 240, 170)]))
for _ in range(10):
    cx, cy, r = rnd.randint(140, 192), rnd.randint(0, 10), rnd.randint(2, 4)
    for y in range(cy - r, cy + r + 1):
        for x in range(cx - r, cx + r + 1):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r and get(x, y)[3] == 0:
                g = rnd.randint(70, 95)
                put(x, y, (g, g, g, 150))

# ---------------------------------------------------------------- the flamethrower (left)
STONE = (150, 150, 142)
STONE_D = (98, 98, 92)
WOOD = (150, 108, 64)
WOOD_D = (112, 78, 44)
COPPER = (206, 118, 74)
COPPER_L = (236, 158, 108)
COPPER_D = (150, 76, 46)
STEEL = (72, 74, 84)
STEEL_L = (112, 116, 128)
STEEL_D = (40, 40, 48)

# shaft sticking out of the back
for y in range(34, 41):
    for x in range(2, 12):
        c = (176, 174, 166) if y in (35, 36) else (138, 136, 128)
        if y == 40 or y == 34:
            c = (92, 90, 84)
        if (x + 1) % 4 == 0:
            c = shade(c, 0.8)
        put(x, y, c)
# andesite casing
BX0, BY0, BX1, BY1 = 10, 22, 38, 54
for y in range(BY0, BY1):
    for x in range(BX0, BX1):
        edge = x in (BX0, BX1 - 1) or y in (BY0, BY1 - 1)
        frame = x < BX0 + 3 or x >= BX1 - 3 or y < BY0 + 3 or y >= BY1 - 3
        if edge:
            c = STONE_D
        elif frame:
            c = STONE if (x + y) % 7 else shade(STONE, 0.9)
            if y == BY0 + 1:
                c = shade(STONE, 1.12)
        else:
            c = WOOD if (x - BX0) % 5 else WOOD_D
            if rnd.random() < 0.08:
                c = shade(c, 0.9)
        put(x, y, c)
# copper fuel tank with a lava window
for y in range(27, 49):
    for x in range(15, 33):
        if (x in (15, 32)) and (y in (27, 48)):
            continue
        c = COPPER
        if x <= 16 or y <= 28:
            c = COPPER_L
        if x >= 31 or y >= 47:
            c = COPPER_D
        if y in (31, 44):
            c = COPPER_D
        put(x, y, c)
for y in range(34, 42):
    for x in range(20, 28):
        if x in (20, 27) or y in (34, 41):
            put(x, y, (64, 52, 48))
        else:
            k = (y - 35) / 6
            put(x, y, (255, int(220 - 140 * k), int(70 - 50 * k)))
# rivets
for x, y in ((12, 24), (35, 24), (12, 51), (35, 51)):
    put(x, y, (190, 190, 182))
# fuel pipe on top
for y in range(14, 22):
    for x in range(21, 27):
        c = COPPER if x not in (21, 26) else COPPER_D
        if x == 22:
            c = COPPER_L
        put(x, y, c)
rect(19, 12, 29, 15, COPPER_D)
rect(20, 12, 28, 14, COPPER)
# nozzle barrel
for y in range(33, 42):
    for x in range(38, 47):
        c = STEEL
        if y == 33:
            c = STEEL_L
        if y == 41:
            c = STEEL_D
        if x in (41, 42):
            c = shade(c, 0.8)
        put(x, y, c)
for y in range(31, 44):
    for x in range(45, 48):
        c = STEEL_D if y in (31, 43) else STEEL
        put(x, y, c)
for y in range(34, 41):
    put(47, y, (255, 214, 110))
    put(46, y, (255, 160, 60) if y not in (34, 40) else STEEL)

# ---------------------------------------------------------------- title text
FONT = {
    "C": [".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."],
    "R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    "E": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    "F": ["#####", "#....", "#....", "####.", "#....", "#....", "#...."],
    "L": ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
    "M": ["#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"],
    "H": ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "W": ["#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"],
    "S": [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
    ":": [".....", "..#..", "..#..", ".....", "..#..", "..#..", "....."],
}


def text(s, x0, y0, scale, top, bottom, outline, depth):
    cells = set()
    x = x0
    for ch in s:
        g = FONT.get(ch)
        if g is None:
            x += 3 * scale
            continue
        for gy, row in enumerate(g):
            for gx, v in enumerate(row):
                if v == "#":
                    for sy in range(scale):
                        for sx in range(scale):
                            cells.add((x + gx * scale + sx, y0 + gy * scale + sy))
        x += 6 * scale
    hgt = 7 * scale
    # 3D extrusion, then outline, then the gradient face
    for d in range(1, depth + 1):
        for (cx, cy) in cells:
            put(cx + d, cy + d, shade(outline, 1.0) if d == depth else (outline[0] + 30, outline[1] + 12, outline[2] + 6))
    for (cx, cy) in cells:
        for ox, oy in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, 1), (-1, 1), (1, -1)):
            if (cx + ox, cy + oy) not in cells:
                put(cx + ox, cy + oy, outline)
    for (cx, cy) in cells:
        k = (cy - y0) / max(1, hgt - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * k) for i in range(3))
        if (cy - y0) % scale == 0 and (cy - y0) // scale == 0:
            c = shade(c, 1.08)
        put(cx, cy, c)


def text_width(s, scale):
    return sum(6 * scale if ch in FONT else 3 * scale for ch in s) - scale


title = "FLAMETHROWERS"
tw = text_width(title, 2)
text("CREATE", (W - text_width("CREATE", 1)) // 2 - 1, 63, 1, (222, 214, 196), (168, 158, 140), (42, 34, 30), 1)
text(title, (W - tw) // 2 - 1, 73, 2, (255, 222, 96), (226, 74, 24), (52, 20, 12), 2)


# ---------------------------------------------------------------- write
def write_png(path, scale):
    w, h = W * scale, H * scale
    rows = []
    for y in range(h):
        row = bytearray(b"\x00")
        src = img[y // scale]
        for x in range(w):
            row += bytes(src[x // scale])
        rows.append(bytes(row))
    raw = b"".join(rows)

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)

    data = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)
    print(path, w, "x", h)


write_png(os.path.join(ROOT, "docs", "logo.png"), SCALE)
write_png(os.path.join(ROOT, "src", "main", "resources", "logo.png"), 2)
