"""Inspect the King Fahd Complex digital Mushaf (Hafs, 1441H) zip mirrored on archive.org.

Writes to <out>/mushaf/:
  metadata_*.json      archive.org metadata of the items that carry the zip
  zip_check.txt        md5/sha1/sha256 of the downloaded zip vs. archive.org values
  listing.tsv          every entry: name, size, compressed size, crc, first bytes
  pdf_info.tsv         for each page file: PDF version, page count, media box
  manifest.json        page -> {name, size, sha256}
  inner_fetch.txt      test of downloading one entry directly from inside the zip on archive.org
  render/*.png         a few pages rendered with pdftoppm for visual review
  pages/               the raw files of pages 1 and 2 (to bundle in the app)
  texts/               any readme / license text found in the zip
"""
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import urllib.request
import zipfile

OUT = os.path.join(sys.argv[1] if len(sys.argv) > 1 else "out", "mushaf")
os.makedirs(OUT, exist_ok=True)
UA = {"User-Agent": "sakina-asset-research/1.0 (personal app; GitHub Actions)"}
ITEMS = ["1441-ai-hafs_202109", "MushafMadinaHafsGreen1441"]
ZIP_NAME = "1441-AI-hafs.zip"

subprocess.run([sys.executable, "-m", "pip", "install", "-q", "pypdf"], check=False)
subprocess.run("sudo apt-get update -qq && sudo apt-get install -y -qq poppler-utils", shell=True, check=False, stdout=subprocess.DEVNULL)
import shutil  # noqa: E402
import traceback  # noqa: E402

HAVE_POPPLER = shutil.which("pdftoppm") is not None


def run_tool(args, stdout=None):
    if not HAVE_POPPLER:
        return
    try:
        subprocess.run(args, check=False, stdout=stdout, timeout=120)
    except Exception:  # noqa: BLE001
        log("errors.txt", traceback.format_exc())


def get(url, timeout=120):
    req = urllib.request.Request(url, headers=UA)
    return urllib.request.urlopen(req, timeout=timeout)


def log(name, text):
    with open(os.path.join(OUT, name), "a", encoding="utf-8") as f:
        f.write(text + "\n")
    print(text)


# 1. Metadata of the items carrying the zip.
expected = {}
for item in ITEMS:
    try:
        meta = json.load(get(f"https://archive.org/metadata/{item}"))
        with open(os.path.join(OUT, f"metadata_{item}.json"), "w", encoding="utf-8") as f:
            json.dump(meta, f, ensure_ascii=False, indent=1)
        for fi in meta.get("files", []):
            if fi.get("name") == ZIP_NAME:
                expected[item] = {k: fi.get(k) for k in ("size", "md5", "sha1", "crc32", "mtime")}
                log("zip_check.txt", f"{item}: {expected[item]}")
    except Exception as e:  # noqa: BLE001
        log("zip_check.txt", f"{item}: metadata failed {e!r}")

# 2. Download the zip once and hash it.
zpath = os.path.join("/tmp", ZIP_NAME)
src = f"https://archive.org/download/{ITEMS[0]}/{ZIP_NAME}"
t0 = time.time()
h_md5, h_sha1, h_sha256 = hashlib.md5(), hashlib.sha1(), hashlib.sha256()
with get(src, timeout=600) as r, open(zpath, "wb") as f:
    while True:
        b = r.read(1 << 20)
        if not b:
            break
        f.write(b)
        h_md5.update(b); h_sha1.update(b); h_sha256.update(b)
log("zip_check.txt", f"poppler available: {HAVE_POPPLER}")
log("zip_check.txt", f"downloaded {os.path.getsize(zpath)} bytes in {time.time() - t0:.0f} s from {src}")
log("zip_check.txt", f"md5 {h_md5.hexdigest()}\nsha1 {h_sha1.hexdigest()}\nsha256 {h_sha256.hexdigest()}")
for item, e in expected.items():
    log("zip_check.txt", f"matches {item}: md5={e.get('md5') == h_md5.hexdigest()} sha1={e.get('sha1') == h_sha1.hexdigest()}")
log("zip_check.txt", "description md5 F21863DB9C9DEC8EB2C449201C63E116 match: " + str(h_md5.hexdigest().upper() == "F21863DB9C9DEC8EB2C449201C63E116"))

# 3. Listing.
z = zipfile.ZipFile(zpath)
infos = z.infolist()
total = 0
with open(os.path.join(OUT, "listing.tsv"), "w", encoding="utf-8") as f:
    f.write("name\tsize\tcompressed\tcrc\tdate\thead\n")
    for i in infos:
        head = b""
        if not i.is_dir():
            with z.open(i) as fh:
                head = fh.read(16)
            total += i.file_size
        f.write(f"{i.filename}\t{i.file_size}\t{i.compress_size}\t{i.CRC:08x}\t{i.date_time}\t{head!r}\n")
log("zip_check.txt", f"entries {len(infos)}, files {sum(1 for i in infos if not i.is_dir())}, uncompressed total {total} bytes = {total / 1048576:.2f} MiB")

# 4. Identify page files (a number 1..604 in the file name) and inspect them as PDF.
from pypdf import PdfReader  # noqa: E402

pages = {}
for i in infos:
    if i.is_dir():
        continue
    base = os.path.basename(i.filename)
    m = re.match(r"(\d{3})___", base)
    ext = os.path.splitext(base)[1].lower()
    if m and ext in (".pdf", ".ai"):
        n = int(m.group(1))
        if 1 <= n <= 604:
            pages.setdefault(n, []).append(i)
log("zip_check.txt", f"page numbers found: {len(pages)}; duplicates: {[n for n, v in pages.items() if len(v) > 1][:20]}")
log("zip_check.txt", f"missing: {[n for n in range(1, 605) if n not in pages][:50]}")

manifest = {}
page_total = 0
with open(os.path.join(OUT, "pdf_info.tsv"), "w", encoding="utf-8") as f:
    f.write("page\tname\tsize\tsha256\tversion\tnpages\tmediabox\tcropbox\tfonts_or_images\n")
    for n in sorted(pages):
        i = pages[n][0]
        data = z.read(i)
        sha = hashlib.sha256(data).hexdigest()
        page_total += len(data)
        manifest[n] = {"name": i.filename, "size": len(data), "sha256": sha}
        ver, npg, mb, cb, res = "", "", "", "", ""
        try:
            ver = data[:8].decode("latin1").strip()
            rd = PdfReader(__import__("io").BytesIO(data))
            npg = len(rd.pages)
            p0 = rd.pages[0]
            mb = [round(float(x), 2) for x in p0.mediabox]
            cb = [round(float(x), 2) for x in p0.cropbox]
            r0 = p0.get("/Resources") or {}
            res = ",".join(sorted(str(k) for k in r0.keys()))
        except Exception as e:  # noqa: BLE001
            res = f"ERR {e!r}"[:120]
        f.write(f"{n}\t{i.filename}\t{len(data)}\t{sha}\t{ver}\t{npg}\t{mb}\t{cb}\t{res}\n")
log("zip_check.txt", f"page files total {page_total} bytes = {page_total / 1048576:.2f} MiB")
with open(os.path.join(OUT, "manifest.json"), "w", encoding="utf-8") as f:
    json.dump({"source": src, "zip_md5": h_md5.hexdigest(), "zip_sha256": h_sha256.hexdigest(), "pages": manifest}, f, ensure_ascii=False, indent=1)

# 5. Texts (readme, license) inside the zip.
os.makedirs(os.path.join(OUT, "texts"), exist_ok=True)
for i in infos:
    if not i.is_dir() and os.path.splitext(i.filename)[1].lower() in (".txt", ".html", ".htm", ".md", ".rtf", ".url", ".docx", ".doc"):
        with open(os.path.join(OUT, "texts", re.sub(r"[^\w.-]", "_", i.filename)), "wb") as f:
            f.write(z.read(i))

# 6. Raw pages 1-2 and renders.
os.makedirs(os.path.join(OUT, "pages"), exist_ok=True)
os.makedirs(os.path.join(OUT, "render"), exist_ok=True)
for n in (1, 2, 3, 50, 187, 300, 582, 603, 604):
    if n not in pages:
        continue
    data = z.read(pages[n][0])
    ext = os.path.splitext(pages[n][0].filename)[1].lower()
    raw = os.path.join("/tmp", f"p{n:03d}{ext}")
    with open(raw, "wb") as f:
        f.write(data)
    if n in (1, 2):
        with open(os.path.join(OUT, "pages", f"{n:03d}{ext}"), "wb") as f:
            f.write(data)
    run_tool(["pdftoppm", "-r", "60", "-png", "-singlefile", raw, os.path.join(OUT, "render", f"p{n:03d}")])
    run_tool(["pdfinfo", raw], stdout=open(os.path.join(OUT, "render", f"p{n:03d}_pdfinfo.txt"), "w"))
    run_tool(["pdffonts", raw], stdout=open(os.path.join(OUT, "render", f"p{n:03d}_pdffonts.txt"), "w"))

# 6b. Local header offsets, for fetching single entries with HTTP Range requests.
import struct  # noqa: E402
import zlib  # noqa: E402

ranges = {}
with open(zpath, "rb") as fz:
    for n in sorted(pages):
        i = pages[n][0]
        fz.seek(i.header_offset)
        hdr = fz.read(30)
        sig, ver, flag, method, mt, md, crc, csz, usz, nlen, xlen = struct.unpack("<IHHHHHIIIHH", hdr)
        assert sig == 0x04034B50, n
        data_off = i.header_offset + 30 + nlen + xlen
        ranges[n] = {"offset": i.header_offset, "dataOffset": data_off, "compressed": i.compress_size, "method": i.compress_type,
                     "crc32": f"{i.CRC:08x}", "flag": flag}
        manifest[n].update(ranges[n])
with open(os.path.join(OUT, "manifest.json"), "w", encoding="utf-8") as f:
    json.dump({"source": src, "zip_size": os.path.getsize(zpath), "zip_md5": h_md5.hexdigest(), "zip_sha256": h_sha256.hexdigest(), "pages": manifest}, f, ensure_ascii=False, indent=1)
log("zip_check.txt", f"methods {sorted(set(r['method'] for r in ranges.values()))} flags {sorted(set(r['flag'] for r in ranges.values()))}")

# Raw folder name bytes, decoded with a few code pages.
raw_name = infos[0].filename.encode("cp437", "replace")
for cp in ("utf-8", "cp720", "cp864", "cp1256"):
    try:
        log("zip_check.txt", f"folder name as {cp}: {raw_name.decode(cp)}")
    except Exception as e:  # noqa: BLE001
        log("zip_check.txt", f"folder name as {cp}: failed {e}")


def range_fetch(base_url, n):
    r_ = ranges[n]
    end = r_["dataOffset"] + r_["compressed"] - 1
    req = urllib.request.Request(base_url, headers={**UA, "Range": f"bytes={r_['dataOffset']}-{end}"})
    t0_ = time.time()
    with urllib.request.urlopen(req, timeout=120) as resp:
        body_ = resp.read()
        info_ = f"status {resp.status} final {resp.geturl()} content-range {resp.headers.get('Content-Range')} accept-ranges {resp.headers.get('Accept-Ranges')} len {len(body_)} in {time.time() - t0_:.2f} s"
    data_ = zlib.decompress(body_, -15) if r_["method"] == 8 else body_
    ok_ = hashlib.sha256(data_).hexdigest() == manifest[n]["sha256"] and len(data_) == manifest[n]["size"]
    return info_, ok_


for base in (f"https://archive.org/download/{ITEMS[0]}/{ZIP_NAME}", f"https://archive.org/download/{ITEMS[1]}/{ZIP_NAME}"):
    for n in (3, 300, 604):
        try:
            info, ok = range_fetch(base, n)
            log("range_fetch.txt", f"{base} page {n}: {info} verified={ok}")
        except Exception as e:  # noqa: BLE001
            log("range_fetch.txt", f"{base} page {n}: failed {e!r}")

# Text layer: is the Quran text extractable from a page (to compare page boundaries with Tanzil)?
for n in (3, 50, 187, 300):
    if n in pages:
        raw = os.path.join("/tmp", f"p{n:03d}.ai")
        with open(raw, "wb") as f:
            f.write(z.read(pages[n][0]))
        run_tool(["pdftotext", "-layout", raw, os.path.join(OUT, "render", f"p{n:03d}.txt")])

# 7. Can one entry be fetched directly from inside the zip on archive.org (no full download)?
if 3 in pages:
    inner = pages[3][0].filename
    url = f"https://archive.org/download/{ITEMS[0]}/{ZIP_NAME}/" + urllib.request.quote(inner)
    try:
        t0 = time.time()
        with get(url) as r:
            body = r.read()
            hdrs = dict(r.headers)
        log("inner_fetch.txt", f"url {url}\nfinal {r.geturl()}\nstatus {r.status} bytes {len(body)} in {time.time() - t0:.1f} s")
        log("inner_fetch.txt", f"sha256 {hashlib.sha256(body).hexdigest()} expected {manifest[3]['sha256']} match {hashlib.sha256(body).hexdigest() == manifest[3]['sha256']}")
        log("inner_fetch.txt", json.dumps(hdrs, indent=1))
    except Exception as e:  # noqa: BLE001
        log("inner_fetch.txt", f"inner fetch failed {url}: {e!r}")
