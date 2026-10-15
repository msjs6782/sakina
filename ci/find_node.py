#!/usr/bin/env python3
"""Prints the centre "x y" of the first UI node whose text, content-desc or resource-id matches.
Usage: find_node.py <ui.xml> <text> [exact|contains|icontains|id|idtext]
id matches a resource id exactly (with or without the package prefix) first, then by suffix.
idtext prints the text (or content-desc) of the node with that test tag instead of its centre."""
import re, sys
import xml.etree.ElementTree as ET

path, needle = sys.argv[1], sys.argv[2]
mode = sys.argv[3] if len(sys.argv) > 3 else "exact"
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(1)


def hit(n, strict):
    t, d, rid = n.get("text", ""), n.get("content-desc", ""), n.get("resource-id", "")
    if mode == "exact":
        return needle in (t, d)
    if mode == "contains":
        return needle in t or needle in d
    if mode == "icontains":
        return needle.lower() in t.lower() or needle.lower() in d.lower()
    if strict:
        return rid == needle or rid.endswith(":id/" + needle)
    return rid.endswith(needle)


for strict in ((True, False) if mode in ("id", "idtext") else (True,)):
    for n in root.iter("node"):
        if not hit(n, strict):
            continue
        if mode == "idtext":
            print(n.get("text", "") or n.get("content-desc", ""))
            sys.exit(0)
        m = re.findall(r"\d+", n.get("bounds", ""))
        if len(m) == 4:
            x1, y1, x2, y2 = map(int, m)
            print((x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit(0)
sys.exit(1)
