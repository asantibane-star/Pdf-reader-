#!/usr/bin/env python3
"""Imprime en el log de la CI cada prueba instrumentada fallida con su mensaje y primeras líneas de pila."""
import glob
import sys
import xml.etree.ElementTree as ET

total = failed = 0
for path in sorted(glob.glob("app/build/outputs/androidTest-results/connected/**/*.xml", recursive=True)):
    root = ET.parse(path).getroot()
    for case in root.iter("testcase"):
        total += 1
        problems = list(case.iter("failure")) + list(case.iter("error"))
        if not problems:
            continue
        failed += 1
        print(f"\n### FALLA {case.get('classname')}.{case.get('name')}")
        text = (problems[0].text or problems[0].get("message") or "").strip().splitlines()
        print("\n".join(text[:14]))
print(f"\nResumen: {total} pruebas instrumentadas, {failed} fallidas", file=sys.stderr)
