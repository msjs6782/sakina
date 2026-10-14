"""Packs Natural Earth GeoJSON (public domain, v5.1.2) into compact binary files for the offline qibla map.

  python3 tools/qibla/ne_pack.py <dir with the .geojson files>

Format (little-endian): b"SKG1", int32 ring count, then per ring: int32 point count and that many
(int16 lon, int16 lat) pairs, lon scaled by 32767/180 and lat by 32767/90 (about 0.6 km and 0.3 km steps).
Polygon rings (outer and holes) and line parts are all stored as rings; the app fills land even-odd.
"""
import hashlib
import json
import os
import struct
import sys

SRC = sys.argv[1]
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "main", "assets", "qibla")
FILES = {
    "ne_110m_land": ("land110.bin", "9e0729ee253ca7d7a5c4ae9395fb1902264c5377c52e224d13dd85010e2835d9"),
    "ne_50m_land": ("land50.bin", "e874b27a51d146452be360cafb3cc50c86001074a67d534113e6534682f9826b"),
    "ne_50m_admin_0_boundary_lines_land": ("borders50.bin", "2faac4f6b34386f3d21b6e018cf151f241f00e5c936d44dd17d7d9bfb147fa48"),
    "ne_110m_lakes": ("lakes110.bin", "eb02ecc86c82004fccbf979058bfabbbd6c2d07968c7844d38eb1c9152d2ffc9"),
}


def rings(geom):
    t, c = geom["type"], geom["coordinates"]
    if t == "Polygon":
        return c
    if t == "MultiPolygon":
        return [r for p in c for r in p]
    if t == "LineString":
        return [c]
    if t == "MultiLineString":
        return c
    raise ValueError(t)


def q(v, span):
    return max(-32767, min(32767, round(v * 32767 / span)))


for name, (out, sha) in FILES.items():
    raw = open(os.path.join(SRC, name + ".geojson"), "rb").read()
    got = hashlib.sha256(raw).hexdigest()
    if got != sha:
        raise SystemExit(f"{name}: sha256 {got} differs from the research run")
    g = json.loads(raw)
    rs = []
    for f in g["features"]:
        for r in rings(f["geometry"]):
            pts = []
            for lon, lat, *_ in r:
                p = (q(lon, 180), q(lat, 90))
                if not pts or pts[-1] != p:
                    pts.append(p)
            if len(pts) >= 2:
                rs.append(pts)
    buf = bytearray(b"SKG1") + struct.pack("<i", len(rs))
    for pts in rs:
        buf += struct.pack("<i", len(pts))
        buf += b"".join(struct.pack("<hh", x, y) for x, y in pts)
    with open(os.path.join(OUT, out), "wb") as fh:
        fh.write(buf)
    print(out, len(rs), "rings", sum(len(p) for p in rs), "points", len(buf), "bytes")
