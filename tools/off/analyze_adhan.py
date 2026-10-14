"""Downloads candidate adhan recordings from Wikimedia Commons, verifies their SHA-1 against the Commons API,
measures them with ffmpeg and transcribes them (faster-whisper) so the content can be reviewed."""
import hashlib, json, subprocess, sys, time, urllib.parse, urllib.request
from pathlib import Path

OUT = Path(sys.argv[1]) / "adhan"; OUT.mkdir(parents=True, exist_ok=True)
API = "https://commons.wikimedia.org/w/api.php"
UA = "SakinaResearch/0.1 (personal Android app; https://github.com/msjs6782/sakina)"
TITLES = [
    "File:Azan.ogg",
    "File:Beautiful adhan.ogg",
    "File:33937 ejaz215 call-to-prayer-from-the-prophet-s-mo.ogg",
    "File:The Adhan - Muslim Call to Prayer - Aaqib Azeez.mp3",
]

def get(url, binary=False):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for i in range(4):
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                return r.read()
        except Exception as e:
            print("retry", url, e); time.sleep(5)
    raise RuntimeError(url)

params = {"action": "query", "titles": "|".join(TITLES), "prop": "imageinfo", "iiprop": "url|size|mime|sha1|extmetadata", "format": "json", "formatversion": "2"}
info = json.loads(get(API + "?" + urllib.parse.urlencode(params)))
model_error = ""
try:
    from faster_whisper import WhisperModel
    model = WhisperModel("medium", device="cpu", compute_type="int8")
except Exception as e:
    import traceback
    model_error += "\n" + traceback.format_exc()
    print("whisper unavailable", e); model = None

report = []
for i, p in enumerate(info["query"]["pages"]):
    ii = p["imageinfo"][0]
    em = ii.get("extmetadata", {})
    ext = ii["url"].split("?")[0].rsplit(".", 1)[-1].lower()
    raw = get(ii["url"], True)
    sha1 = hashlib.sha1(raw).hexdigest()
    name = f"c{i:02d}.{ext}"
    (OUT / name).write_bytes(raw)
    wav = OUT / f"c{i:02d}.wav"
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(OUT / name), "-vn", "-ac", "1", "-ar", "16000", str(wav)], check=False)
    probe = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration,bit_rate:stream=codec_name,codec_type,sample_rate,channels", "-of", "json", str(OUT / name)], capture_output=True, text=True).stdout
    loud = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", str(OUT / name), "-vn", "-af", "ebur128=peak=true", "-f", "null", "-"], capture_output=True, text=True).stderr[-700:]
    sil = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", str(OUT / name), "-vn", "-af", "silencedetect=noise=-40dB:d=1.5", "-f", "null", "-"], capture_output=True, text=True).stderr
    silences = [l.split("silence_")[1] for l in sil.splitlines() if "silence_" in l][:40]
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(OUT / name), "-vn", "-lavfi", "showspectrumpic=s=1200x300:legend=1:scale=log", str(OUT / f"c{i:02d}_spectrum.png")], check=False)
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(OUT / name), "-vn", "-lavfi", "showwavespic=s=1200x200:split_channels=0", str(OUT / f"c{i:02d}_wave.png")], check=False)
    segments = []
    if model is not None and wav.exists():
        try:
            segs, _ = model.transcribe(str(wav), language="ar", vad_filter=False, beam_size=5, condition_on_previous_text=False,
                                       initial_prompt="الله أكبر الله أكبر، أشهد أن لا إله إلا الله، أشهد أن محمدا رسول الله، حي على الصلاة، حي على الفلاح")
            segments = [f"{s.start:6.1f}-{s.end:6.1f}  {s.text.strip()}" for s in segs]
            # Second pass without the prompt, so the prompt cannot put words in the transcript.
            segs2, _ = model.transcribe(str(wav), language="ar", vad_filter=False, beam_size=5, condition_on_previous_text=False)
            segments += ["--- without prompt ---"] + [f"{s.start:6.1f}-{s.end:6.1f}  {s.text.strip()}" for s in segs2]
        except Exception as e:
            segments = [f"transcription failed: {e}"]
    wav.unlink(missing_ok=True)
    if ext == "webm":
        # Keep only an audio copy of videos (Ogg Vorbis, unchanged content) to save space.
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(OUT / name), "-vn", "-c:a", "copy", str(OUT / f"c{i:02d}_audio.webm")], check=False)
        (OUT / name).unlink()
    rec = {
        "file": name, "title": p["title"], "page": ii.get("descriptionurl"), "url": ii["url"], "mime": ii.get("mime"), "size": ii.get("size"),
        "sha1_commons": ii.get("sha1"), "sha1_downloaded": sha1, "sha1_ok": sha1 == ii.get("sha1"),
        "sha256": hashlib.sha256(raw).hexdigest(),
        "license": em.get("LicenseShortName", {}).get("value"), "license_url": em.get("LicenseUrl", {}).get("value"),
        "artist": em.get("Artist", {}).get("value"), "credit": em.get("Credit", {}).get("value"),
        "attribution_required": em.get("AttributionRequired", {}).get("value"),
        "date": em.get("DateTimeOriginal", {}).get("value"), "description": em.get("ImageDescription", {}).get("value"),
        "probe": json.loads(probe or "{}"), "loudness_tail": loud, "silences": silences, "transcript": segments,
    }
    report.append(rec)
    print(p["title"], "sha1 ok" if rec["sha1_ok"] else "SHA1 MISMATCH")

(OUT / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=1))
(OUT / "model_error.txt").write_text(model_error)
