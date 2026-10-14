"""WMM2025 coefficients and test values (NOAA NCEI), cross-checked with pygeomag; Natural Earth land data.

Writes <out>/wmm/ (WMM.COF, test values, pygeomag results) and <out>/ne/ (GeoJSON files).
"""
import hashlib
import io
import json
import os
import re
import subprocess
import sys
import urllib.request
import zipfile

OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
W = os.path.join(OUT, "wmm")
N = os.path.join(OUT, "ne")
os.makedirs(W, exist_ok=True)
os.makedirs(N, exist_ok=True)
UA = {"User-Agent": "sakina-asset-research/1.0 (personal app; GitHub Actions)"}


def get(url, timeout=60):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=timeout) as r:
        return r.geturl(), r.read()


def log(name, text):
    with open(name, "a", encoding="utf-8") as f:
        f.write(text + "\n")
    print(text)


L = os.path.join(W, "log.txt")
# 1. NOAA pages: find the coefficient zip and the test values.
links = set()
for page in ("https://www.ncei.noaa.gov/products/world-magnetic-model/wmm-coefficients",
             "https://www.ncei.noaa.gov/products/world-magnetic-model",
             "https://www.ncei.noaa.gov/products/world-magnetic-model/wmm-test-values"):
    try:
        final, body = get(page)
        text = body.decode("utf-8", "replace")
        found = set(re.findall(r'href="([^"]+)"', text))
        for h in found:
            if re.search(r"(?i)wmm.?2025", h) or re.search(r"(?i)test.?values", h):
                links.add(urllib.request.urljoin(final, h))
        log(L, f"page {page}: {len(found)} links")
    except Exception as e:  # noqa: BLE001
        log(L, f"page {page} failed: {e!r}")
for h in sorted(links):
    log(L, f"link {h}")
for h in sorted(links):
    try:
        final, data = get(h)
        name = re.sub(r"[^\w.-]", "_", final.rsplit("/", 1)[-1]) or "file"
        log(L, f"got {final} {len(data)} bytes sha256 {hashlib.sha256(data).hexdigest()}")
        if name.lower().endswith(".zip"):
            z = zipfile.ZipFile(io.BytesIO(data))
            for i in z.infolist():
                log(L, f"  zip entry {i.filename} {i.file_size}")
                if not i.is_dir() and i.file_size < 5_000_000:
                    with open(os.path.join(W, re.sub(r"[^\w.-]", "_", i.filename)), "wb") as f:
                        f.write(z.read(i))
        elif len(data) < 5_000_000:
            with open(os.path.join(W, name), "wb") as f:
                f.write(data)
    except Exception as e:  # noqa: BLE001
        log(L, f"download {h} failed: {e!r}")

# 2. pygeomag as an independent implementation (and its bundled coefficients).
subprocess.run([sys.executable, "-m", "pip", "install", "-q", "pygeomag"], check=False)
try:
    import pygeomag  # noqa: E402
    from pygeomag import GeoMag  # noqa: E402
    base = os.path.dirname(pygeomag.__file__)
    for root, _, files in os.walk(base):
        for fn in files:
            if fn.upper().endswith(".COF"):
                p = os.path.join(root, fn)
                d = open(p, "rb").read()
                log(L, f"pygeomag file {os.path.relpath(p, base)} {len(d)} sha256 {hashlib.sha256(d).hexdigest()} head {d[:60]!r}")
                with open(os.path.join(W, "pygeomag_" + fn), "wb") as f:
                    f.write(d)
    pts = [(21.4225, 39.8262, 0, 2026.0), (24.7136, 46.6753, 0.6, 2026.8), (51.5074, -0.1278, 0, 2026.8), (69.6492, 18.9553, 0, 2026.8),
           (-33.8688, 151.2093, 0, 2026.8), (40.7128, -74.0060, 0, 2027.5), (35.6762, 139.6503, 0, 2026.8), (0, 0, 0, 2025.0), (80, 0, 0, 2027.5)]
    res = []
    for lat, lon, alt, t in pts:
        try:
            gm = GeoMag(coefficients_file="wmm/WMM_2025.COF")
        except Exception:  # noqa: BLE001
            gm = GeoMag()
        r = gm.calculate(glat=lat, glon=lon, alt=alt, time=t)
        res.append({"lat": lat, "lon": lon, "altKm": alt, "year": t, "d": r.d, "i": r.i, "h": r.h, "x": r.x, "y": r.y, "z": r.z, "f": r.f})
        log(L, f"pygeomag {lat},{lon},{alt}km {t}: D={r.d:.4f} I={r.i:.4f} F={r.f:.1f}")
    json.dump(res, open(os.path.join(W, "pygeomag_points.json"), "w"), indent=1)
except Exception as e:  # noqa: BLE001
    log(L, f"pygeomag failed: {e!r}")

# 3. Natural Earth land and boundaries (public domain), pinned release.
NL = os.path.join(N, "log.txt")
for name in ("ne_110m_land", "ne_50m_land", "ne_110m_admin_0_boundary_lines_land", "ne_50m_admin_0_boundary_lines_land", "ne_110m_lakes"):
    url = f"https://raw.githubusercontent.com/nvkelso/natural-earth-vector/v5.1.2/geojson/{name}.geojson"
    try:
        final, data = get(url)
        with open(os.path.join(N, name + ".geojson"), "wb") as f:
            f.write(data)
        g = json.loads(data)
        npts = 0
        for ft in g["features"]:
            geom = ft["geometry"]
            coords = geom["coordinates"]
            def count(c):
                return 1 if isinstance(c[0], (int, float)) else sum(count(x) for x in c)
            npts += count(coords)
        log(NL, f"{name}: {len(data)} bytes, {len(g['features'])} features, {npts} points, sha256 {hashlib.sha256(data).hexdigest()}")
    except Exception as e:  # noqa: BLE001
        log(NL, f"{name} failed: {e!r}")
for doc in ("README.md", "LICENSE.md"):
    try:
        final, data = get(f"https://raw.githubusercontent.com/nvkelso/natural-earth-vector/v5.1.2/{doc}")
        with open(os.path.join(N, doc), "wb") as f:
            f.write(data)
    except Exception as e:  # noqa: BLE001
        log(NL, f"{doc} failed: {e!r}")
