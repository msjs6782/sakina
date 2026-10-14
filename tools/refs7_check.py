"""Checks phase 7 references in hadith-api: wording at the cited number, else where in the book it occurs."""
import json
import os
import re
import sys
import traceback
import urllib.request

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "lib"))
from textnorm import MARKS, norm, norm_with_map  # noqa: E402
from refs7 import REFS  # noqa: E402

OUT = os.path.join(sys.argv[1] if len(sys.argv) > 1 else "out", "refs7")
os.makedirs(OUT, exist_ok=True)
BASE = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1"
log = open(os.path.join(OUT, "refs7.txt"), "w", encoding="utf-8")


def p(*a):
    s = " ".join(str(x) for x in a)
    print(s)
    log.write(s + "\n")
    log.flush()


cache = {}


def edition(name):
    if name not in cache:
        with urllib.request.urlopen(urllib.request.Request(f"{BASE}/editions/{name}.min.json", headers={"User-Agent": "sakina"}), timeout=120) as r:
            d = json.loads(r.read())
        cache[name] = (d, [norm(h.get("text", "")) for h in d["hadiths"]])
    return cache[name]


def span(text, phrase):
    n, m = norm_with_map(text)
    a = n.find(norm(phrase))
    if a < 0:
        return None
    b = a + len(norm(phrase))
    e = m[b - 1] + 1
    while e < len(text) and MARKS.match(text[e]):
        e += 1
    return text[m[a]:e]


rows = []
for rid, ref, phrase in REFS:
    try:
        book, num = ref.split(":")
        (ara, arn) = edition(f"ara-{book}")
        (eng, _) = edition(f"eng-{book}")
        q = norm(phrase)
        at = [i for i, h in enumerate(ara["hadiths"]) if h.get("arabicnumber") is not None and int(float(h["arabicnumber"])) == int(num)]
        hit = [i for i in at if q in arn[i]]
        where = [str(ara["hadiths"][i]["arabicnumber"]) for i, t in enumerate(arn) if q in t][:8]
        row = {"id": rid, "ref": ref, "ok": bool(hit), "found_at": where}
        if hit:
            h = ara["hadiths"][hit[0]]
            row["number"] = str(h["arabicnumber"])
            row["text"] = h["text"]
            row["span"] = span(h["text"], phrase)
            e = next((x for x in eng["hadiths"] if str(x.get("arabicnumber")) == str(h["arabicnumber"])), None)
            row["grades"] = (e or {}).get("grades") or h.get("grades") or []
            row["english"] = (e or {}).get("text", "")[:600]
        p("OK " if hit else "BAD", rid, ref, "| at", row.get("number"), "| found in", ",".join(where) or "-", "|",
          "; ".join(f"{g['name']}: {g['grade']}" for g in row.get("grades", [])) or "-")
        if not hit:
            for i in at[:2]:
                p("   text:", re.sub(r"\s+", " ", re.sub(MARKS, "", ara["hadiths"][i]["text"]))[:400])
        rows.append(row)
    except Exception:  # noqa: BLE001
        p("ERR", rid, ref, traceback.format_exc().replace("\n", " | "))
json.dump(rows, open(os.path.join(OUT, "refs7.json"), "w"), ensure_ascii=False, indent=1)
p("done", sum(r["ok"] for r in rows), "of", len(REFS))
