#!/usr/bin/env python3
"""Builds the "Tuned for your earbuds" table from AutoEq's public measurements.

AutoEq (github.com/jaakkopasanen/AutoEq, MIT licence, see licenses/AutoEq-MIT.txt) publishes, for each measured
headphone, a correction curve that brings it to a neutral target. For every model in models.tsv this script:

  1. finds the model in AutoEq's results/INDEX.md and picks one measurement (SOURCES below, best first),
  2. downloads its "<name> GraphicEQ.txt" into measurements/ (kept in the repo, so the table can be rebuilt offline),
  3. fits the curve to the model's own bands (least squares, the same sums as EqFit.kt), and
  4. writes core/src/commonMain/kotlin/com/blazify/buds/core/eq/TunedTable.kt.

The app never goes online for this: the table is built in. Run it now and then to pick up new measurements:

    python3 tools/autoeq/refresh.py            # download what is missing, then write the table
    python3 tools/autoeq/refresh.py --fetch    # download everything again first
    python3 tools/autoeq/refresh.py --offline  # only rebuild the table from measurements/

Then run ./gradlew :core:jvmTest, which checks the Kotlin fit gives the same gains.
"""

import math
import os
import re
import sys
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
MEASUREMENTS = os.path.join(HERE, "measurements")
TABLE = os.path.join(ROOT, "core/src/commonMain/kotlin/com/blazify/buds/core/eq/TunedTable.kt")
RAW = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/"

# Measurement sources, best first: oratory1990's and crinacle's rigs, then Rtings' consistent lab, then the rest in
# the order AutoEq lists them.
SOURCES = ["oratory1990", "crinacle", "Rtings", "Innerfidelity", "HypetheSonics", "Kuulokenurkka", "Super Review"]

# Each layout: the band centres in hertz, and the step the earbuds take. The range is the app's, -6 to +6 dB.
LAYOUTS = {
    "soundcore": ([100, 200, 400, 800, 1600, 3200, 6400, 12800], 0.1),
    "samsung": ([63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000], 1.0),
    "sony5": ([400, 1000, 2500, 6300, 16000], 1.0),
    "sony10": ([31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000], 1.0),
    "nothing8": ([55, 110, 220, 440, 1320, 3300, 6600, 13200], 0.1),
    "nothing3": ([140, 980, 3400], 1.0),
    "nothing3wide": ([140, 980, 3500], 1.0),
    "nothing3cmf": ([140, 980, 6900], 1.0),
    "melody6": ([62, 250, 1000, 4000, 8000, 16000], 1.0),
}
MIN_DB, MAX_DB = -6.0, 6.0
RIDGE = 0.003


# ---- The fit: keep in step with EqFit.kt ----

def qualities(bands):
    out = []
    for k in range(len(bands)):
        gaps = []
        if k > 0:
            gaps.append(math.log2(bands[k] / bands[k - 1]))
        if k + 1 < len(bands):
            gaps.append(math.log2(bands[k + 1] / bands[k]))
        gaps = [g for g in gaps if g > 0]
        if not gaps:
            out.append(1.0)
        else:
            ratio = 2.0 ** (sum(gaps) / len(gaps))
            out.append(math.sqrt(ratio) / (ratio - 1))
    return out


def bell(hz, centre, q):
    x = q * (hz / centre - centre / hz)
    return 1.0 / (1.0 + x * x)


def at(points, hz):
    if not points:
        return 0.0
    if hz <= points[0][0]:
        return points[0][1]
    if hz >= points[-1][0]:
        return points[-1][1]
    i = 1
    while points[i][0] < hz:
        i += 1
    f0, g0 = points[i - 1]
    f1, g1 = points[i]
    if f1 <= f0:
        return g1
    t = (math.log(hz) - math.log(f0)) / (math.log(f1) - math.log(f0))
    return g0 + t * (g1 - g0)


def grid(bands):
    low = max(20.0, bands[0] / 2.0)
    high = min(20000.0, bands[-1] * 2.0)
    out, j = [], 0
    while True:
        f = low * 2.0 ** (j / 12.0)
        if f > high * 1.000001:
            return out
        out.append(f)
        j += 1


def gauss(a, b):
    n = len(b)
    for col in range(n):
        pivot = col
        for r in range(col + 1, n):
            if abs(a[r][col]) > abs(a[pivot][col]):
                pivot = r
        if pivot != col:
            a[col], a[pivot] = a[pivot], a[col]
            b[col], b[pivot] = b[pivot], b[col]
        p = a[col][col]
        if abs(p) < 1e-12:
            continue
        for r in range(col + 1, n):
            factor = a[r][col] / p
            if factor == 0.0:
                continue
            for c in range(col, n):
                a[r][c] -= factor * a[col][c]
            b[r] -= factor * b[col]
    x = [0.0] * n
    for r in range(n - 1, -1, -1):
        s = b[r]
        for c in range(r + 1, n):
            s -= a[r][c] * x[c]
        x[r] = 0.0 if abs(a[r][r]) < 1e-12 else s / a[r][r]
    return x


def solve(shape, target, fixed):
    bands = len(shape[0])
    free = [k for k in range(bands) if k not in fixed]
    n = len(free) + 1
    a = [[0.0] * n for _ in range(n)]
    b = [0.0] * n
    count = float(len(target))
    for j in range(len(target)):
        row = [shape[j][k] for k in free] + [1.0]
        rest = target[j]
        for k, v in fixed.items():
            rest -= shape[j][k] * v
        for r in range(n):
            b[r] += row[r] * rest / count
            for c in range(n):
                a[r][c] += row[r] * row[c] / count
    for i in range(n - 1):
        a[i][i] += RIDGE
    x = gauss(a, b)
    out = [0.0] * bands
    for k, v in fixed.items():
        out[k] = v
    for i, k in enumerate(free):
        out[k] = x[i]
    return out


def round_step(x, step):
    steps = math.copysign(math.floor(abs(x) / step + 0.5), x)
    v = round(steps * step * 10) / 10.0
    return 0.0 if v == 0 else v


def fit(points, bands, lo=MIN_DB, hi=MAX_DB, step=0.1):
    points = sorted(points)
    qs = qualities(bands)
    g = grid(bands)
    target = [at(points, f) for f in g]
    shape = [[bell(f, bands[k], qs[k]) for k in range(len(bands))] for f in g]
    fixed = {}
    gains = [0.0] * len(bands)
    for _ in range(len(bands) + 1):
        gains = solve(shape, target, fixed)
        worst, by = -1, 1e-9
        for k in range(len(bands)):
            if k in fixed:
                continue
            over = max(gains[k] - hi, lo - gains[k])
            if over > by:
                by, worst = over, k
        if worst < 0:
            break
        fixed[worst] = hi if gains[worst] > hi else lo
    return [round_step(min(hi, max(lo, v)), step) for v in gains]


# ---- AutoEq ----

def graphic_eq(text):
    line = next((l for l in text.splitlines() if l.strip().startswith("GraphicEQ:")), None)
    if line is None:
        return []
    points = []
    for pair in line.split(":", 1)[1].split(";"):
        parts = pair.split()
        if len(parts) == 2:
            points.append((float(parts[0]), float(parts[1])))
    return points


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "blazbuds-autoeq-refresh"}), timeout=60) as r:
        return r.read().decode("utf-8")


def index():
    """Every measurement AutoEq lists: name -> [(source, results path)], in the order listed."""
    found = {}
    for m in re.finditer(r"^- \[(.+?)\]\(\./(.+?)\) by (.+)$", get(RAW + "INDEX.md"), re.M):
        name, path, by = m.group(1), m.group(2), m.group(3)
        found.setdefault(name, []).append((by.split(" on ")[0].strip(), urllib.parse.unquote(path)))
    return found


def pick(entries):
    def rank(entry):
        source = entry[0]
        return SOURCES.index(source) if source in SOURCES else len(SOURCES)
    return sorted(enumerate(entries), key=lambda e: (rank(e[1]), e[0]))[0][1]


def slug(name):
    return re.sub(r"[^A-Za-z0-9]+", "-", name).strip("-").lower()


def models():
    rows = []
    with open(os.path.join(HERE, "models.tsv"), encoding="utf-8") as f:
        for line in f:
            if not line.strip() or line.startswith("#"):
                continue
            label, keys, autoeq, layouts = line.rstrip("\n").split("\t")
            rows.append((label, [k.strip() for k in keys.split(",")], autoeq, layouts.split("+")))
    return rows


def fetch(rows, again):
    os.makedirs(MEASUREMENTS, exist_ok=True)
    listed = None
    for label, _, autoeq, _ in rows:
        file = os.path.join(MEASUREMENTS, slug(autoeq) + ".txt")
        if os.path.exists(file) and not again:
            continue
        if listed is None:
            listed = index()
        entries = listed.get(autoeq)
        if not entries:
            print(f"  no measurement of {autoeq}")
            continue
        source, path = pick(entries)
        url = RAW + urllib.parse.quote(f"{path}/{autoeq} GraphicEQ.txt")
        text = get(url)
        if not graphic_eq(text):
            print(f"  {autoeq}: no GraphicEQ line at {url}")
            continue
        with open(file, "w", encoding="utf-8") as out:
            out.write(f"# AutoEq: {path} (by {source})\n")
            out.write(text.strip() + "\n")
        print(f"  {autoeq}: {source}")


def kotlin_list(values, fmt):
    return "listOf(" + ", ".join(fmt(v) for v in values) + ")"


def write_table(rows):
    entries = []
    for label, keys, autoeq, layouts in rows:
        file = os.path.join(MEASUREMENTS, slug(autoeq) + ".txt")
        if not os.path.exists(file):
            continue
        with open(file, encoding="utf-8") as f:
            text = f.read()
        source = text.splitlines()[0].removeprefix("# AutoEq: ").strip()
        points = graphic_eq(text)
        for layout in layouts:
            bands, step = LAYOUTS[layout]
            gains = fit(points, bands, step=step)
            entries.append((label, keys, autoeq, source, slug(autoeq), bands, step, gains))
    lines = [
        "package com.blazify.buds.core.eq",
        "",
        "// Made by tools/autoeq/refresh.py from AutoEq's measurements; run it again rather than editing by hand.",
        "",
        "/** Every built-in correction: a model, the bands it was fitted to, their step, and the gains. */",
        "internal val TUNED_TABLE: List<TunedCurve> = listOf(",
    ]
    for label, keys, autoeq, source, file, bands, step, gains in entries:
        lines.append(f"    // {autoeq}: {source}")
        lines.append(
            "    TunedCurve(\"%s\", %s, \"%s\", %s, %s, %s)," % (
                label,
                kotlin_list(keys, lambda k: '"%s"' % k),
                file,
                kotlin_list(bands, str),
                f"{step:.1f}",
                kotlin_list(gains, lambda g: f"{g:.1f}"),
            )
        )
    lines.append(")")
    with open(TABLE, "w", encoding="utf-8") as out:
        out.write("\n".join(lines) + "\n")
    print(f"wrote {len(entries)} curves for {len({e[0] for e in entries})} models to {os.path.relpath(TABLE, ROOT)}")


if __name__ == "__main__":
    rows = models()
    if "--offline" not in sys.argv:
        fetch(rows, again="--fetch" in sys.argv)
    write_table(rows)
