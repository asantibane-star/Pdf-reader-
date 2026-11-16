#!/usr/bin/env bash
# Fija por SHA de commit las acciones de terceros de .github/workflows (PLAN §12).
# Requiere red y `git`. Cada `uses: dueño/repo[/ruta]@etiqueta` pasa a `@<sha> # etiqueta`.
# Uso: scripts/pin-actions.sh        (reescribe los archivos en su sitio)
set -euo pipefail
cd "$(dirname "$0")/.."

grep -rhoE 'uses: *[A-Za-z0-9._-]+/[A-Za-z0-9._/-]+@v[0-9][A-Za-z0-9._-]*' .github/workflows | sed -E 's/uses: *//' | sort -u | while read -r ref; do
  repo="${ref%@*}"; tag="${ref#*@}"
  slug="$(echo "$repo" | cut -d/ -f1-2)"
  sha="$(git ls-remote "https://github.com/${slug}.git" "refs/tags/${tag}" "refs/tags/${tag}^{}" | tail -1 | cut -f1)"
  [ -n "$sha" ] || { echo "No se pudo resolver $ref" >&2; exit 1; }
  echo "$ref -> $sha"
  sed -i -E "s#uses: *${repo//\//\\/}@${tag}\$#uses: ${repo}@${sha} \# ${tag}#" .github/workflows/*.yml
done
