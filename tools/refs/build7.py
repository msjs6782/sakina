"""Builds app/src/main/assets/refs/refs7.json: the hadith references shown on the calendar, Ramadan, night and
Friday pages, quoted from the verified narrations in sources7.json (hadith-api, sunnah.com numbering).

  python3 tools/refs/build7.py
"""
import json
import os
import re
import sys
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "adhkar"))
from build import BOOKS, GRADES_AR, clean, letter_ref  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "refs", "refs7.json")

# A wider quotation than the matched phrase, where the phrase alone would lose its meaning: (from, to) in plain Arabic.
WIDER = {
    "monday_born": ("وسيل عن صوم يوم الاثنين", "او انزل علي فيه"),
    "forbidden_times": ("ثلاث ساعات كان رسول الله", "حتي تغرب"),
    "duha_rumh": ("حتي تصلي الصبح ثم اقصر", "او رمحين"),
    "last_third": ("ينزل ربنا تبارك وتعالي", "فاغفر له"),
    "friday_salawat": ("ان من افضل ايامكم يوم الجمعه", "معروضه علي"),
    "white_days": ("يا ابا ذر اذا صمت من الشهر ثلاثه ايام", "وخمس عشره"),
    "mon_thu_tahari": ("كان النبي صلي الله عليه وسلم يتحري", "والخميس"),
    "tasua": ("لين بقيت الي قابل", "لاصومن التاسع"),
    "eid_forbidden": ("نهي النبي صلي الله عليه وسلم عن صوم يوم الفطر", "والنحر"),
    "eid_forbidden_m": ("نهي عن صيام يومين", "ويوم الفطر"),
    "ten_ihya": ("كان النبي صلي الله عليه وسلم اذا دخل العشر", "وايقظ اهله"),
    "friday_hour": ("فيه ساعه لا يوافقها", "الا اعطاه اياه"),
}

sys.path.insert(0, os.path.join(HERE, "..", "adhkar"))
from textnorm import MARKS, norm, norm_with_map  # noqa: E402


def span(text, a_phrase, b_phrase):
    n, m = norm_with_map(text)
    a = n.find(norm(a_phrase))
    if a < 0:
        return None
    e = n.find(norm(b_phrase), a)
    if e < 0:
        return None
    b = e + len(norm(b_phrase))
    end = m[b - 1] + 1
    while end < len(text) and MARKS.match(text[end]):
        end += 1
    return text[m[a]:end]


rows = json.load(open(os.path.join(HERE, "sources7.json"), encoding="utf-8"))
out = {}
for r in rows:
    if not r.get("ok"):
        raise SystemExit(f"{r['id']}: not verified")
    book = r["ref"].split(":")[0]
    text = unicodedata.normalize("NFC", r["text"])
    quote = r["span"]
    w = WIDER.get(r["id"])
    if w:
        s = span(text, *w)
        if not s:
            raise SystemExit(f"{r['id']}: wider quote not found")
        quote = s
    quote = clean(unicodedata.normalize("NFC", quote)).replace("صلى الله عليه وسلم", "ﷺ")
    grades = r.get("grades") or []
    if book in ("bukhari", "muslim"):
        grade = {"ar": "صحيح", "en": "Sahih", "byAr": BOOKS[book][0], "byEn": BOOKS[book][1]}
    else:
        alb = next((g for g in grades if g["name"] == "Al-Albani"), None)
        if not alb or alb["grade"] not in GRADES_AR:
            raise SystemExit(f"{r['id']}: grade {grades}")
        grade = {"ar": GRADES_AR[alb["grade"]], "en": alb["grade"], "byAr": "الألباني", "byEn": "Al-Albani"}
    ref = letter_ref(book, r["ref"], r["number"])
    num = ref.split(":")[1]
    out[r["id"]] = {
        "text": quote,
        "book": book,
        "number": num,
        "ar": f"{BOOKS[book][0]} {re.sub('[a-z]', '', num)}",
        "en": f"{BOOKS[book][1]} {num}",
        "grade": grade,
        "graders": [{"by": g["name"], "grade": g["grade"]} for g in grades],
        "url": f"https://sunnah.com/{ref}",
    }
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=1, sort_keys=True)
print(len(out), "references ->", OUT)
