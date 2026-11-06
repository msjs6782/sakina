#!/usr/bin/env python3
"""Converts the 3D widget art (generated with Higgsfield, see docs/ART.md) to WebP for the app: the diorama scene at
the six times of day (with a 3:1 band from the bottom of each, for wide widgets) and four textures. Prints the veil
each scene needs so that white text keeps 4.5:1 over the part of the picture behind it (as tools/art/prepare.py).

Usage: python3 tools/art/prepare_3d.py <folder with the source PNGs, named s_<period>.png and c1x.png>
"""
import pathlib
import sys

from PIL import Image

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from prepare import scrim  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/main/res/drawable-nodpi"
PERIODS = ["fajr", "morning", "noon", "asr", "maghrib", "night"]
TEXTURES = {"c10": "ivory", "c11": "emerald", "c12": "marble", "c13": "marble_navy"}


def main(src):
    for period in PERIODS:
        im = Image.open(f"{src}/s_{period}.png").convert("RGB")
        w, h = im.size
        im.resize((1344, 760), Image.LANCZOS).crop((0, 4, 1344, 756)).save(OUT / f"scene_{period}.webp", "WEBP", quality=80, method=6)
        im.crop((0, h - w // 3, w, h)).resize((1344, 448), Image.LANCZOS).save(OUT / f"scene_{period}_wide.webp", "WEBP", quality=80, method=6)
        top = im.resize((1, 1), box=(0, 0, w, 8)).getpixel((0, 0))
        print(f"scene {period}: scrim {scrim(im)}%, top colour #{top[0]:02X}{top[1]:02X}{top[2]:02X}")
    for key, name in TEXTURES.items():
        im = Image.open(f"{src}/{key}.png").convert("RGB")
        w, h = im.size
        cw = int(h * 1.6)
        x = (w - cw) // 2
        im.crop((x, 0, x + cw, h)).resize((1280, 800), Image.LANCZOS).save(OUT / f"tex_{name}.webp", "WEBP", quality=80, method=6)
        mean = im.resize((1, 1)).getpixel((0, 0))
        print(f"texture {name}: mean colour #{mean[0]:02X}{mean[1]:02X}{mean[2]:02X}")


if __name__ == "__main__":
    main(sys.argv[1])
