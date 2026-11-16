# Decisiones y desviaciones del plan

Cada entrada dice qué se decidió, por qué y qué consecuencia tiene. Las decisiones **[FIJO]** de `docs/PLAN.md`
no se han cambiado; aquí solo se registran los cambios en los detalles que el propio plan permite ajustar.

## 1. Versiones (PLAN §2)

Resueltas el 2026-11-16 desde Google Maven y Maven Central (solo estables, nunca alpha/beta/rc) y fijadas en
`gradle/libs.versions.toml`:

| Pieza | Versión |
|---|---|
| Gradle (wrapper) | 9.9.1 |
| Android Gradle Plugin | 9.5.1 (Kotlin integrado en AGP) |
| Kotlin / plugin Compose | 2.4.21 |
| KSP | 2.3.14 |
| Compose BOM | 2026.09.00 |
| Activity Compose / Navigation Compose | 1.13.0 / 2.10.2 |
| Lifecycle | 2.11.0 |
| Room / DataStore | 2.8.5 / 1.3.0 |
| kotlinx.coroutines | 1.11.0 |
| androidx.webkit / exifinterface | 1.18.0 / 1.4.2 |
| ML Kit Document Scanner | 16.0.0 (única versión publicada) |
| Robolectric / Turbine | 4.18 / 1.2.1 |

`compileSdk = targetSdk = 37` (Android 17, estable). `minSdk = 29` **[FIJO]**.

El plan pide JDK 17. En este entorno de construcción hay JDK 21; el código se compila con `jvmTarget = 17` y `-release`
de 17, así que el resultado es el mismo. La CI usa JDK 17.

## 2. Entorno de construcción

El contenedor donde se escribió el código bloquea por defecto `dl.google.com` (SDK de Android y Google Maven) y
`gcp.mirrors.maven.org` (espejo al que Maven Central redirige los archivos grandes). Se pidió y se aprobó añadirlos al
entorno; no se usó ninguna vía alternativa. El SDK (plataforma 37, build-tools 37.0.0) se instaló con `sdkmanager`.

No hay KVM en ese contenedor, así que **no se pudo ejecutar un emulador**: las pruebas instrumentadas (§11.3) están
escritas y compilan, pero se ejecutan en la CI de GitHub (`instrumented.yml`), no se ejecutaron localmente.

## 3. Spike de LibreOffice (PLAN §7.2, Fase 5) — **NO cumplido: se adopta el respaldo de §7.6**

El plan dice: «Si no se cumple: no improvisar. Anotar qué falló, implementar el respaldo de §7.6 y avisar al usuario».

| Paso / criterio | Estado | Detalle |
|---|---|---|
| 1. Obtener `liblo-native-code.so` y assets (arm64-v8a, x86_64) | **Falló** | `download.documentfoundation.org` y F-Droid están bloqueados en el entorno. En Maven Central, `org.libreoffice:*` solo contiene las APIs UNO de escritorio (`juh`, `jurt`, `ridl`, `unoil`…), sin binarios Android. No hay ningún origen accesible que sirva una compilación de LibreOfficeKit para Android con hash verificable. |
| 2. `scripts/fetch-lokit.sh` con URL y SHA-256 fijados | No aplicable | Sin origen no hay URL ni hash que fijar. |
| 3. Prototipo de conversión en emulador de CI | No aplicable | Sin binarios ni emulador. |
| Cada fixture `.pptx`/`.ppt` produce un PDF con N páginas | No medido | — |
| SSIM ≥ 0,90 frente a la referencia | No medido | — |
| 30 diapositivas en < 25 s, arranque en frío < 8 s | No medido | — |
| Matar `:converter` no cierra la app | No aplicable | `ConverterService` no existe todavía. |
| APK arm64 de release ≤ 250 MB | No medido | El APK actual pesa unos 40 MB. |

**Consecuencias para el usuario (hay que comunicarlas):**

- `.pptx` / `.ppsx` se muestran con un renderizador JS (`@aiden0z/pptx-renderer`) como **«Vista aproximada»**: no es la
  fidelidad de LibreOffice. En los fixtures reproduce bien textos, tablas, degradados y gráficos, pero las fuentes que no
  están en el teléfono (Calibri, Cambria) se sustituyen por las del sistema.
- `.docx` se muestra con `docx-preview` también como «Vista aproximada».
- **No disponibles**: `.ppt`, `.pps`, `.odp`, `.doc`, `.odt`, `.rtf`. Se muestra «No compatible en este dispositivo» y el
  botón **Abrir con otra app**.
- No se escribió `ConverterService` ni `OfficeConverter` ni el modo presentación del visor PDF (§6.3): sin motor de
  conversión serían código muerto sin posibilidad de probarlo. La integración del motor A queda abierta; el punto de
  enganche es `Routes.viewerFor` y `WebViewerViewModel` (los formatos que hoy caen en «No compatible» pasarían a
  convertirse a PDF y abrirse en `PdfViewerScreen`).
- Los ajustes «Vaciar caché de conversiones» (DESIGN §6.7) no existen porque no hay caché de conversiones.

Para retomar el spike: conseguir una compilación de LibreOfficeKit para Android (p. ej. compilarla desde
`android/Bootstrap` del repositorio `core` de LibreOffice, o permitir su host de descarga en el entorno), colocarla en
`app/src/main/jniLibs/` y `assets/lokit/` (ya están en `.gitignore`) y seguir §7.2–7.4.

## 4. Elección del motor de respaldo de presentaciones (PLAN §7.6)

Se evaluaron en Chromium con la CSP del WebView, sin red, contra los fixtures y comparando con la diapositiva exportada
por LibreOffice de escritorio:

| Biblioteca | Licencia | Resultado |
|---|---|---|
| `@aiden0z/pptx-renderer` 1.4.0 | Apache-2.0 | **Adoptada.** Texto, tablas, degradados y **gráficos** correctos; SmartArt, límites de ZIP y `externalMedia: 'block'`; sin `eval` en el camino normal. |
| `pptx-preview` 1.0.7 | ISC | Descartada: el gráfico del fixture sale vacío y el texto cae a una fuente serif. |
| `pptxviewjs` 1.1.9 | MIT | No se evaluó a fondo (su artefacto contiene una llamada `fetch`); se dejó de lado al comprobar que `@aiden0z/pptx-renderer` cumplía todos los criterios. |

`@aiden0z/pptx-renderer` lanza un `EvalError` no fatal bajo la CSP con el fixture `external-link.pptx` (un `new Function`
interno); la presentación se renderiza igual y `viewer.js` solo falla si el render no termina.

## 5. SheetJS desde un espejo de npm

La última versión de SheetJS en npm oficial (`xlsx@0.18.5`) tiene vulnerabilidades conocidas (CVE-2023-30533,
CVE-2024-22363). Las correcciones solo se publican en `cdn.sheetjs.com`, que está bloqueado en el entorno. Se vendoriza
`@e965/xlsx@0.20.3` (republicación automática de la versión oficial, repositorio `e965/sheetjs-npm-publisher`). **No se pudo
contrastar el archivo con el original**: queda anotado en `THIRD_PARTY.md`. El WebView está aislado (sin red, CSP, proceso
de render separado), lo que acota el impacto. Recomendación: verificar el SHA-256 contra `cdn.sheetjs.com` cuando haya red.

## 6. Cambios menores al modelo de datos y a la estructura

- `RecentEntity` añade `pageCount` (el plan no lo lista, pero «Página 12 de 48» de la tarjeta destacada lo necesita).
- Los fixtures viven en `app/src/test/resources/fixtures/` y se comparten con `androidTest` mediante
  `sourceSets.androidTest.assets` (una sola copia, 2,3 MB), en vez de duplicarlos en `androidTest/assets/fixtures/`.
- `smartart.pptx` **no se generó**: `python-pptx` no crea SmartArt y fabricar las partes de diagrama a mano no habría sido un
  archivo representativo de PowerPoint. Debe aportarlo quien tenga PowerPoint si se retoma el spike de LibreOffice.
- `legacy.doc`, `legacy.xls` y `legacy.ppt` se generaron con LibreOffice de escritorio (`soffice --convert-to`), no con
  Microsoft Office. Sus contenedores OLE2 son válidos (`MagicSniffer` los distingue por nombres de flujo).
- Navegación: rutas con cadena y argumentos (`Routes`), sin `kotlinx.serialization` (no está en §2).
- `FileNameSanitizer.displayTitle` quita la extensión del nombre que se muestra.
- Las miniaturas usan el id de la fila de Room (`thumbs/<id>.jpg`), como pide DESIGN §4.2.
- `AppServices` (interfaz) separa lo que necesitan los ViewModels de `AppContainer`, para poder probarlos con falsos.

## 7. Búsqueda y contraseña de PDF

Solo en Android 15+ (API ≥ 35), con las firmas verificadas en `android.jar` (`PdfRenderer(pfd, LoadParams)`,
`Page.searchText`). En Android 10–14 un PDF con contraseña muestra «Este PDF tiene contraseña y esta versión de Android no
permite abrirlo aquí» y el botón de búsqueda no aparece. La Fase 9 (`PdfRendererPreV`) no se implementó (opcional).

## 8. Permisos

El manifiesto elimina `INTERNET`, `ACCESS_NETWORK_STATE` y el permiso
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` que AndroidX añade; el APK de release no declara ningún permiso
(`scripts/check-apk.sh`).
