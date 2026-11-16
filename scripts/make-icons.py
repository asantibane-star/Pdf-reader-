#!/usr/bin/env python3
"""Convierte docs/design/icons/*-light.svg (Phosphor Light, MIT) a VectorDrawable de 24 dp.

Uso: python3 -I scripts/make-icons.py
Cada SVG de Phosphor es un único <path> con viewBox 256x256; el color lo pone Compose con `tint`.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "docs" / "design" / "icons"
DST = ROOT / "app" / "src" / "main" / "res" / "drawable"

TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!-- Phosphor Icons (Light), MIT. Generado por scripts/make-icons.py; no editar a mano. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="256"
    android:viewportHeight="256">
    <path
        android:fillColor="#FF000000"
        android:pathData="{path}" />
</vector>
"""


def main() -> int:
    DST.mkdir(parents=True, exist_ok=True)
    count = 0
    for svg in sorted(SRC.glob("*-light.svg")):
        text = svg.read_text(encoding="utf-8")
        paths = re.findall(r'<path[^>]*\sd="([^"]+)"', text)
        if len(paths) != 1 or re.search(r"<(circle|rect|line|polyline|polygon|g)\b", text):
            print(f"ERROR: {svg.name} no es un único <path>", file=sys.stderr)
            return 1
        name = "ic_" + svg.stem.removesuffix("-light").replace("-", "_")
        (DST / f"{name}.xml").write_text(TEMPLATE.format(path=paths[0]), encoding="utf-8")
        count += 1
    print(f"{count} iconos escritos en {DST}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
