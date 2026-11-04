#!/usr/bin/env python3
"""Finds every ayah on the printed pages of the 1441H Madinah Mushaf and writes app/src/main/assets/mushaf/ayahs.tsv.

On each page (rendered with poppler's pdftoppm) it finds the light-green frame and, inside its text area, the
light-green ayah-end markers and surah header boxes (by colour and size). The numbering is chained from page 3
(2:6): every marker ends one ayah, and every header must fall exactly where a surah starts, which checks the count
as it goes; the last page must end at ayah 6236. This edition breaks some pages differently from Tanzil's page
index (25 pages, mostly in juz 30), so the app takes its page starts from here.

Usage: python3 tools/mushaf_ayahs.py <folder with 1.pdf … 604.pdf> [output.tsv]
Needs numpy, scipy, Pillow and pdftoppm. The PDFs are the pages of 1441-AI-hafs.zip (assets/mushaf/pages.tsv).
"""
import os, re, subprocess, sys, tempfile
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[1]
PAGE_W = 382.677
W = 960
UNIT = PAGE_W / W
LINES = 15
# Line j's centre sits about (C0 + C1·j) line heights below the text area's top (fitted on the markers).
C0, C1 = 0.73, 0.98


def runs(v, t):
    r, s = [], None
    for i, x in enumerate(v):
        if x > t and s is None: s = i
        if x <= t and s is not None: r.append((s, i - 1)); s = None
    if s is not None: r.append((s, len(v) - 1))
    return r


def render(pdf):
    with tempfile.TemporaryDirectory() as d:
        out = os.path.join(d, 'p')
        subprocess.run(['pdftoppm', '-scale-to-x', str(W), '-scale-to-y', '-1', '-png', '-singlefile', pdf, out], check=True)
        return np.asarray(Image.open(out + '.png').convert('RGB')).astype(np.int32)


def analyse(pdf):
    a = render(pdf)
    h, w = a.shape[:2]
    mx = a.max(2); mn = a.min(2); lum = a.sum(2) // 3
    fr = ((mx - mn) > 14) & (lum > 140)
    dark = lum < 150
    sides = runs(fr.sum(0) / h, 0.45)
    if len(sides) < 2: return None
    l0, l1 = sides[0]; r0, r1 = sides[-1]
    bands = runs(fr[:, l0:r1 + 1].sum(1) / (r1 - l0 + 1), 0.45)
    if len(bands) < 2: return None
    x0, y0, x1, y1 = l1 + 1, bands[0][1] + 1, r0, bands[-1][0]
    inner = fr[y0:y1, x0:x1]
    H, Wi = inner.shape
    lh = H / LINES
    lab, _ = ndimage.label(inner)
    markers, headers = [], []
    for i, sl in enumerate(ndimage.find_objects(lab)):
        ys, xs = sl
        hh = ys.stop - ys.start; ww = xs.stop - xs.start
        cy = (ys.start + ys.stop) / 2
        if ww > Wi * 0.5 and hh > lh * 0.5:
            headers.append(cy)
        elif 0.35 * lh < hh < 1.0 * lh and 0.35 * lh < ww < 1.2 * lh and (lab[sl] == i + 1).sum() > 0.25 * hh * ww:
            markers.append((cy, xs.start, xs.stop))
    return dict(box=(x0, y0, x1, y1), lh=lh, markers=markers, headers=headers, ink=dark[y0:y1, x0:x1])


def line_of(cy, lh):
    return int(round((cy / lh - C0) / C1))


def quran():
    x = (ROOT / 'app/src/main/assets/quran/quran-data.xml').read_text(encoding='utf-8')
    suras = {int(m.group(1)): int(m.group(2)) for m in re.finditer(r'<sura index="(\d+)" ayas="(\d+)"', x)}
    ref, g = {}, 0
    start = {}
    for s in range(1, 115):
        start[s] = g
        for a in range(1, suras[s] + 1): ref[g] = (s, a); g += 1
    return ref, start


def main():
    folder = sys.argv[1]
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / 'app/src/main/assets/mushaf/ayahs.tsv'
    ref, start = quran()
    rows = ['1\t1:1\t7\t-', '2\t2:1\t5\t-']
    nxt = start[2] + 5  # page 3 starts at 2:6
    basmala_next = False
    for p in range(3, 605):
        r = analyse(os.path.join(folder, f'{p}.pdf'))
        if r is None: sys.exit(f'page {p}: no frame')
        lh = r['lh']
        events = [(line_of(cy, lh), 1, -xa, ('m', cy, xa, xb)) for cy, xa, xb in r['markers']] + \
                 [(line_of(cy, lh), 0, 0, ('h', cy)) for cy in r['headers']]
        events.sort()
        first = nxt
        kinds = ['T'] * LINES
        if basmala_next: kinds[0] = 'B'; basmala_next = False
        marks = []
        cys = {}
        for j, _, _, e in events:
            if not 0 <= j < LINES: sys.exit(f'page {p}: line {j} out of range')
            if e[0] == 'h':
                kinds[j] = 'H'
                s, a = ref[nxt]
                if a != 1: sys.exit(f'page {p}: a surah header where {s}:{a} is next')
                if s != 9:  # every surah but At-Tawbah opens with the basmala on the line below its header
                    if j + 1 < LINES: kinds[j + 1] = 'B'
                    else: basmala_next = True
            else:
                _, cy, xa, xb = e
                if kinds[j] != 'T': sys.exit(f'page {p}: a marker on a {kinds[j]} line')
                marks.append((j, xa, xb)); cys.setdefault(j, []).append(cy)
                nxt += 1
        x0, y0, x1, y1 = r['box']
        # Each line's ink from side to side (centred lines do not reach the frame) and its centre.
        fit = np.polyfit([j for j in cys for _ in cys[j]], [c for j in cys for c in cys[j]], 1) if len(cys) >= 2 else None
        lines = []
        for j in range(LINES):
            cy = np.mean(cys[j]) if j in cys else (np.polyval(fit, j) if fit is not None else (C0 + C1 * j) * lh)
            band = r['ink'][max(0, int(cy - 0.42 * lh)):int(cy + 0.42 * lh)]
            cols = np.where(band.any(0))[0]
            ex = (cols[0], cols[-1] + 1) if len(cols) else (0, 0)
            lines.append(f'{(x0 + ex[0]) * UNIT:.1f},{(x0 + ex[1]) * UNIT:.1f},{(y0 + cy) * UNIT:.1f}')
        s, a = ref[first]
        box = f'{x0 * UNIT:.1f},{y0 * UNIT:.1f},{x1 * UNIT:.1f},{y1 * UNIT:.1f}'
        mk = ' '.join(f'{j}:{(x0 + xa) * UNIT:.1f}:{(x0 + xb) * UNIT:.1f}' for j, xa, xb in marks)
        rows.append(f'{p}\t{s}:{a}\t{nxt - first}\t{"".join(kinds)}\t{box}\t{";".join(lines)}\t{mk}')
        if p % 100 == 0: print('page', p, file=sys.stderr)
    if nxt != 6236: sys.exit(f'the pages end at ayah {nxt}, not 6236')
    head = ['# The ayahs of the printed pages (1441H Madinah Mushaf), found from the PDFs by tools/mushaf_ayahs.py.',
            '# page, first ayah (sura:aya), ayahs ending on the page, the 15 lines (T text, H surah header, B basmala),',
            '# text area x0,y0,x1,y1 (PDF points), each line\'s ink x0,x1 and centre y, and the ayah-end markers (line:x0:x1).',
            '# Pages 1 and 2 have their own ornate frame and only their ayahs.']
    out.write_text('\n'.join(head + rows) + '\n', encoding='utf-8')
    print(f'wrote {out} ({len(rows)} pages)')


if __name__ == '__main__':
    main()
