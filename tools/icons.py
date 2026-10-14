#!/usr/bin/env python3
"""Sakina's line icons (24x24, 1.8 stroke). Generates ui/components/Icons.kt and an HTML preview."""
import sys
from pathlib import Path

ICONS = {
    "Home": "M4 10.5 L12 4 L20 10.5 V20 H14.5 V15.5 A2.5 2.5 0 0 0 9.5 15.5 V20 H4 Z",
    "Clock": "M21 12 A9 9 0 1 1 3 12 A9 9 0 1 1 21 12 Z M12 7 V12 L15.5 14",
    "Book": "M12 6.5 C10 5 7 4.5 3.5 5 V18.5 C7 18 10 18.5 12 20 C14 18.5 17 18 20.5 18.5 V5 C17 4.5 14 5 12 6.5 Z M12 6.5 V20",
    "Compass": "M21 12 A9 9 0 1 1 3 12 A9 9 0 1 1 21 12 Z M15.5 8.5 L13.4 13.4 L8.5 15.5 L10.6 10.6 Z",
    "Sliders": "M4 7 H20 M4 12 H20 M4 17 H20 M15 5 V9 M9 10 V14 M13 15 V19",
    "Pin": "M12 21 C12 21 5 14.5 5 9.5 A7 7 0 0 1 19 9.5 C19 14.5 12 21 12 21 Z M14.5 9.5 A2.5 2.5 0 1 1 9.5 9.5 A2.5 2.5 0 1 1 14.5 9.5 Z",
    "Gps": "M19 12 A7 7 0 1 1 5 12 A7 7 0 1 1 19 12 Z M12 2.5 V5 M12 19 V21.5 M2.5 12 H5 M19 12 H21.5 M14 12 A2 2 0 1 1 10 12 A2 2 0 1 1 14 12 Z",
    "Plus": "M12 5 V19 M5 12 H19",
    "Search": "M16.5 10.5 A6 6 0 1 1 4.5 10.5 A6 6 0 1 1 16.5 10.5 Z M15 15 L20 20",
    "ChevronEnd": "M9 5 L16 12 L9 19",
    "ChevronStart": "M15 5 L8 12 L15 19",
    "ArrowStart": "M19 12 H5 M11 6 L5 12 L11 18",
    "Edit": "M4 20 H8 L19 9 L15 5 L4 16 Z M13 7 L17 11",
    "Trash": "M5 7 H19 M10 7 V4.5 H14 V7 M7 7 L8 20 H16 L17 7 M10 11 V16 M14 11 V16",
    "Check": "M5 12.5 L10 17 L19 7",
    "Close": "M6 6 L18 18 M18 6 L6 18",
    "ArrowUp": "M12 19 V5 M6 11 L12 5 L18 11",
    "ArrowDown": "M12 5 V19 M6 13 L12 19 L18 13",
    "Suitcase": "M4 8 H20 V19 H4 Z M9 8 V5.5 H15 V8 M4 13 H20",
    "Info": "M21 12 A9 9 0 1 1 3 12 A9 9 0 1 1 21 12 Z M12 11 V16.5 M12 7.6 V7.7",
    "Warning": "M12 4 L21 19.5 H3 Z M12 10 V14 M12 16.9 V17",
    "Calendar": "M4 6 H20 V20 H4 Z M4 10.5 H20 M8 3.5 V7.5 M16 3.5 V7.5",
    "Palette": "M12 3 A9 9 0 1 0 12 21 C13.2 21 13.6 20 13 19 C12.4 18 12.8 16.5 14.5 16.5 H17 A4 4 0 0 0 21 12.5 C21 7.3 17 3 12 3 Z M8 10 V10.1 M12 7 V7.1 M16 10 V10.1",
    "Grid": "M4 4 H10 V10 H4 Z M14 4 H20 V10 H14 Z M4 14 H10 V20 H4 Z M14 14 H20 V20 H14 Z",
    "Globe": "M21 12 A9 9 0 1 1 3 12 A9 9 0 1 1 21 12 Z M3 12 H21 M12 3 C9.5 6 9.5 18 12 21 C14.5 18 14.5 6 12 3 Z",
    "Fajr": "M3 18 H21 M6 18 A6 6 0 0 1 18 18 M9 18 A3 3 0 0 1 15 18 M12 5 V8",
    "Sunrise": "M3 18 H21 M7.5 18 A4.5 4.5 0 0 1 16.5 18 M12 7.5 V10 M5.2 11.2 L6.8 12.8 M18.8 11.2 L17.2 12.8",
    "Dhuhr": "M16 12 A4 4 0 1 1 8 12 A4 4 0 1 1 16 12 Z M12 3 V5 M12 19 V21 M3 12 H5 M19 12 H21 M5.6 5.6 L7 7 M17 17 L18.4 18.4 M5.6 18.4 L7 17 M17 7 L18.4 5.6",
    "Asr": "M19 7.5 A2.5 2.5 0 1 1 14 7.5 A2.5 2.5 0 1 1 19 7.5 Z M8 19 V11 M3 19 H21 M8 11 L3.5 19",
    "Maghrib": "M3 18 H21 M7.5 18 A4.5 4.5 0 0 1 16.5 18 M12 5.5 V11 M9.5 8.5 L12 11 L14.5 8.5",
    "Isha": "M17 15.5 A7 7 0 1 1 8.5 5 A5.5 5.5 0 0 0 17 15.5 Z M18.5 3 Q18.9 5.6 21.5 6 Q18.9 6.4 18.5 9 Q18.1 6.4 15.5 6 Q18.1 5.6 18.5 3 Z",
    "Moon": "M20 14.5 A8 8 0 1 1 9.5 4 A6.5 6.5 0 0 0 20 14.5 Z",
    "Sun": "M16 12 A4 4 0 1 1 8 12 A4 4 0 1 1 16 12 Z M12 3 V5 M12 19 V21 M3 12 H5 M19 12 H21 M5.6 5.6 L7 7 M17 17 L18.4 18.4 M5.6 18.4 L7 17 M17 7 L18.4 5.6",
    "Table": "M4 5 H20 V19 H4 Z M4 10 H20 M4 14.5 H20 M10 5 V19",
    "Bell": "M6 16.5 V11 A6 6 0 0 1 18 11 V16.5 L19.5 18 H4.5 Z M10 20.5 A2 2 0 0 0 14 20.5",
    "BellOff": "M6 16.5 V11 A6 6 0 0 1 15.5 6.2 M18 11 V16.5 L19.5 18 H8 M10 20.5 A2 2 0 0 0 14 20.5 M4 4 L20 20",
    "Play": "M8 5.5 L18.5 12 L8 18.5 Z",
    "StopSq": "M7 7 H17 V17 H7 Z",
    "Volume": "M4 9.5 H8 L13 5.5 V18.5 L8 14.5 H4 Z M16 9 A4 4 0 0 1 16 15 M18.5 6.5 A7.5 7.5 0 0 1 18.5 17.5",
    "Music": "M9 17.5 V6 L19 4 V15.5 M9 17.5 A2.5 2.5 0 1 1 4 17.5 A2.5 2.5 0 1 1 9 17.5 Z M19 15.5 A2.5 2.5 0 1 1 14 15.5 A2.5 2.5 0 1 1 19 15.5 Z",
    "Shield": "M12 3 L19.5 6 V11.5 C19.5 16 16.5 19.5 12 21 C7.5 19.5 4.5 16 4.5 11.5 V6 Z M9 12 L11.2 14.2 L15.2 10",
    "Battery": "M4 8 H18 V16 H4 Z M18 10.5 H20 V13.5 H18 M7 11 V13 M10 11 V13",
    "Flask": "M9.5 3.5 H14.5 M10.5 3.5 V9 L5.5 18.5 A1.5 1.5 0 0 0 6.8 20.5 H17.2 A1.5 1.5 0 0 0 18.5 18.5 L13.5 9 V3.5 M7.8 15 H16.2",
    "Repeat": "M17 3.5 L20 6.5 L17 9.5 M4 11.5 V9.5 A3 3 0 0 1 7 6.5 H20 M7 20.5 L4 17.5 L7 14.5 M20 12.5 V14.5 A3 3 0 0 1 17 17.5 H4",
    "Download": "M12 4 V15 M7 10.5 L12 15.5 L17 10.5 M5 19.5 H19",
    "Page": "M6 3.5 H14.5 L19 8 V20.5 H6 Z M14.5 3.5 V8 H19 M9 12.5 H16 M9 16 H16",
    "Lines": "M5 6.5 H19 M5 10.5 H19 M5 14.5 H19 M5 18.5 H13",
    "Bookmark": "M7 4 H17 V20 L12 16.2 L7 20 Z",
    "ListIndex": "M9.5 6 H20 M9.5 12 H20 M9.5 18 H20 M4.5 6 V6.1 M4.5 12 V12.1 M4.5 18 V18.1",
    "More": "M12.9 5.5 A0.9 0.9 0 1 1 11.1 5.5 A0.9 0.9 0 1 1 12.9 5.5 Z M12.9 12 A0.9 0.9 0 1 1 11.1 12 A0.9 0.9 0 1 1 12.9 12 Z M12.9 18.5 A0.9 0.9 0 1 1 11.1 18.5 A0.9 0.9 0 1 1 12.9 18.5 Z",
    "Storage": "M4 6.5 C4 4.5 20 4.5 20 6.5 V17.5 C20 19.5 4 19.5 4 17.5 Z M4 6.5 C4 8.5 20 8.5 20 6.5 M4 12 C4 14 20 14 20 12",
}

def kotlin():
    out = ["package com.sakina.app.ui.components", "",
           "import androidx.compose.ui.graphics.Color",
           "import androidx.compose.ui.graphics.SolidColor",
           "import androidx.compose.ui.graphics.StrokeCap",
           "import androidx.compose.ui.graphics.StrokeJoin",
           "import androidx.compose.ui.graphics.vector.ImageVector",
           "import androidx.compose.ui.graphics.vector.addPathNodes",
           "import androidx.compose.ui.unit.dp", "",
           "// Generated by tools/icons.py. Line icons drawn for Sakina (24×24 grid, 1.8 stroke).",
           "object SakinaIcons {",
           "    private fun icon(name: String, path: String): ImageVector = ImageVector.Builder(",
           "        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,",
           "    ).addPath(",
           "        pathData = addPathNodes(path),",
           "        fill = null,",
           "        stroke = SolidColor(Color.Black),",
           "        strokeLineWidth = 1.8f,",
           "        strokeLineCap = StrokeCap.Round,",
           "        strokeLineJoin = StrokeJoin.Round,",
           "    ).build()", ""]
    for k, v in ICONS.items():
        out.append(f'    val {k}: ImageVector by lazy {{ icon("{k}", "{v}") }}')
    out.append("}")
    return "\n".join(out) + "\n"

def html():
    cells = "".join(f'<div><svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#1c1b17" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="{v}"/></svg><span>{k}</span></div>' for k, v in ICONS.items())
    return f'<html><body style="margin:0;background:#f6f3ec;font:11px sans-serif"><div style="display:grid;grid-template-columns:repeat(8,90px);gap:6px;padding:10px">{cells}</div><style>div>div{{display:flex;flex-direction:column;align-items:center;gap:4px}}</style></body></html>'

if __name__ == "__main__":
    root = Path(__file__).resolve().parent.parent
    (root / "app/src/main/java/com/sakina/app/ui/components/Icons.kt").write_text(kotlin())
    if len(sys.argv) > 1:
        Path(sys.argv[1]).write_text(html())
