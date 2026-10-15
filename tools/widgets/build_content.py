#!/usr/bin/env python3
"""Builds app/src/main/assets/widgets/content.json for the content widget.

Every text is copied verbatim from the files already bundled with the app:
  - verses from the Tanzil Uthmani text (quran/quran-uthmani.xml), the references of the app's
    "verse of the day" list that are short enough for a widget (200 characters or fewer);
  - short general adhkar from the app's adhkar library (adhkar/adhkar.json), with source and grade.
RefsAssetTest-style unit test WidgetContentAssetTest checks the output against both sources.
Run from the repository root: python3 tools/widgets/build_content.py
"""
import html
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets"

# The same list as core/quran/DailyVerse.kt (kept in the same order).
VERSES = ("2:152 2:153 2:186 2:201 2:255 2:286 3:8 3:139 3:173 3:200 6:162 7:23 7:56 9:51 10:62 13:28 14:7 14:40 "
          "15:99 16:97 16:128 17:80 18:10 20:114 21:87 23:118 25:74 28:24 29:69 33:41 39:53 40:60 42:19 47:7 50:16 "
          "51:56 64:11 93:5 94:5 2:45").split()
MAX_VERSE = 200

# Short adhkar not tied to a time or situation (no morning-and-evening, after-prayer, food, travel or sleep adhkar).
ADHKAR = ["kalimatan", "hawqala", "baqiyat", "istighfar_70",
          "rabbighfir_100", "rabbana_atina", "dhun_nun", "hasbunallah", "hamm_hazan"]


def verses():
    xml = (ASSETS / "quran/quran-uthmani.xml").read_text(encoding="utf-8")
    meta = (ASSETS / "quran/quran-data.xml").read_text(encoding="utf-8")
    names = {int(m.group(1)): (m.group(2), m.group(3)) for m in
             re.finditer(r'<sura index="(\d+)" ayas="\d+" start="\d+" name="([^"]+)" tname="([^"]+)"', meta)}
    texts, sura = {}, 0
    for line in xml.splitlines():
        m = re.search(r'<sura\s+index="(\d+)"', line)
        if m:
            sura = int(m.group(1))
        m = re.search(r'<aya\s+index="(\d+)"\s+text="([^"]*)"', line)
        if m:
            texts[(sura, int(m.group(1)))] = html.unescape(m.group(2))
    out = []
    for r in VERSES:
        s, a = map(int, r.split(":"))
        t = texts[(s, a)]
        if len(t) > MAX_VERSE:
            continue
        ar, en = names[s]
        out.append({"type": "ayah", "ref": r, "text": t, "sourceAr": f"سورة {ar} {a}", "sourceEn": f"{html.unescape(en)} {s}:{a}"})
    return out


def adhkar():
    lib = json.loads((ASSETS / "adhkar/adhkar.json").read_text(encoding="utf-8"))
    items = {x["id"]: x for x in lib["items"]}
    out = []
    for i in ADHKAR:
        x = items[i]
        assert len(x["parts"]) == 1 and x["parts"][0].get("text"), i
        p = x["parts"][0]
        out.append({
            "type": "dhikr", "ref": i, "text": p["text"],
            "sourceAr": x["source"]["ar"], "sourceEn": x["source"]["en"],
            "gradeAr": x["source"]["grade"]["ar"], "gradeEn": x["source"]["grade"]["en"],
            "count": max(1, p.get("count", 1)),
        })
    return out


def main():
    pack = {"version": 1, "items": verses() + adhkar()}
    dst = ASSETS / "widgets/content.json"
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(json.dumps(pack, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{dst}: {sum(1 for x in pack['items'] if x['type'] == 'ayah')} verses, "
          f"{sum(1 for x in pack['items'] if x['type'] == 'dhikr')} adhkar")


if __name__ == "__main__":
    main()
