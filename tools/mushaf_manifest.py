#!/usr/bin/env python3
"""Rebuilds app/src/main/assets/mushaf/pages.tsv (and the bundled pages 1-2) from 1441-AI-hafs.zip.

Usage: tools/mushaf_manifest.py path/to/1441-AI-hafs.zip

The zip is the King Fahd Complex digital Mushaf (Madinah, Hafs, 1441H): 604 single-page PDFs named
NNN___Hafs39__DM.ai. The app fetches each page with an HTTP Range request over [dataOffset,
dataOffset + compressed), inflates it and checks size, CRC-32 and SHA-256 against this manifest.
"""
import hashlib
import re
import struct
import sys
import zipfile
from pathlib import Path

MIRRORS = [
    "https://archive.org/download/1441-ai-hafs_202109/1441-AI-hafs.zip",
    "https://archive.org/download/MushafMadinaHafsGreen1441/1441-AI-hafs.zip",
]


def main(zpath: str) -> None:
    root = Path(__file__).resolve().parent.parent
    assets = root / "app/src/main/assets/mushaf"
    raw = Path(zpath).read_bytes()
    z = zipfile.ZipFile(zpath)
    pages = {}
    for i in z.infolist():
        m = re.match(r"(\d{3})___Hafs39__DM\.ai$", Path(i.filename).name)
        if m:
            pages[int(m.group(1))] = i
    assert sorted(pages) == list(range(1, 605)), "expected pages 1..604"
    lines = [
        "# Sakina printed Mushaf manifest, format 1",
        "# The King Fahd Glorious Quran Printing Complex digital Mushaf, Madinah, Hafs, 1441H: 604 page PDFs",
        "# (files NNN___Hafs39__DM.ai, PDF 1.5 from Adobe Illustrator CS6) in the zip 1441-AI-hafs.zip.",
        "# Each page is read straight from the zip with an HTTP Range request, inflated, and checked",
        "# against its size, CRC-32 and SHA-256 below before the app shows it.",
        f"zip\t{len(raw)}\t{hashlib.md5(raw).hexdigest()}\t{hashlib.sha256(raw).hexdigest()}",
        *[f"mirror\t{u}" for u in MIRRORS],
        "#page\tsize\tsha256\tcrc32\tdataOffset\tcompressed\tmethod\tentry",
    ]
    for n in range(1, 605):
        i = pages[n]
        data = z.read(i)
        sig, _, _, _, _, _, _, _, _, nlen, xlen = struct.unpack("<IHHHHHIIIHH", raw[i.header_offset:i.header_offset + 30])
        assert sig == 0x04034B50
        off = i.header_offset + 30 + nlen + xlen
        lines.append(f"{n}\t{len(data)}\t{hashlib.sha256(data).hexdigest()}\t{i.CRC:08x}\t{off}\t{i.compress_size}\t{i.compress_type}\t{Path(i.filename).name}")
        if n in (1, 2):
            (assets / f"{n:03d}.pdf").write_bytes(data)
    (assets / "pages.tsv").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {assets / 'pages.tsv'}")


if __name__ == "__main__":
    main(sys.argv[1])
