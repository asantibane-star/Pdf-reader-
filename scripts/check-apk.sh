#!/usr/bin/env bash
# Verifica un APK (PLAN §4.4): sin ningún uses-permission, sin `debuggable` en release, como máximo una
# Activity exportada y ningún receiver/provider exportado.
# Uso: scripts/check-apk.sh <apk> [--debug]
#   --debug  admite debuggable=true y la Activity de pruebas de Compose (solo para el APK de depuración).
set -euo pipefail

APK="${1:?Uso: check-apk.sh <apk> [--debug]}"
MODE="${2:-release}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
AAPT2="$(ls -1 "$SDK"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1 || true)"
[ -x "$AAPT2" ] || { echo "No se encontró aapt2 (define ANDROID_HOME)"; exit 2; }

fail=0
ok()  { echo "ok    $*"; }
bad() { echo "FALLA $*"; fail=1; }

permissions="$("$AAPT2" dump permissions "$APK" | grep -E '^uses-permission' || true)"
if [ -z "$permissions" ]; then ok "el APK no declara ningún permiso"; else bad "el APK declara permisos:"; echo "$permissions"; fi

report="$("$AAPT2" dump xmltree --file AndroidManifest.xml "$APK" | python3 -I -c '
import re, sys
debuggable = False
exported = {"activity": [], "receiver": [], "provider": [], "service": []}
stack = []  # (indent, tag, name, exported)
def flush(upto):
    while stack and stack[-1][0] >= upto:
        _, tag, name, exp = stack.pop()
        if tag in exported and exp:
            exported[tag].append(name or "?")
for line in sys.stdin:
    m = re.match(r"^(\s*)E: (\S+)", line)
    if m:
        flush(len(m.group(1)))
        stack.append((len(m.group(1)), m.group(2), None, False))
        continue
    a = re.match(r"^(\s*)A: (?:\S+:)?(\w+)\(0x[0-9a-f]+\)=(.*)$", line)
    if a and stack:
        key, val = a.group(2), a.group(3)
        indent, tag, name, exp = stack[-1]
        if key == "name" and tag in exported:
            name = re.search(r"\"([^\"]+)\"", val).group(1)
        if key == "exported" and val.strip().startswith("true"):
            exp = True
        if key == "debuggable" and val.strip().startswith("true"):
            debuggable = True
        stack[-1] = (indent, tag, name, exp)
flush(0)
print("debuggable=" + str(debuggable).lower())
for tag, names in exported.items():
    print(tag + "=" + ",".join(names))
')"

get() { echo "$report" | grep "^$1=" | cut -d= -f2-; }
count() { [ -z "$1" ] && echo 0 || echo "$1" | tr "," "\n" | grep -c .; }

if [ "$(get debuggable)" = "true" ]; then
  [ "$MODE" = "--debug" ] && ok "debuggable (esperado en depuración)" || bad "el APK es debuggable"
else
  ok "no es debuggable"
fi

acts="$(get activity)"; n="$(count "$acts")"; limit=1; [ "$MODE" = "--debug" ] && limit=2
if [ "$n" -le "$limit" ]; then ok "Activities exportadas: $n (máximo $limit) ${acts}"; else bad "demasiadas Activities exportadas: $n ($acts)"; fi
for kind in receiver provider service; do
  list="$(get "$kind")"; c="$(count "$list")"
  if [ "$c" -eq 0 ]; then ok "sin $kind exportados"; else bad "$kind exportados: $list"; fi
done

exit "$fail"
