#!/usr/bin/env python3
"""Converts the six sky paintings (and a wide band of each) (generated with Higgsfield, see docs/ART.md) to WebP for the app and prints
the scrim each needs so that white text keeps a 4.5:1 contrast over the part of the picture behind the text.

Usage: python3 tools/art/prepare.py <folder with the source PNGs>
"""
import glob
import pathlib
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/main/res/drawable-nodpi"
PICK = {  # period -> Higgsfield job id prefix
    "fajr": "59863dcf", "morning": "4396a3d2", "noon": "f60a2bf5",
    "asr": "d1259ace", "maghrib": "979b9d8d", "night": "d1ac059f",
}


def lum(c):
    c = c / 255.0
    return ((c + 0.055) / 1.055) ** 2.4 if c > 0.03928 else c / 12.92


def rel(px):
    return 0.2126 * lum(px[0]) + 0.7152 * lum(px[1]) + 0.0722 * lum(px[2])


def scrim(im, target=4.5):
    """Black overlay (percent) that brings the 90th-percentile pixel behind the text to [target] against white."""
    w, h = im.size
    ch = int(w / 1.55)  # the prayer card shows about a 1.55:1 window anchored to the bottom
    region = im.crop((0, h - ch, w, h - ch + int(ch * 0.8))).resize((200, 100))
    px = sorted(region.get_flattened_data() if hasattr(region, "get_flattened_data") else region.getdata(), key=rel)
    p = px[int(len(px) * 0.9)]
    for a in range(101):
        f = 1 - a / 100
        if 1.05 / (rel(tuple(v * f for v in p)) + 0.05) >= target:
            return a
    return 100


def main(src):
    OUT.mkdir(parents=True, exist_ok=True)
    for period, key in PICK.items():
        im = Image.open(glob.glob(f"{src}/*{key}*.png")[0]).convert("RGB")
        dst = OUT / f"sky_{period}.webp"
        im.save(dst, "WEBP", quality=90, method=6)
        # A 3:1 band from the bottom, so wide widgets (centre-cropped by the launcher) keep the skyline.
        w, h = im.size
        im.crop((0, h - w // 3, w, h)).save(OUT / f"sky_{period}_wide.webp", "WEBP", quality=90, method=6)
        top = im.resize((1, 1), box=(0, 0, im.size[0], 8)).getpixel((0, 0))
        print(f"{period}: {dst.stat().st_size // 1024} KB, scrim {scrim(im)}%, top colour #{top[0]:02X}{top[1]:02X}{top[2]:02X}")


if __name__ == "__main__":
    main(sys.argv[1])
