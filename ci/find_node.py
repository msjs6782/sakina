#!/usr/bin/env python3
"""Prints the centre "x y" of the first UI node whose text, content-desc or resource-id matches.
Usage: find_node.py <ui.xml> <text> [exact|contains|id]"""
import re, sys
import xml.etree.ElementTree as ET

path, needle = sys.argv[1], sys.argv[2]
mode = sys.argv[3] if len(sys.argv) > 3 else "exact"
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(1)
for n in root.iter("node"):
    t, d, rid = n.get("text", ""), n.get("content-desc", ""), n.get("resource-id", "")
    hit = (mode == "exact" and needle in (t, d)) or (mode == "contains" and (needle in t or needle in d)) or (mode == "id" and rid.endswith(needle))
    if hit:
        m = re.findall(r"\d+", n.get("bounds", ""))
        if len(m) == 4:
            x1, y1, x2, y2 = map(int, m)
            print((x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit(0)
sys.exit(1)
