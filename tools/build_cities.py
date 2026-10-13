#!/usr/bin/env python3
"""Builds app/src/main/assets/cities.tsv from data/cities_curated.tsv.

Coordinates come from the GeoNames gazetteer (CC BY 4.0) through the `cities.json` package
(https://github.com/lutangar/cities.json). Each curated city carries an approximate position; the
nearest GeoNames place of the same country within 30 km with the most similar name is used.
Usage: tools/build_cities.py <path to cities.json>
"""
import json, math, sys, unicodedata, re, difflib
from pathlib import Path
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parent.parent
geo = json.load(open(sys.argv[1], encoding='utf-8'))
countries = {}
for line in open(ROOT / 'data/countries.tsv', encoding='utf-8'):
    if line.startswith('#') or not line.strip(): continue
    c, ar, en = line.rstrip('\n').split('\t'); countries[c] = (ar, en)

by_cc = {}
for g in geo:
    by_cc.setdefault(g['country'], []).append(g)

def fold(s):
    s = unicodedata.normalize('NFKD', s)
    s = ''.join(ch for ch in s if not unicodedata.combining(ch)).lower()
    s = re.sub(r"[‘’'`ʿʾ\-\.,()]", ' ', s)
    s = re.sub(r'\b(al|el|ad|ar|as|at|az|an|ash|adh)\b', ' ', s)
    return re.sub(r'\s+', '', s)

def km(a, b, c, d):
    p = math.pi / 180
    h = math.sin((c - a) * p / 2) ** 2 + math.cos(a * p) * math.cos(c * p) * math.sin((d - b) * p / 2) ** 2
    return 12742 * math.asin(math.sqrt(h))

out = ["# id\tname_ar\tname_en\tcountry\tcountry_ar\tcountry_en\tlatitude\tlongitude\tzone",
       "# Coordinates: GeoNames (CC BY 4.0) via cities.json. Arabic names and time zones: curated for Sakina."]
report = []
for line in open(ROOT / 'data/cities_curated.tsv', encoding='utf-8'):
    if line.startswith('#') or not line.strip(): continue
    f = line.rstrip('\n').split('\t')
    cid, ar, en, cc, zone, alat, alon = f[:7]
    gcc = f[7] if len(f) > 7 and f[7] else cc
    ZoneInfo({'Europe/Kiev': 'Europe/Kyiv'}.get(zone, zone))  # fails on an unknown zone (Kiev kept for Android 8 tzdata)
    alat, alon = float(alat), float(alon)
    cands = []
    for g in by_cc.get(gcc, []):
        d = km(alat, alon, float(g['lat']), float(g['lng']))
        if d <= 30:
            sim = difflib.SequenceMatcher(None, fold(en.split('(')[0]), fold(g['name'])).ratio()
            cands.append((sim, -d, g))
    if not cands:
        report.append(f'MISSING {cid}'); continue
    cands.sort(key=lambda x: (x[0], x[1]), reverse=True)
    sim, negd, g = cands[0]
    report.append(f'{cid:20s} {en:28s} -> {g["name"]:30s} {-negd:5.1f} km  sim {sim:.2f}')
    car, cen = countries[cc]
    out.append('\t'.join([cid, ar, en, cc, car, cen, f'{float(g["lat"]):.5f}', f'{float(g["lng"]):.5f}', zone]))

(ROOT / 'app/src/main/assets').mkdir(parents=True, exist_ok=True)
(ROOT / 'app/src/main/assets/cities.tsv').write_text('\n'.join(out) + '\n', encoding='utf-8')
print('\n'.join(report))
print(len(out) - 2, 'cities written')
