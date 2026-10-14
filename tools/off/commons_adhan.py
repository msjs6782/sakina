"""Searches Wikimedia Commons for adhan recordings and records license metadata (no downloads)."""
import json, re, sys, time, urllib.parse, urllib.request
from pathlib import Path

OUT = Path(sys.argv[1]); OUT.mkdir(exist_ok=True)
API = "https://commons.wikimedia.org/w/api.php"
UA = "SakinaResearch/0.1 (personal Android app; https://github.com/msjs6782/sakina)"

def q(params):
    params = dict(params, format="json", formatversion="2")
    req = urllib.request.Request(API + "?" + urllib.parse.urlencode(params), headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)

titles = set()
for term in ["adhan", "azan", "athan", "adan islam", "call to prayer", "muezzin", "أذان", "اذان", "ezan", "azaan"]:
    off = 0
    while off is not None and off < 400:
        d = q({"action": "query", "list": "search", "srsearch": term + " filetype:audio", "srnamespace": 6, "srlimit": 100, "sroffset": off})
        for h in d["query"]["search"]:
            titles.add(h["title"])
        off = d.get("continue", {}).get("sroffset")
        time.sleep(0.3)

def cat_files(cat, depth):
    cont = {}
    while True:
        d = q(dict({"action": "query", "list": "categorymembers", "cmtitle": cat, "cmlimit": 500, "cmtype": "file|subcat"}, **cont))
        for m in d["query"]["categorymembers"]:
            if m["ns"] == 6:
                titles.add(m["title"])
            elif m["ns"] == 14 and depth > 0:
                cat_files(m["title"], depth - 1)
        if "continue" not in d:
            break
        cont = d["continue"]

for c in ["Category:Adhan", "Category:Audio files of adhan", "Category:Azan", "Category:Adhan (audio)", "Category:Muezzins"]:
    try:
        cat_files(c, 2)
    except Exception as e:
        print("category failed", c, e)

def strip(h):
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", h or "")).strip()

rows = []
tl = sorted(titles)
for i in range(0, len(tl), 40):
    d = q({"action": "query", "titles": "|".join(tl[i:i + 40]), "prop": "imageinfo", "iiprop": "url|size|mime|extmetadata|metadata|mediatype"})
    for p in d["query"]["pages"]:
        ii = (p.get("imageinfo") or [{}])[0]
        if ii.get("mediatype") not in ("AUDIO", "VIDEO") and not str(ii.get("mime", "")).startswith("audio"):
            continue
        em = ii.get("extmetadata", {})
        meta = {m["name"]: m["value"] for m in (ii.get("metadata") or []) if isinstance(m, dict) and "name" in m}
        length = meta.get("length") or meta.get("playtime_seconds")
        rows.append({
            "title": p["title"], "mime": ii.get("mime"), "size": ii.get("size"), "length": length,
            "license": strip(em.get("LicenseShortName", {}).get("value")),
            "license_url": strip(em.get("LicenseUrl", {}).get("value")),
            "artist": strip(em.get("Artist", {}).get("value")),
            "credit": strip(em.get("Credit", {}).get("value")),
            "date": strip(em.get("DateTimeOriginal", {}).get("value")),
            "description": strip(em.get("ImageDescription", {}).get("value"))[:400],
            "categories": strip(em.get("Categories", {}).get("value")),
            "url": ii.get("url"), "page": ii.get("descriptionurl"),
        })
    time.sleep(0.3)

rows.sort(key=lambda r: r["title"])
(OUT / "commons_adhan.json").write_text(json.dumps(rows, ensure_ascii=False, indent=1))
with open(OUT / "commons_adhan.tsv", "w") as f:
    for r in rows:
        f.write("\t".join(str(r[k]) for k in ["title", "mime", "size", "length", "license", "artist", "date", "description"]) + "\n")
print(len(rows), "audio files")
