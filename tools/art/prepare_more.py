#!/usr/bin/env python3
"""Converts the second set of Higgsfield paintings (see docs/ART.md) for the app: screen headers, the onboarding
backdrop, the launcher icon's background, and three widget background textures. Prints the veil each header
needs so white text keeps 4.5:1 over its upper half.

Usage: python3 tools/art/prepare_more.py <folder with the source PNGs>
"""
import glob
import pathlib
import sys

from PIL import Image

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from prepare import scrim  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/main/res/drawable-nodpi"
PICK = {  # resource -> (Higgsfield job id prefix, width, quality)
    "art_quran": ("dad3adfa", 1200, 82),
    "art_adhkar": ("14f161dc", 1200, 82),
    "art_calendar": ("f15ac0f4", 1200, 82),
    "art_welcome": ("3c0f19d9", 720, 82),
    "ic_launcher_art": ("efd5758d", 432, 90),
    "tex_geometric": ("833446bf", 512, 85),
    "tex_paper": ("770cdb8e", 512, 85),
    "tex_night": ("6b9d2b74", 512, 85),
}

src = pathlib.Path(sys.argv[1])
for name, (job, width, q) in PICK.items():
    f = next(iter(glob.glob(str(src / f"*{job}*.png"))))
    im = Image.open(f).convert("RGB")
    im = im.resize((width, round(im.height * width / im.width)), Image.LANCZOS)
    im.save(OUT / f"{name}.webp", "WEBP", quality=q, method=6)
    top = im.crop((0, 0, im.width, im.height // 2))
    print(f"{name}: {im.size} {(OUT / f'{name}.webp').stat().st_size // 1024} KB, veil {scrim(im)}%, "
          f"mean {tuple(int(sum(c) / len(c)) for c in zip(*im.resize((16, 16)).getdata()))}")
