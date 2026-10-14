"""Checks each adhkar candidate against fawazahmed0/hadith-api (sunnah.com-style numbering, grades).

sunnah.com's robots.txt disallows fetching hadith pages, so this uses the open hadith-api data through
jsDelivr. For each candidate: the hadith with the cited number, whether the wording is in it, its grades,
and the vowelled text span. Writes <out>/adhkar/hapi.json, hapi.txt, and samples.
"""
import json
import os
import re
import sys
import urllib.request

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "lib"))
from textnorm import MARKS, norm, norm_with_map  # noqa: E402
from candidates import C  # noqa: E402

OUT = os.path.join(sys.argv[1] if len(sys.argv) > 1 else "out", "adhkar")
os.makedirs(OUT, exist_ok=True)
BASE = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1"
UA = {"User-Agent": "sakina-asset-research/1.0"}
log = open(os.path.join(OUT, "hapi.txt"), "w", encoding="utf-8")


def p(*a):
    s = " ".join(str(x) for x in a)
    print(s)
    log.write(s + "\n")
    log.flush()


def get_json(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r:
        return json.loads(r.read())


eds = get_json(BASE + "/editions.json")
json.dump(eds, open(os.path.join(OUT, "editions.json"), "w"), ensure_ascii=False, indent=1)
for b in ("bukhari", "muslim", "abudawud", "tirmidhi", "ibnmajah", "nasai"):
    for e in eds.get(b, {}).get("collection", []):
        if e["language"] in ("Arabic", "English"):
            p("edition", e["name"], e["language"], "|", e.get("source", ""), "|", e.get("comments", ""))
try:
    info = get_json(BASE + "/info.json")
    json.dump(info, open(os.path.join(OUT, "info.json"), "w"), ensure_ascii=False, indent=1)
except Exception as ex:  # noqa: BLE001
    p("info failed", repr(ex))

cache = {}


def edition(name):
    if name not in cache:
        try:
            cache[name] = get_json(f"{BASE}/editions/{name}.min.json")
        except Exception as ex:  # noqa: BLE001
            p("edition failed", name, repr(ex))
            cache[name] = None
        if cache[name]:
            hs = cache[name]["hadiths"]
            with open(os.path.join(OUT, f"sample_{name}.json"), "w", encoding="utf-8") as f:
                json.dump({"metadata": cache[name].get("metadata"), "first": hs[:2], "count": len(hs)}, f, ensure_ascii=False, indent=1)
    return cache[name]


def number_of(h):
    for k in ("arabicnumber", "hadithnumber"):
        v = h.get(k)
        if v is not None:
            return v
    return None


def find_span(text, c):
    """The vowelled span of the dhikr in text, or None."""
    n, m = norm_with_map(text)
    if c["find"]:
        a = n.find(norm(c["find"]))
        if a < 0:
            return None
        b = a + len(norm(c["find"]))
    else:
        a = n.find(norm(c["find_from"]))
        if a < 0:
            return None
        e = n.find(norm(c["find_to"]), a)
        if e < 0:
            return None
        b = e + len(norm(c["find_to"]))
    start = m[a]
    end = m[b - 1] + 1
    # Keep trailing marks of the last letter.
    while end < len(text) and "ً" <= text[end] <= "ْ" or end < len(text) and text[end] in "ٰـ":
        end += 1
    return text[start:end]


import traceback  # noqa: E402

results = []
ok = 0


def check(c):
    global ok
    book, ref = c["ref"].split(":")
    num = int(re.match(r"\d+", ref).group())
    row = {"id": c["id"], "ref": c["ref"]}
    for lang in ("ara", "eng"):
        ed = edition(f"{lang}-{book}")
        if not ed:
            continue
        hits = [h for h in ed["hadiths"] if number_of(h) is not None and int(float(number_of(h))) == num]
        row[lang] = [{"hadithnumber": h.get("hadithnumber"), "arabicnumber": h.get("arabicnumber"), "grades": h.get("grades"),
                      "reference": h.get("reference"), "text": h.get("text", "")} for h in hits]
    spans = []
    ev = None
    for h in row.get("ara", []):
        s = find_span(h["text"], c)
        if s:
            spans.append({"arabicnumber": h["arabicnumber"], "span": s})
            if c["find_evening"]:
                n, m = norm_with_map(h["text"])
                q = norm(c["find_evening"])
                a = n.find(q)
                if a >= 0:
                    ev = h["text"][m[a]:m[a + len(q) - 1] + 1]
    row["spans"] = spans
    row["evening"] = ev
    grades = []
    for lang in ("ara", "eng"):
        for h in row.get(lang, []):
            for g in h.get("grades") or []:
                grades.append(f'{g.get("name")}: {g.get("grade")}')
    row["grades"] = sorted(set(grades))
    good = bool(spans) and (not c["find_evening"] or bool(ev))
    ok += good
    p("OK " if good else "BAD", c["id"], c["ref"], "hits", [h["arabicnumber"] for h in row.get("ara", [])], "spans", [s["arabicnumber"] for s in spans], "| grades:", "; ".join(row["grades"]) or "-")
    if not good:
        for h in row.get("ara", [])[:3]:
            p("   text:", re.sub(r"\s+", " ", h["text"])[:300])
    results.append(row)


for c in C:
    try:
        check(c)
    except Exception:  # noqa: BLE001
        p("ERR", c["id"], c["ref"], traceback.format_exc().replace("\n", " | "))
p("verified", ok, "of", len(C))
json.dump(results, open(os.path.join(OUT, "hapi.json"), "w"), ensure_ascii=False, indent=1)
