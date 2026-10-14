"""MP3Quran sources for Sakina's listening feature: reciters, files, and ayah timing maps.

Writes to <out>/mp3quran/:
  reciters_ar.json / reciters_eng.json   entries for Alafasy, Minshawi and Husary
  timing_reads.json                       timing reads for the same reciters
  heads_<moshaf>.tsv                      HEAD of each surah file: status, size, ranges, type, etag, last-modified
  probe_<moshaf>.tsv                      ffprobe duration and bitrate of each file (over HTTP)
  timing/<read>/<sura>.json               raw timing maps
  timing_check.txt                        structural checks and comparison with audio durations
"""
import concurrent.futures as cf
import json
import os
import subprocess
import sys
import time
import urllib.request

OUT = os.path.join(sys.argv[1] if len(sys.argv) > 1 else "out", "mp3quran")
os.makedirs(OUT, exist_ok=True)
UA = {"User-Agent": "sakina-asset-research/1.0 (personal app; GitHub Actions)"}
API = "https://www.mp3quran.net/api/v3"
NAMES = ["العفاسي", "المنشاوي", "الحصري"]
AYAHS = [7,286,200,176,120,165,206,75,129,109,123,111,43,52,99,128,111,110,98,135,112,78,118,64,77,227,93,88,69,60,34,30,73,54,45,83,182,88,75,85,54,53,89,59,37,35,38,29,18,45,60,49,62,55,78,96,29,22,24,13,14,11,11,18,12,12,30,52,52,44,28,28,20,56,40,31,50,40,46,42,29,19,36,25,22,17,19,26,30,20,15,21,11,8,8,19,5,8,8,11,11,8,3,9,5,4,7,3,6,3,5,4,5,6]
assert len(AYAHS) == 114 and sum(AYAHS) == 6236


def get(url, method="GET", timeout=60):
    req = urllib.request.Request(url, headers=UA, method=method)
    return urllib.request.urlopen(req, timeout=timeout)


def jget(url):
    with get(url) as r:
        return json.loads(r.read().decode("utf-8"))


def log(name, text):
    with open(os.path.join(OUT, name), "a", encoding="utf-8") as f:
        f.write(text + "\n")
    print(text)


def save(name, obj):
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)


def matches(name):
    return any(n in (name or "") for n in NAMES)


# 1. Reciters and their moshaf (recording sets).
moshafs = []
for lang in ("ar", "eng"):
    try:
        data = jget(f"{API}/reciters?language={lang}")
        rec = [r for r in data.get("reciters", []) if matches(r.get("name")) or lang == "eng" and any(k in (r.get("name") or "").lower() for k in ("afasy", "minsh", "husar", "hussar", "hosary", "husary"))]
        save(f"reciters_{lang}.json", rec)
        if lang == "ar":
            for r in rec:
                for m in r.get("moshaf", []):
                    moshafs.append({"reciter_id": r["id"], "reciter": r["name"], **m})
                    log("summary.txt", f"reciter {r['id']} {r['name']} | moshaf {m.get('id')} {m.get('name')} | type {m.get('moshaf_type')} | server {m.get('server')} | surahs {m.get('surah_total')}")
    except Exception as e:  # noqa: BLE001
        log("summary.txt", f"reciters {lang} failed: {e!r}")

# 2. Timing reads.
reads = []
try:
    data = jget(f"{API}/ayat_timing/reads")
    allr = data if isinstance(data, list) else data.get("reads", data)
    save("timing_reads_all.json", allr)
    reads = [r for r in allr if matches(r.get("name"))]
    save("timing_reads.json", reads)
    for r in reads:
        log("summary.txt", f"timing read {r}")
except Exception as e:  # noqa: BLE001
    log("summary.txt", f"timing reads failed: {e!r}")

# 3. HEAD every surah file of every Hafs moshaf of these reciters.
def head(url):
    for attempt in range(3):
        try:
            with get(url, "HEAD", timeout=30) as r:
                h = r.headers
                return (r.status, h.get("Content-Length"), h.get("Accept-Ranges"), h.get("Content-Type"), h.get("ETag"), h.get("Last-Modified"), r.geturl())
        except Exception as e:  # noqa: BLE001
            err = repr(e)
            time.sleep(1 + attempt)
    return ("ERR", err, "", "", "", "", url)


def probe(url):
    try:
        p = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration,bit_rate", "-of", "default=nw=1", url], capture_output=True, text=True, timeout=90)
        d = dict(line.split("=", 1) for line in p.stdout.split() if "=" in line)
        return d.get("duration", ""), d.get("bit_rate", "")
    except Exception as e:  # noqa: BLE001
        return "ERR", repr(e)[:60]


subprocess.run("sudo apt-get update -qq && sudo apt-get install -y -qq ffmpeg", shell=True, check=False, stdout=subprocess.DEVNULL)
durations = {}
for m in moshafs:
    server = m.get("server", "")
    surahs = [int(x) for x in str(m.get("surah_list", "")).split(",") if x.strip().isdigit()]
    tag = f"{m['id']}"
    urls = {s: f"{server}{s:03d}.mp3" for s in surahs}
    with cf.ThreadPoolExecutor(12) as ex:
        heads = dict(zip(urls, ex.map(head, urls.values())))
    total = 0
    with open(os.path.join(OUT, f"heads_{tag}.tsv"), "w", encoding="utf-8") as f:
        f.write("sura\tstatus\tbytes\tranges\ttype\tetag\tlast_modified\tfinal_url\n")
        for s in surahs:
            h = heads[s]
            f.write(f"{s}\t" + "\t".join(str(x) for x in h) + "\n")
            if h[0] == 200 and h[1]:
                total += int(h[1])
    biggest = max((int(h[1]) for h in heads.values() if h[0] == 200 and h[1]), default=0)
    log("summary.txt", f"moshaf {tag} {m['reciter']} {m.get('name')}: {len(surahs)} files, ok {sum(1 for h in heads.values() if h[0] == 200)}, total {total} bytes ({total/1048576:.1f} MiB), biggest {biggest/1048576:.1f} MiB, ranges {set(h[2] for h in heads.values())}")
    # Durations only for the Hafs murattal sets we are likely to use (all files, in parallel).
    if m.get("moshaf_type") in (11, "11") or "مرتل" in (m.get("name") or ""):
        with cf.ThreadPoolExecutor(8) as ex:
            probes = dict(zip(urls, ex.map(probe, urls.values())))
        durations[server] = {s: probes[s] for s in surahs}
        with open(os.path.join(OUT, f"probe_{tag}.tsv"), "w", encoding="utf-8") as f:
            f.write("sura\tduration\tbit_rate\n")
            for s in surahs:
                f.write(f"{s}\t{probes[s][0]}\t{probes[s][1]}\n")

# 4. Timing maps: fetch every surah of every matching read and check them.
for r in reads:
    rid = r.get("id")
    os.makedirs(os.path.join(OUT, "timing", str(rid)), exist_ok=True)
    folder = r.get("folder_url", "")
    dur = durations.get(folder) or next((v for k, v in durations.items() if k.rstrip("/").split("/")[-1] == folder.rstrip("/").split("/")[-1]), None)

    def fetch(s, rid=rid):
        for attempt in range(3):
            try:
                return s, jget(f"{API}/ayat_timing?surah={s}&read={rid}")
            except Exception as e:  # noqa: BLE001
                err = repr(e)
                time.sleep(1 + attempt)
        return s, {"error": err}

    with cf.ThreadPoolExecutor(8) as ex:
        res = dict(ex.map(fetch, range(1, 115)))
    good = 0
    for s in range(1, 115):
        t = res[s]
        save(f"timing/{rid}/{s:03d}.json", t)
        problems = []
        if not isinstance(t, list):
            problems.append(f"not a list: {str(t)[:80]}")
        else:
            ay = [x for x in t if int(x.get("ayah", -1)) >= 1]
            nums = [int(x["ayah"]) for x in ay]
            if nums != list(range(1, AYAHS[s - 1] + 1)):
                problems.append(f"ayahs {nums[:3]}..{nums[-3:]} count {len(nums)} expected {AYAHS[s - 1]}")
            st = [int(x.get("start_time", -1)) for x in ay]
            en = [int(x.get("end_time", -1)) for x in ay]
            if any(b < a for a, b in zip(st, st[1:])):
                problems.append("start times not increasing")
            if any(e < a for a, e in zip(st, en)):
                problems.append("an end before its start")
            if dur and s in dur and dur[s][0] not in ("", "ERR"):
                d_ms = float(dur[s][0]) * 1000
                last = max(en) if en else 0
                if last > d_ms + 1500:
                    problems.append(f"last end {last} ms after audio end {d_ms:.0f} ms")
                if last < d_ms - 60000:
                    problems.append(f"last end {last} ms far before audio end {d_ms:.0f} ms")
            extra = [x for x in t if int(x.get("ayah", -1)) < 1]
            if extra:
                problems.append(f"{len(extra)} non-ayah entries (ayah {[x.get('ayah') for x in extra][:3]})")
        if not problems:
            good += 1
        log("timing_check.txt", f"read {rid} sura {s}: {'OK' if not problems else '; '.join(problems)}")
    log("summary.txt", f"timing read {rid} {r.get('name')} folder {folder}: {good}/114 surahs pass the checks; durations matched to {'yes' if dur else 'no audio set'}")
