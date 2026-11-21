#!/usr/bin/env python3
"""Writes the first sounds of community/sounds.json: a few shapes, each fitted to every common band layout.

Each shape is a curve in decibels made of gentle shelves and bells; the fit (the same one the app and
tools/autoeq/refresh.py use) turns it into gains for each layout, so the gallery has every sound for most earbuds.
Sounds people send through the "Share a sound" form are added to the file by hand, after this script's ones.

    python3 tools/community/seed.py
"""

import importlib.util
import json
import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
spec = importlib.util.spec_from_file_location("refresh", os.path.join(ROOT, "tools/autoeq/refresh.py"))
refresh = importlib.util.module_from_spec(spec)
spec.loader.exec_module(refresh)


def low(f, hz, db):
    return db / (1 + (f / hz) ** 2)


def high(f, hz, db):
    return db / (1 + (hz / f) ** 2)


def bell(f, hz, db, q):
    return db * refresh.bell(f, hz, q)


SHAPES = [
    ("Vocal clarity", "Voices and lead singers step forward; the bass eases back.",
     lambda f: low(f, 150, -2.5) + bell(f, 1200, 1.5, 0.9) + bell(f, 3000, 3.0, 1.0) + high(f, 12000, -1.0)),
    ("Warm bass", "A fuller, rounder low end with softer treble, for long listening.",
     lambda f: low(f, 110, 5.0) + bell(f, 300, 1.0, 1.0) + high(f, 7000, -2.0)),
    ("Podcast", "Clear speech: less boom, more presence, no hiss.",
     lambda f: low(f, 180, -5.0) + bell(f, 2500, 3.0, 1.1) + high(f, 10000, -2.5)),
    ("Bright and airy", "More sparkle and detail up top, for acoustic and classical.",
     lambda f: bell(f, 4000, 1.5, 1.0) + high(f, 8000, 4.0) + low(f, 100, -1.0)),
    ("Late night", "Gentle at low volume: a little bass kept, sharp treble softened.",
     lambda f: low(f, 120, 2.0) + bell(f, 3500, -2.0, 1.4) + high(f, 9000, -3.0)),
]

LAYOUTS = ["soundcore", "samsung", "sony5", "sony10", "nothing8", "melody6"]


def main():
    points = [20.0 * 2 ** (i / 12) for i in range(121) if 20.0 * 2 ** (i / 12) <= 20000]
    sounds = []
    for name, about, shape in SHAPES:
        curve = [(f, shape(f)) for f in points]
        for layout in LAYOUTS:
            bands, step = refresh.LAYOUTS[layout]
            gains = refresh.fit(curve, bands, step=max(step, 0.5))
            sounds.append({"name": name, "about": about, "by": "Blazbuds", "bands": bands, "gains": gains})
    out = os.path.join(ROOT, "community/sounds.json")
    with open(out, "w", encoding="utf-8") as f:
        f.write('{\n  "sounds": [\n')
        f.write(",\n".join("    " + json.dumps(s, ensure_ascii=False) for s in sounds))
        f.write("\n  ]\n}\n")
    print(f"wrote {len(sounds)} sounds to {os.path.relpath(out, ROOT)}")


if __name__ == "__main__":
    main()
