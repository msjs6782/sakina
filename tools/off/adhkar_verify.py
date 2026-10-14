"""Verifies each candidate dhikr against its sunnah.com page: the reference exists, the wording is in
the hadith's Arabic text, and the grade shown there. Respects sunnah.com's robots.txt.

Writes <out>/adhkar/verify.json and verify.txt, plus the raw Arabic text of each hadith.
"""
import html
import json
import os
import re
import sys
import time
import urllib.request
import urllib.robotparser

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "lib"))
from textnorm import norm  # noqa: E402
from candidates import C  # noqa: E402

OUT = os.path.join(sys.argv[1] if len(sys.argv) > 1 else "out", "adhkar")
os.makedirs(os.path.join(OUT, "pages"), exist_ok=True)
UA = "sakina-asset-research/1.0 (personal app; GitHub Actions)"


def log(text):
    with open(os.path.join(OUT, "verify.txt"), "a", encoding="utf-8") as f:
        f.write(text + "\n")
    print(text)


rp = urllib.robotparser.RobotFileParser()
rp.set_url("https://sunnah.com/robots.txt")
try:
    rp.read()
    log("robots.txt read")
except Exception as e:  # noqa: BLE001
    log(f"robots.txt failed: {e!r}")


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "en"})
    with urllib.request.urlopen(req, timeout=40) as r:
        return r.geturl(), r.read().decode("utf-8", "replace")


def strip_tags(s):
    return html.unescape(re.sub(r"<[^>]+>", " ", s))


results = {}
for c in C:
    url = "https://sunnah.com/" + c["ref"]
    if not rp.can_fetch(UA, url):
        log(f"{c['id']}: robots.txt disallows {url}")
        results[c["id"]] = {"ok": False, "reason": "robots"}
        continue
    try:
        final, page = fetch(url)
    except Exception as e:  # noqa: BLE001
        log(f"{c['id']}: fetch failed {url}: {e!r}")
        results[c["id"]] = {"ok": False, "reason": f"fetch {e!r}"}
        time.sleep(1)
        continue
    if not os.path.exists(os.path.join(OUT, "pages", "_sample.html")):
        with open(os.path.join(OUT, "pages", "_sample.html"), "w", encoding="utf-8") as f:
            f.write(page)
    # The Arabic text and the grade of the (first) hadith on the page.
    arab = re.findall(r'<div class="arabic_hadith_full arabic">(.*?)</div>\s*</div>', page, re.S) or re.findall(r'class="arabic_hadith_full[^"]*"[^>]*>(.*?)</div>', page, re.S)
    arabic = strip_tags(" ".join(arab))
    grades = [re.sub(r"\s+", " ", strip_tags(g)).strip() for g in re.findall(r'<td class="english_grade"[^>]*>(.*?)</td>', page, re.S)]
    agrades = [re.sub(r"\s+", " ", strip_tags(g)).strip() for g in re.findall(r'<td class="arabic_grade[^"]*"[^>]*>(.*?)</td>', page, re.S)]
    refs = [re.sub(r"\s+", " ", strip_tags(g)).strip() for g in re.findall(r'<table class="hadith_reference">(.*?)</table>', page, re.S)][:1]
    n = norm(arabic)
    phrases = [c["find"]] if c["find"] else [c["find_from"], c["find_to"]]
    if c.get("find_evening"):
        phrases.append(c["find_evening"])
    missing = [p for p in phrases if norm(p) not in n]
    ok = bool(arabic) and not missing
    results[c["id"]] = {"ok": ok, "url": final, "arabic": arabic.strip(), "grades": grades, "arabicGrades": agrades, "reference": refs, "missing": missing}
    with open(os.path.join(OUT, "pages", c["id"] + ".txt"), "w", encoding="utf-8") as f:
        f.write(arabic)
    log(f"{c['id']}: {'OK' if ok else 'MISMATCH'} {final} grades={grades[:2]} {('missing ' + str(missing)) if missing else ''} {'(no arabic found)' if not arabic else ''}")
    time.sleep(1.2)

with open(os.path.join(OUT, "verify.json"), "w", encoding="utf-8") as f:
    json.dump(results, f, ensure_ascii=False, indent=1)
log(f"verified {sum(1 for r in results.values() if r.get('ok'))} of {len(results)}")
