"""Arabic normalisation shared by the adhkar tools: match wording regardless of diacritics and letter forms."""
import re

MARKS = re.compile("[ؐ-ًؚ-ٰٟۖ-ۭـ‌-‏‪-‮⁦-⁩]")
FOLD = str.maketrans({"أ": "ا", "إ": "ا", "آ": "ا", "ٱ": "ا", "ى": "ي", "ة": "ه", "ؤ": "و", "ئ": "ي", "ء": ""})
KEEP = re.compile("[ء-ي]")


def norm_with_map(text: str):
    """Returns (normalised letters joined by single spaces, list mapping each normalised char to its index in text)."""
    out, idx = [], []
    space = True
    for i, ch in enumerate(text):
        if MARKS.match(ch):
            continue
        c = ch.translate(FOLD)
        if not c:
            continue
        if KEEP.match(c):
            out.append(c)
            idx.append(i)
            space = False
        elif not space:
            out.append(" ")
            idx.append(i)
            space = True
    s = "".join(out).rstrip()
    return s, idx[: len(s)]


def norm(text: str) -> str:
    return norm_with_map(text)[0]
