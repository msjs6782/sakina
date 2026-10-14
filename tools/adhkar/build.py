"""Builds app/src/main/assets/adhkar/adhkar.json from candidates.py and the verified hadith texts.

  python3 tools/adhkar/build.py --refresh <asset-results>/adhkar/hapi.json   # update sources.json from a research run
  python3 tools/adhkar/build.py                                              # rebuild the asset from sources.json

The wording of every dhikr is taken from the hadith text of the cited narration (fawazahmed0/hadith-api,
sunnah.com numbering), located by the plain-Arabic phrases in candidates.py. A candidate whose phrases are not
in its hadith, or whose grade is not sahih/hasan, stops the build.
"""
import json
import os
import re
import sys
import unicodedata
import urllib.parse

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from candidates import C, CATEGORIES, PRACTICES  # noqa: E402
from textnorm import MARKS, norm, norm_with_map  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(HERE))
SOURCES = os.path.join(HERE, "sources.json")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "adhkar", "adhkar.json")

BOOKS = {
    "bukhari": ("صحيح البخاري", "Sahih al-Bukhari"),
    "muslim": ("صحيح مسلم", "Sahih Muslim"),
    "abudawud": ("سنن أبي داود", "Sunan Abi Dawud"),
    "tirmidhi": ("جامع الترمذي", "Jami at-Tirmidhi"),
    "ibnmajah": ("سنن ابن ماجه", "Sunan Ibn Majah"),
}
GRADES_AR = {"Sahih": "صحيح", "Hasan": "حسن", "Hasan Sahih": "حسن صحيح"}
SURAHS = {112: ("سورة الإخلاص", "Al-Ikhlas"), 113: ("سورة الفلق", "Al-Falaq"), 114: ("سورة الناس", "An-Nas"), 2: ("سورة البقرة", "Al-Baqarah")}

# Practice order: as the adhkar are usually said.
ORDER = {
    "morning": ["muawwidhat_3", "asbahna_mulk", "bika_asbahna", "sayyid_istighfar", "alim_ghayb", "afiya", "bismillah_la_yadurr",
                "subhanallah_wabihamdih_100", "tahlil_100"],
    "evening": ["muawwidhat_3", "asbahna_mulk", "bika_asbahna", "sayyid_istighfar", "alim_ghayb", "afiya", "bismillah_la_yadurr",
                "subhanallah_wabihamdih_100"],
    "sleep": ["nafth_muawwidhat", "ayat_kursi_sleep", "last_baqarah", "bismika_rabbi_wadatu", "qini_adhabak", "bismika_amut", "alim_ghayb",
              "atamana_wasaqana", "tasbih_sleep", "aslamtu_nafsi"],
    "waking": ["ahyana", "taarra_minal_layl"],
    "after_prayer": ["astaghfirullah_salam", "la_mania", "lahu_nimah", "tasbih_33", "ainni_ala_dhikrik"],
}


def lit(text, count, labelAr=None, labelEn=None):
    return {"kind": "text", "text": text, "count": count, "labelAr": labelAr, "labelEn": labelEn}


def find(phrase, count, labelAr=None, labelEn=None, phrase_to=None):
    return {"kind": "find", "find": phrase, "to": phrase_to, "count": count, "labelAr": labelAr, "labelEn": labelEn}


def quran(sura, a, b):
    ar, en = SURAHS[sura]
    whole = a == 1 and sura != 2
    return {"kind": "quran", "quran": {"sura": sura, "from": a, "to": b}, "count": 1,
            "labelAr": ar if whole else f"{ar}، الآية {a}" if a == b else f"{ar}، الآيتان {a}–{b}",
            "labelEn": en if whole else f"{en} {sura}:{a}" if a == b else f"{en} {sura}:{a}–{b}"}


MAIN = {"kind": "main"}
MUAWWIDHAT = [quran(112, 1, 4), quran(113, 1, 5), quran(114, 1, 6)]
TAHLIL = "لا اله الا الله وحده لا شريك له له الملك وله الحمد وهو علي كل شيء قدير"

# Items shown as several parts (each with its own count), and how many rounds of the whole set.
PARTS = {
    "tasbih_sleep": ([lit("اللَّهُ أَكْبَرُ", 34), lit("الْحَمْدُ لِلَّهِ", 33), lit("سُبْحَانَ اللَّهِ", 33)], 1),
    "tasbih_33": ([lit("سُبْحَانَ اللَّهِ", 33), lit("الْحَمْدُ لِلَّهِ", 33), lit("اللَّهُ أَكْبَرُ", 33),
                   find(TAHLIL, 1, "تمام المائة", "Completing the hundred")], 1),
    "astaghfirullah_salam": ([find("استغفر الله", 3), MAIN], 1),
    "ruku_sujud": ([find("سبحان ربي العظيم", 0, "في الركوع", "In bowing"), find("سبحان ربي الاعلي", 0, "في السجود", "In prostration")], 1),
    "masjid_enter_exit": ([find("اللهم افتح لي ابواب رحمتك", 1, "عند الدخول", "On entering"),
                           find("اللهم اني اسالك من فضلك", 1, "عند الخروج", "On leaving")], 1),
    "travel": ([lit("اللَّهُ أَكْبَرُ", 3), MAIN, find("ايبون تايبون عابدون لربنا حامدون", 1, "ويزيد عند الرجوع", "Added on return")], 1),
    "ayat_kursi_sleep": ([quran(2, 255, 255)], 1),
    "last_baqarah": ([quran(2, 285, 286)], 1),
    "nafth_muawwidhat": (MUAWWIDHAT, 3),
    "muawwidhat_3": (MUAWWIDHAT, 3),
}

# The morning wording of a dhikr narrated with its evening wording: the hadith gives the opening; the rest follows it.
MORNING_FROM_EVENING = {
    "asbahna_mulk": ([("أَمْسَيْنَا وَأَمْسَى", "أَصْبَحْنَا وَأَصْبَحَ"), ("هَذِهِ اللَّيْلَةِ", "هَذَا الْيَوْمِ"), ("مَا بَعْدَهَا", "مَا بَعْدَهُ")],
                     "في الحديث: «وإذا أصبح قال ذلك أيضًا: أصبحنا وأصبح الملك لله»، ويُقال في الصباح: «هذا اليوم… ما بعده» مكان «هذه الليلة… ما بعدها».",
                     "The hadith: “In the morning he said the same: We have entered the morning…”; in the morning “this day… after it” replaces “this night… after it”."),
}


def letter_ref(book, cand_ref, number):
    """sunnah.com reference: hadith-api's x.0k sub-narrations are sunnah.com's letters."""
    s = str(number)
    if "." in s:
        base, sub = s.split(".")
        return f"{book}:{base}{chr(ord('a') + int(sub) - 1)}"
    return cand_ref if cand_ref.split(":")[1].rstrip("abcdefgh") == s else f"{book}:{s}"


def clean(text):
    t = text.replace("‏", "").replace("‎", "")
    t = re.sub(r'["{}()]', "", t)
    t = re.sub(r"\s*\.\s*", "، ", t)  # a sentence stop inside the words reads as a pause
    t = re.sub(r"\s+", " ", t).strip()
    t = re.sub(r"\s+،", "،", t)
    t = re.sub(r"،\s*،", "،", t)
    return t.strip(" ،.")


def span(text, phrase, phrase_to=None, start=0):
    n, m = norm_with_map(text)
    a = n.find(norm(phrase), start)
    if a < 0:
        return None
    if phrase_to:
        e = n.find(norm(phrase_to), a)
        if e < 0:
            return None
        b = e + len(norm(phrase_to))
    else:
        b = a + len(norm(phrase))
    s, e = m[a], m[b - 1] + 1
    while e < len(text) and MARKS.match(text[e]):
        e += 1
    return s, e


def extract(text, phrase, phrase_to=None, remove=()):
    r = span(text, phrase, phrase_to)
    if not r:
        return None
    piece = text[r[0]:r[1]]
    for rm in remove:
        x = span(piece, rm)
        if not x:
            raise SystemExit(f"remove phrase not found: {rm}")
        piece = piece[:x[0]] + " " + piece[x[1]:]
    return clean(piece)


def refresh(hapi_path):
    rows = {r["id"]: r for r in json.load(open(hapi_path, encoding="utf-8"))}
    out = {}
    for c in C:
        if c["drop"]:
            continue
        r = rows[c["id"]]
        # The research run's own span, else its only narration with that number (build() re-checks the wording).
        want = c["prefer"] or (r["spans"][0]["arabicnumber"] if r["spans"] else r["ara"][0]["arabicnumber"] if len(r["ara"]) == 1 else None)
        if want is None:
            raise SystemExit(f"{c['id']}: no verified span")
        ara = next(h for h in r["ara"] if str(h["arabicnumber"]) == str(want))
        eng = next((h for h in r.get("eng", []) if str(h["arabicnumber"]) == str(want)), None)
        grades = (eng or {}).get("grades") or ara.get("grades") or []
        out[c["id"]] = {"number": str(want), "text": ara["text"], "grades": grades}
    with open(SOURCES, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1, sort_keys=True)
    print("sources:", len(out))


def build():
    src = json.load(open(SOURCES, encoding="utf-8"))
    items = []
    for c in C:
        if c["drop"]:
            continue
        s = src[c["id"]]
        # Canonical mark order (fatha before shadda, as NFC orders them) so texts compare and render alike.
        text = unicodedata.normalize("NFC", s["text"])
        for wrong, right in [(unicodedata.normalize("NFC", w), unicodedata.normalize("NFC", r)) for w, r in c["patch"]]:
            if wrong not in text:
                raise SystemExit(f"{c['id']}: patch target missing")
            text = text.replace(wrong, right)
        book = c["ref"].split(":")[0]
        main = extract(text, c["find_from"] or c["find"], c["find_to"], c["remove"])
        if not main:
            raise SystemExit(f"{c['id']}: wording not in {c['ref']}")
        if "�" in main:
            raise SystemExit(f"{c['id']}: damaged characters")
        spec, rounds = PARTS.get(c["id"], ([MAIN], 1))
        parts = []
        for p in spec:
            if p["kind"] == "main":
                part = {"text": main, "count": c["count"] if c["id"] not in PARTS else 1}
            elif p["kind"] == "find":
                t = extract(text, p["find"], p.get("to"))
                if not t:
                    raise SystemExit(f"{c['id']}: part not found: {p['find']}")
                part = {"text": t, "count": p["count"]}
            elif p["kind"] == "text":
                part = {"text": unicodedata.normalize("NFC", p["text"]), "count": p["count"]}
            else:
                part = {"quran": p["quran"], "count": p["count"]}
            for k in ("labelAr", "labelEn"):
                if p.get(k):
                    part[k] = p[k]
            parts.append(part)
        if c["find_evening"]:
            ev = extract(text, c["find_evening"])
            if not ev:
                raise SystemExit(f"{c['id']}: evening wording missing")
            parts[0]["evening"] = ev
        notes = [c["note"]] if c["note"] else []
        notes_en = []
        if c["id"] in MORNING_FROM_EVENING:
            subs, nar, nen = MORNING_FROM_EVENING[c["id"]]
            mt = parts[0]["text"]
            for a, b in subs:
                a, b = unicodedata.normalize("NFC", a), unicodedata.normalize("NFC", b)
                if a not in mt:
                    raise SystemExit(f"{c['id']}: morning substitution missing: {a}")
                mt = mt.replace(a, b)
            parts[0]["morning"] = mt
            parts[0]["evening"] = parts[0]["text"]
            notes.append(nar)
            notes_en.append(nen)
        # Grade.
        g = s["grades"]
        if book in ("bukhari", "muslim"):
            grade = {"ar": "صحيح", "en": "Sahih", "byAr": BOOKS[book][0], "byEn": BOOKS[book][1]}
        else:
            alb = next((x for x in g if x["name"] == "Al-Albani"), None)
            if not alb or alb["grade"] not in GRADES_AR:
                raise SystemExit(f"{c['id']}: grade {g}")
            if any("daif" in x["grade"].lower() for x in g):
                raise SystemExit(f"{c['id']}: graded weak by {g}")
            grade = {"ar": GRADES_AR[alb["grade"]], "en": alb["grade"], "byAr": "الألباني", "byEn": "Al-Albani"}
        ref = letter_ref(book, c["ref"], s["number"])
        num = ref.split(":")[1]
        num_plain = re.sub(r"[a-z]", "", num)
        links = [{"titleAr": "sunnah.com", "titleEn": "sunnah.com", "url": f"https://sunnah.com/{ref}"}]
        query = main if len(main) < 90 else " ".join(main.split()[:8])
        links.append({"titleAr": "بحث في الدرر السنية", "titleEn": "Search on Dorar", "url": "https://dorar.net/hadith/search?q=" + urllib.parse.quote(re.sub(MARKS, "", query))})
        for p in parts:
            if "quran" in p:
                q = p["quran"]
                tail = f"{q['sura']}" if q["from"] == 1 and q["sura"] != 2 else f"{q['sura']}/{q['from']}" + ("" if q["from"] == q["to"] else f"-{q['to']}")
                links.append({"titleAr": "quran.com: " + p["labelAr"], "titleEn": "quran.com: " + p["labelEn"], "url": f"https://quran.com/{tail}"})
        item = {
            "id": c["id"], "cat": c["cat"], "titleAr": c["titleAr"], "titleEn": c["titleEn"],
            "rounds": rounds, "parts": parts,
            "ctxAr": c["ctxAr"], "ctxEn": c["ctxEn"],
            "source": {"book": book, "number": num, "ar": f"{BOOKS[book][0]} {num_plain}", "en": f"{BOOKS[book][1]} {num}",
                       "grade": grade, "graders": [{"by": x["name"], "grade": x["grade"]} for x in g]},
            "links": links,
        }
        if notes:
            item["noteAr"] = " ".join(notes)
        if notes_en:
            item["noteEn"] = " ".join(notes_en)
        if c["count_note"]:
            item["countNoteAr"] = c["count_note"]
        items.append(item)

    ids = {i["id"] for i in items}
    practices = []
    for pid, ar, en, reminder, cAr, cEn in PRACTICES:
        order = ORDER[pid]
        tagged = {c["id"] for c in C if not c["drop"] and pid in c["practices"]}
        if set(order) != tagged:
            raise SystemExit(f"practice {pid}: order {sorted(set(order) ^ tagged)} differs from tags")
        practices.append({"id": pid, "titleAr": ar, "titleEn": en, "reminder": reminder, "ctxAr": cAr, "ctxEn": cEn, "items": order})
    assert all(i in ids for p in practices for i in p["items"])
    cats = [{"id": cid, "titleAr": ar, "titleEn": en} for cid, ar, en in CATEGORIES]
    assert {i["cat"] for i in items} == {c["id"] for c in cats}, "every category has items"
    if len(items) != 54 or len(cats) != 11 or len(practices) != 5:
        raise SystemExit(f"counts: {len(items)} items, {len(cats)} categories, {len(practices)} practices")
    data = {"version": 1, "source": "hadith-api (fawazahmed0), sunnah.com numbering; Quran text from the app's Tanzil copy",
            "categories": cats, "practices": practices, "items": items}
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    print("items", len(items), "categories", len(cats), "practices", len(practices), "->", OUT)


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "--refresh":
        refresh(sys.argv[2])
    build()
