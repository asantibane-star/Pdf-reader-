# Componentes de terceros

Este archivo registra la versión, el origen, la licencia y el SHA-256 de cada archivo de terceros incluido en el repositorio.
Una prueba unitaria (`ThirdPartyIntegrityTest`) recalcula los SHA-256 de esta tabla y falla si algún archivo cambió.

## Archivos incluidos

| Archivo | Componente | Versión | Origen | Licencia | SHA-256 |
|---|---|---|---|---|---|
| `app/src/main/assets/viewer/vendor/xlsx.full.min.js` | SheetJS Community Edition | 0.20.3 | npm `@e965/xlsx@0.20.3` (republicación automática de la versión oficial; no se pudo contrastar con cdn.sheetjs.com, bloqueado en el entorno de construcción) | Apache-2.0 (`licenses/SheetJS-Apache-2.0.txt`) | `cc015130aa8521e7f088f88898eba949ccdcbfb38df0bd129b44b7273c3a6f41` |
| `app/src/main/assets/viewer/vendor/jszip.min.js` | JSZip | 3.10.2 | npm `jszip@3.10.2` | MIT o GPL-3.0 (se usa bajo MIT) (`licenses/JSZip-MIT-or-GPL3.txt`) | `7f839b2d4688b845c105ebf5d2f9803075f91ea0fe72bdaac176c3a04dd3d2c1` |
| `app/src/main/assets/viewer/vendor/docx-preview.min.js` | docx-preview | 0.4.1 | npm `docx-preview@0.4.1` | Apache-2.0 (`licenses/docx-preview-Apache-2.0.txt`) | `c4a133c65a112799e35b143c572dfff9e923a3bf39b5fd3b07a06aa0f8f530c2` |
| `app/src/main/assets/viewer/vendor/pptx-renderer.browser.es.js` | @aiden0z/pptx-renderer (incluye JSZip y ECharts) | 1.4.0 | npm `@aiden0z/pptx-renderer@1.4.0`, artefacto `dist/aiden0z-pptx-renderer.browser.es.js` | Apache-2.0 (`licenses/pptx-renderer-Apache-2.0.txt`) | `eed96db79a8300ef66a57e8a5f4f2c06a5cb78ac30b7eecd5df921c05ac5069b` |
| `app/src/main/res/font/geist_regular.ttf` | Geist Regular | 1.9.2 | npm `geist@1.9.2` | SIL OFL 1.1 (`licenses/Geist-OFL.txt`) | `2c460a7a71063e53491f343d44b382267bc0cf496e31db64865f962af3152e68` |
| `app/src/main/res/font/geist_medium.ttf` | Geist Medium | 1.9.2 | npm `geist@1.9.2` | SIL OFL 1.1 (`licenses/Geist-OFL.txt`) | `e9099e006d4934cae4a8c7d14a1ca2ea724ee2f69f9bdd5b2ffe715baf5524fc` |
| `app/src/main/res/font/geist_semibold.ttf` | Geist SemiBold | 1.9.2 | npm `geist@1.9.2` | SIL OFL 1.1 (`licenses/Geist-OFL.txt`) | `01923aa148e5202ae70a2e11b2ab4dc41b80bcc374aedccc913d47bb2f591c06` |
| `app/src/main/res/font/geist_mono_medium.ttf` | Geist Mono Medium | 1.9.2 | npm `geist@1.9.2` | SIL OFL 1.1 (`licenses/Geist-OFL.txt`) | `34c0828460d47e697655bc9d730027a4b8baf6e6c9aa46f5f32b653b4797cf36` |

Iconos: Phosphor Icons, variante Light, MIT (`licenses/Phosphor-MIT.txt`), paquete npm `@phosphor-icons/core`. Los SVG originales están en `docs/design/icons/` y se convierten a `VectorDrawable` con `scripts/make-icons.py`.

## Dependencias de Gradle

Las versiones exactas de las bibliotecas AndroidX, Kotlin, kotlinx.coroutines, Room, DataStore y ML Kit Document Scanner están fijadas en `gradle/libs.versions.toml` (Apache-2.0; ML Kit bajo los términos de Google ML Kit).

## LibreOffice

No se incluye (ver `docs/DECISIONS.md`, decisión 3). Si se integra más adelante se registrarán aquí su versión, URL de origen, SHA-256 y licencia (MPL-2.0).
