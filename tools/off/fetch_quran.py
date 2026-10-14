"""Fetches Quran text, metadata and translations from tanzil.net (official source), keeping files verbatim.
Tries known URL patterns and records what each returned."""
import hashlib, json, re, sys, time, urllib.parse, urllib.request
from pathlib import Path

OUT = Path(sys.argv[1]) / "quran"; OUT.mkdir(parents=True, exist_ok=True)
UA = "Mozilla/5.0 (X11; Linux x86_64) SakinaResearch/0.1 (personal app; https://github.com/msjs6782/sakina)"
log = []

def fetch(url, data=None):
    req = urllib.request.Request(url, data=data, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            body = r.read()
            return r.status, dict(r.headers), body
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers or {}), e.read() if hasattr(e, "read") else b""
    except Exception as e:
        return -1, {}, str(e).encode()

def attempt(name, urls, check):
    for u in urls:
        if isinstance(u, tuple):
            url, form = u
            st, hd, body = fetch(url, urllib.parse.urlencode(form).encode())
            label = url + " POST " + json.dumps(form)
        else:
            st, hd, body = fetch(u); label = u
        ok = st == 200 and check(body)
        log.append({"name": name, "url": label, "status": st, "type": hd.get("Content-Type"), "disposition": hd.get("Content-Disposition"),
                    "size": len(body), "ok": ok, "head": body[:300].decode("utf-8", "replace")})
        print(name, st, len(body), ok, label)
        if ok:
            (OUT / name).write_bytes(body)
            return True
        time.sleep(1)
    return False

def is_txt2(b):
    t = b.decode("utf-8", "replace")
    return len(re.findall(r"^\d+\|\d+\|", t, re.M)) >= 6236

def is_xml_quran(b):
    t = b.decode("utf-8", "replace")
    return "<quran" in t and t.count("<aya ") >= 6236

base = "https://tanzil.net/pub/download/index.php"
for qtype, fname in [("uthmani", "quran-uthmani.txt"), ("simple-clean", "quran-simple-clean.txt")]:
    opts = {"marks": "true", "sajdah": "true", "rub": "true", "alef": "true"} if qtype == "uthmani" else {}
    q = dict(opts, quranType=qtype, outType="txt-2", agree="true")
    attempt(fname, [base + "?" + urllib.parse.urlencode(q), (base, q), "https://tanzil.net/pub/download/v1.1/" + fname, "https://tanzil.net/res/text/" + fname], is_txt2)
qx = {"marks": "true", "sajdah": "true", "rub": "true", "alef": "true", "quranType": "uthmani", "outType": "xml", "agree": "true"}
attempt("quran-uthmani.xml", [base + "?" + urllib.parse.urlencode(qx), (base, qx)], is_xml_quran)

attempt("quran-data.xml", ["https://tanzil.net/res/text/metadata/quran-data.xml", "https://tanzil.net/res/text/metadata/quran-data.xml?v=1"],
        lambda b: b"<suras" in b and b"<pages" in b)
attempt("quran-data.js", ["https://tanzil.net/res/text/metadata/quran-data.js"], lambda b: b"Sura" in b and b"Page" in b)

for tid in ["en.itani", "ar.jalalayn"]:
    attempt(tid + ".txt", [
        f"https://tanzil.net/trans/?transID={tid}&type=txt-2",
        f"https://tanzil.net/trans/?transID={tid}&type=txt",
        f"https://tanzil.net/trans/{tid}",
        f"https://tanzil.net/trans/download/{tid}.txt",
    ], lambda b: len(b) > 200000)

# Terms pages, for the record.
for name, url in [("tanzil_download_page.html", "https://tanzil.net/download/"), ("tanzil_trans_page.html", "https://tanzil.net/trans/"),
                  ("clearquran_home.html", "https://www.clearquran.com/"), ("clearquran_about.html", "https://blog.clearquran.com/about")]:
    st, hd, body = fetch(url)
    log.append({"name": name, "url": url, "status": st, "size": len(body)})
    if st == 200:
        (OUT / name).write_bytes(body)

for f in sorted(OUT.iterdir()):
    if f.is_file():
        log.append({"file": f.name, "sha256": hashlib.sha256(f.read_bytes()).hexdigest(), "bytes": f.stat().st_size})
(OUT / "fetch_log.json").write_text(json.dumps(log, ensure_ascii=False, indent=1))
