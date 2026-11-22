# Generates the block models, blockstates, item models and the own textures of Create: Flamethrowers.
# Run: py -I tools/gen_assets.py  (from the project folder)
import json, os, random, struct, zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "create_flamethrowers")
MOD = "create_flamethrowers"


def write(path, data):
    path = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


# ---------------------------------------------------------------- textures
def png(path, w, h, pixels):
    raw = b"".join(b"\x00" + bytes(c for px in pixels[y * w:(y + 1) * w] for c in px) for y in range(h))
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


def clamp(v):
    return max(0, min(255, int(v)))


def tex_nozzle():
    # dark blued steel, sooty towards the top, rivets in the corners
    rnd = random.Random(7)
    px = []
    for y in range(16):
        for x in range(16):
            base = 62 + rnd.randint(-6, 6)
            soot = (15 - y) * 1.4
            r, g, b = base - soot, base + 2 - soot, base + 10 - soot
            if x in (0, 15) or y in (0, 15):
                r, g, b = r - 18, g - 18, b - 16
            if (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
                r, g, b = 120, 118, 122
            if y in (5, 10):
                r, g, b = r + 14, g + 14, b + 14
            px.append((clamp(r), clamp(g), clamp(b), 255))
    png("textures/block/nozzle.png", 16, 16, px)


def tex_hot():
    # glowing muzzle: white-yellow core, orange, red rim, dark edge
    px = []
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 / 7.5
            if d < 0.35:
                c = (255, 246, 190)
            elif d < 0.6:
                c = (255, 190, 60)
            elif d < 0.85:
                c = (230, 92, 20)
            else:
                c = (90, 30, 12)
            px.append(c + (255,))
    png("textures/block/nozzle_hot.png", 16, 16, px)


def tex_tank():
    # copper fuel tank with a lava gauge window on the side
    rnd = random.Random(3)
    px = []
    for y in range(16):
        for x in range(16):
            n = rnd.randint(-8, 8)
            r, g, b = 196 + n, 110 + n, 70 + n
            if y in (0, 15) or x in (0, 15):
                r, g, b = 138, 72, 44
            if y in (3, 12):
                r, g, b = r - 30, g - 20, b - 12
            if 5 <= x <= 10 and 5 <= y <= 10:
                if x in (5, 10) or y in (5, 10):
                    r, g, b = 70, 60, 58
                else:
                    t = (y - 6) / 3
                    r, g, b = 255, clamp(200 - 120 * t), 40
            px.append((clamp(r), clamp(g), clamp(b), 255))
    png("textures/block/fuel_tank.png", 16, 16, px)


# ---------------------------------------------------------------- models
CASING = "create:block/andesite_casing"
COPPER = "create:block/copper_casing"
BRASS = "create:block/brass_casing"
NOZZLE = f"{MOD}:block/nozzle"
HOT = f"{MOD}:block/nozzle_hot"
TANK = f"{MOD}:block/fuel_tank"


def el(frm, to, tex, hot_top=False, lit=False):
    faces = {}
    for f in ("north", "south", "east", "west", "up", "down"):
        faces[f] = {"texture": "#" + tex}
    if hot_top:
        faces["up"] = {"texture": "#hot"}
    e = {"from": list(frm), "to": list(to), "faces": faces}
    if lit or hot_top:
        e["neoforge_data"] = {"block_light": 15, "sky_light": 15}
    return e


BASE = [
    # bottom plate with a hole for the shaft
    el((0, 0, 0), (16, 2, 6), "casing"),
    el((0, 0, 10), (16, 2, 16), "casing"),
    el((0, 0, 6), (6, 2, 10), "casing"),
    el((10, 0, 6), (16, 2, 10), "casing"),
    el((1, 2, 1), (15, 8, 15), "casing"),
    el((2, 8, 2), (14, 11, 14), "tank"),
    el((4, 11, 4), (12, 12, 12), "nozzle"),
]

TOPS = {
    "jet": [
        el((6, 12, 6), (10, 15, 10), "nozzle"),
        el((5, 15, 5), (11, 16, 11), "nozzle", hot_top=True),
        el((3, 11, 7), (4, 14, 9), "copper"),
        el((12, 11, 7), (13, 14, 9), "copper"),
    ],
    "fan": [
        el((2, 12, 5), (14, 14, 11), "nozzle"),
        el((0, 14, 6), (16, 16, 10), "nozzle", hot_top=True),
    ],
    "tornado": [
        el((5, 12, 5), (11, 13, 11), "copper"),
    ],
    "napalm": [
        el((4, 12, 4), (12, 16, 12), "brass", hot_top=True),
        el((3, 13, 3), (13, 14, 13), "nozzle"),
    ],
}

# spinning head of the tornado (rendered as a partial, rotating with the shaft); modelled pointing up here
TORNADO_HEAD = [
    el((5, 13, 5), (11, 15, 11), "nozzle"),
    el((6, 15, 6), (10, 16, 10), "nozzle", hot_top=True),
    el((1, 13, 7), (5, 14, 9), "copper"),
    el((11, 13, 7), (15, 14, 9), "copper"),
    el((7, 13, 1), (9, 14, 5), "copper"),
    el((7, 13, 11), (9, 14, 15), "copper"),
    el((1, 14, 7), (3, 16, 9), "nozzle", hot_top=True),
    el((13, 14, 7), (15, 16, 9), "nozzle", hot_top=True),
    el((7, 14, 1), (9, 16, 3), "nozzle", hot_top=True),
    el((7, 14, 13), (9, 16, 15), "nozzle", hot_top=True),
]

TEXTURES = {"particle": CASING, "casing": CASING, "copper": COPPER, "brass": BRASS,
            "nozzle": NOZZLE, "hot": HOT, "tank": TANK}


def up_to_south(e):
    """Rotate an up-pointing element so that it points south (+Z), the orientation Create's partialFacing expects."""
    (x1, y1, z1), (x2, y2, z2) = e["from"], e["to"]
    # rotation about X by +90 deg around the block centre: (x, y, z) -> (x, 16 - z, y)
    a = (x1, 16 - z1, y1)
    b = (x2, 16 - z2, y2)
    n = dict(e)
    n["from"] = [min(a[i], b[i]) for i in range(3)]
    n["to"] = [max(a[i], b[i]) for i in range(3)]
    faces = {}
    remap = {"up": "south", "down": "north", "north": "up", "south": "down", "east": "east", "west": "west"}
    for k, v in e["faces"].items():
        faces[remap[k]] = v
    n["faces"] = faces
    return n


def main():
    tex_nozzle()
    tex_hot()
    tex_tank()
    for kind, top in TOPS.items():
        name = f"{kind}_flamethrower"
        write(f"models/block/{name}.json", {"parent": "block/block", "textures": TEXTURES, "elements": BASE + top})
        variants = {
            "facing=up": {"model": f"{MOD}:block/{name}"},
            "facing=down": {"model": f"{MOD}:block/{name}", "x": 180},
            "facing=north": {"model": f"{MOD}:block/{name}", "x": 90},
            "facing=south": {"model": f"{MOD}:block/{name}", "x": 90, "y": 180},
            "facing=east": {"model": f"{MOD}:block/{name}", "x": 90, "y": 90},
            "facing=west": {"model": f"{MOD}:block/{name}", "x": 90, "y": 270},
        }
        write(f"blockstates/{name}.json", {"variants": variants})
        if kind == "tornado":
            write(f"models/item/{name}.json", {"parent": "block/block", "textures": TEXTURES,
                                               "elements": BASE + top + TORNADO_HEAD})
        else:
            write(f"models/item/{name}.json", {"parent": f"{MOD}:block/{name}"})
    write("models/block/tornado_head.json", {"textures": TEXTURES, "elements": [up_to_south(e) for e in TORNADO_HEAD]})

    # particles: own white flame puffs, coloured by the particle class (white-hot -> orange -> red)
    tex_flame_sprites()
    flame = [f"{MOD}:flame_{i}" for i in range(8)]
    generic = [f"minecraft:generic_{i}" for i in range(7, -1, -1)]
    write("particles/flame_stream.json", {"textures": flame})
    write("particles/flame_puff.json", {"textures": flame})
    write("particles/flame_swirl.json", {"textures": flame})
    write("particles/ember.json", {"textures": generic})


def tex_flame_sprites():
    import math
    for k in range(8):
        rnd = random.Random(100 + k)
        noise = [[rnd.random() for _ in range(16)] for _ in range(16)]
        px = []
        r = 7.2 - k * 0.35
        for y in range(16):
            for x in range(16):
                dx, dy = (x - 7.5), (y - 8.5) * 0.85
                d = math.sqrt(dx * dx + dy * dy) / r
                n = noise[y][x] * (0.25 + k * 0.06)
                a = max(0.0, 1.0 - (d + n) ** 2)
                core = max(0.0, 1.0 - d * 1.6)
                alpha = int(255 * min(1.0, a * 1.1 + core * 0.3))
                px.append((255, 255, int(255 - 25 * (1 - core)), alpha))
        png(f"textures/particle/flame_{k}.png", 16, 16, px)


if __name__ == "__main__":
    main()
