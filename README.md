# Lector

Lector de documentos y escáner a PDF para Android. **Sin anuncios, sin cuentas y sin conexión a internet**: la app no declara
el permiso `INTERNET` (ni ningún otro), así que ningún documento puede salir del teléfono.

> Código escrito siguiendo [`docs/PLAN.md`](docs/PLAN.md) y [`docs/DESIGN.md`](docs/DESIGN.md). Las desviaciones y lo que no se
> pudo verificar están en [`docs/DECISIONS.md`](docs/DECISIONS.md): léelo antes de confiar en la app.

## Qué hace

| Función | Formatos | Estado |
|---|---|---|
| Leer PDF | `.pdf` | Motor nativo de Android (`PdfRenderer`): zoom, saltar de página, compartir, guardar copia, imprimir, modo noche. Búsqueda y contraseñas en Android 15+. |
| Hojas de cálculo | `.xlsx` `.xls` `.ods` `.csv` | Tabla con cabeceras fijas y pestañas de hoja (SheetJS en un WebView aislado). |
| Word | `.docx` | **Vista aproximada** (docx-preview). |
| PowerPoint | `.pptx` `.ppsx` | **Vista aproximada** (diapositivas estáticas, sin animaciones ni vídeo). No tiene la fidelidad de PowerPoint. |
| Texto | `.txt` `.md` `.log` | Lector con tamaño ajustable. |
| Escanear a PDF | cámara / galería | ML Kit Document Scanner (Google Play Services) y, sin él, un modo simple propio. Guarda en `Documentos/Lector`. |
| No disponibles | `.ppt` `.pps` `.odp` `.doc` `.odt` `.rtf` | Muestra «No compatible» y ofrece **Abrir con otra app**. |

**Límites que conviene conocer** (no son errores): las presentaciones se ven aproximadas porque el motor de alta fidelidad
(LibreOffice embebido, PLAN §7) no se pudo integrar en el entorno donde se escribió el código; ver `docs/DECISIONS.md` §3.
No edita, anota ni firma documentos; no reproduce animaciones, vídeo ni audio; no muestra notas del orador.

## Privacidad y seguridad

- Sin permisos: ni internet, ni almacenamiento, ni cámara (el escáner de Google y la cámara del sistema no los exigen).
- Todo archivo se trata como no confiable: se copia a la caché, se identifica por sus bytes (no por la extensión), y los ZIP
  (Word/Excel/PowerPoint) se inspeccionan contando los bytes reales antes de abrirlos. Los documentos de ofimática se muestran
  en un WebView sin red, con una política de seguridad estricta (CSP) y un canal de mensajes cerrado.
- Los recientes guardan solo la URI, el nombre, el tipo, el tamaño y la página; hay un ajuste para borrarlos y otro para ocultar
  el contenido en la vista de apps recientes.
- Detalle y evidencia punto por punto: [`docs/SECURITY_REVIEW.md`](docs/SECURITY_REVIEW.md).

## Descargar e instalar

Cada push a GitHub construye los APK en *Actions* (flujo **CI**, artefacto `lector-apk`):

- `lector-release-unsigned-debugkey.apk`: release (R8, sin depuración) firmado con la clave de depuración, instalable directamente.
- `lector-debug.apk`: depuración.

Al empujar una etiqueta `v*` se publica una *GitHub Release* con el APK firmado con tu keystore ([`docs/SIGNING.md`](docs/SIGNING.md)).
En el teléfono hay que permitir «instalar apps de origen desconocido» para el navegador o gestor de archivos que uses.
Después, sigue la lista de [`docs/QA.md`](docs/QA.md).

## Compilar

Requisitos: JDK 17+, Android SDK con la plataforma `android-37.0` y `build-tools;37.0.0` (Gradle los descarga si faltan).

```bash
./gradlew lint testDebugUnitTest assembleDebug     # lo que se ejecuta antes de cada commit
./gradlew assembleRelease                          # APK de release
scripts/check-apk.sh app/build/outputs/apk/release/app-release.apk
./gradlew connectedDebugAndroidTest                # con emulador o dispositivo
node scripts/check-viewer.js                       # visor web en Chromium (requiere `playwright`)
```

- `applicationId = dev.lector.app`, `minSdk 29` (Android 10), `compileSdk/targetSdk 37`. Kotlin + Jetpack Compose + Material 3 con
  tema propio completo; un único módulo `:app`, inyección de dependencias manual.
- Versiones fijadas en `gradle/libs.versions.toml` y verificadas con `gradle/verification-metadata.xml`.
- `scripts/make-fixtures.py` genera los archivos de prueba; `scripts/make-icons.py` convierte los iconos Phosphor a `VectorDrawable`.

## Estructura

```
app/src/main/java/dev/lector/app
├── core/      Outcome/DocError, dispatchers, Log, CrashRecorder
├── domain/    modelos, interfaces de repositorio, casos de uso puros
├── data/      intake (UriIntake, MagicSniffer, ZipInspector), pdf (FrameworkPdfEngine), office (WebView seguro),
│              scan (ML Kit, JpegPdfWriter, MediaStore), recents (Room), settings (DataStore), text, share
├── di/        AppContainer
└── ui/        theme, common (componentes de DESIGN.md), home, viewer/{pdf,web,text}, scan, settings, nav
app/src/main/assets/viewer/   página anfitriona del visor web + bibliotecas JS vendorizadas (ver THIRD_PARTY.md)
docs/          PLAN, DESIGN, DECISIONS, SECURITY_REVIEW, QA, SIGNING
```

## Licencias

El código de Lector no declara licencia todavía. Los componentes de terceros (Geist, Phosphor, SheetJS, JSZip, docx-preview,
pptx-renderer, AndroidX, ML Kit…) y sus licencias están en [`THIRD_PARTY.md`](THIRD_PARTY.md) y `licenses/`.
