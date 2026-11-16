# Lector — Plan de construcción

Lector de documentos para Android, sin anuncios, sin conexión a internet, con escáner a PDF.
Este documento es la especificación completa. Quien lo ejecute debe seguirlo en orden de fases
y no cambiar una decisión marcada **[FIJO]** sin consultarlo con el usuario.

---

## 1. Alcance

### 1.1 Qué hace (v1)

| Función | Formatos | Motor |
|---|---|---|
| Leer PDF | `.pdf` | `android.graphics.pdf.PdfRenderer` (nativo) |
| Leer PowerPoint **con fidelidad** | `.pptx`, `.ppt`, `.ppsx`, `.pps`, `.odp` | LibreOffice embebido → PDF → visor de PDF (§7) |
| Leer Word | `.docx`, `.doc`, `.odt`, `.rtf` | LibreOffice embebido → PDF → visor de PDF (§7) |
| Leer Excel | `.xlsx`, `.xls`, `.ods`, `.csv` | SheetJS (JS) en WebView aislado |
| Leer texto | `.txt`, `.md`, `.log` | Compose nativo |
| Escanear a PDF | cámara / galería | ML Kit Document Scanner + respaldo propio |
| Recientes | — | Room |
| Compartir / Guardar copia / Imprimir (solo PDF) | — | FileProvider / SAF / PrintManager |

### 1.2 Qué NO hace (v1) — no implementar

- Reproducir animaciones, transiciones, vídeo o audio de presentaciones: las diapositivas se ven estáticas.
- Editar, anotar, firmar o rellenar formularios.
- Explorar todos los archivos del teléfono (exigiría `MANAGE_EXTERNAL_STORAGE`; se descarta por seguridad).
- Nube, cuentas, sincronización, analítica, reporte remoto de errores.
- Selección/copia de texto en PDF; enlaces dentro de PDF.

### 1.3 Decisiones fijas

- **[FIJO]** La app **no declara el permiso `INTERNET`**. Es la garantía técnica de "sin anuncios" y de que
  ningún documento sale del dispositivo. La CI falla si aparece en el manifiesto fusionado.
- **[FIJO]** Cero permisos peligrosos. Ni almacenamiento ni cámara (el escáner de ML Kit y la cámara del
  sistema no exigen permiso a la app).
- **[FIJO]** Un único módulo Gradle `:app`, Kotlin, Jetpack Compose, Material 3.
- **[FIJO]** `minSdk = 29` (Android 10). `compileSdk`/`targetSdk` = el API estable más reciente instalado (≥ 36).
- **[FIJO]** DI manual (`AppContainer`), sin Hilt/Koin. Menos piezas de build que puedan romperse.
- **[FIJO]** Todo archivo de entrada es **no confiable**: se valida por bytes mágicos, tamaño y estructura
  antes de entregarlo a un motor.
- `applicationId = dev.lector.app`, nombre visible **Lector**. Idioma por defecto español (`values/`),
  inglés en `values-en/`. Identificadores de código en inglés.

---

## 2. Stack y versiones

No se fijan números aquí a propósito: al ejecutar la Fase 0, resolver la **última versión estable**
(nunca alpha/beta/rc) de cada pieza desde Google Maven / Maven Central, comprobar la tabla de
compatibilidad AGP↔Gradle↔Kotlin↔KSP, y fijarlas en `gradle/libs.versions.toml`.

| Pieza | Artefacto |
|---|---|
| Build | Gradle Wrapper, AGP, JDK 17, Kotlin 2.x + plugin `org.jetbrains.kotlin.plugin.compose`, KSP |
| UI | Compose BOM, `material3`, `activity-compose`, `navigation-compose`, `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` |
| Datos | `room-runtime`, `room-ktx`, `room-compiler` (KSP), `datastore-preferences` |
| Concurrencia | `kotlinx-coroutines-android` |
| WebView | `androidx.webkit:webkit` |
| Escáner | `com.google.android.gms:play-services-mlkit-document-scanner` |
| Fotos | `androidx.exifinterface:exifinterface` |
| Tests | JUnit4, `kotlinx-coroutines-test`, Turbine, Robolectric, `androidx.test` (core, runner, ext-junit), `compose-ui-test-junit4`, Espresso-web |

Motor de ofimática: LibreOfficeKit (binarios nativos descargados por `scripts/fetch-lokit.sh` con hash fijado, ver §7.2).
Fuentes de interfaz Geist y Geist Mono (OFL) e iconos Phosphor Light (MIT), ambos incluidos en `res/`.

Librerías JS **vendorizadas** en `app/src/main/assets/viewer/vendor/` (jamás por CDN):
`xlsx` (SheetJS CE); `jszip` y `docx-preview` solo si se activa el respaldo de §7.6. Registrar versión, URL de origen, licencia y SHA-256 de
cada archivo en `THIRD_PARTY.md`. Un test unitario recalcula los SHA-256 y falla si no coinciden.

No añadir ninguna otra dependencia sin justificarla en `docs/DECISIONS.md`.

---

## 3. Arquitectura

### 3.1 Capas

```
ui (Compose + ViewModel)  →  domain (casos de uso, modelos, interfaces)  ←  data (implementaciones)
```

- `ui` nunca toca `ContentResolver`, `File`, `PdfRenderer`, Room ni WebView-bytes directamente.
- `domain` es Kotlin puro (sin `android.*` salvo `Uri` encapsulado como `String`). Testeable en JVM.
- Flujo unidireccional: `ViewModel` expone `StateFlow<UiState>` inmutable y recibe eventos por funciones.
  Efectos de un solo uso (snackbar, navegar, lanzar intent) por `Channel` → `receiveAsFlow()`.

### 3.2 Estructura de paquetes

```
dev.lector.app
├── LectorApp.kt                 Application: AppContainer, StrictMode (debug), CrashRecorder, limpieza de caché
├── MainActivity.kt              Única Activity. singleTop. Recibe VIEW/SEND. Edge-to-edge.
├── di/AppContainer.kt
├── core/
│   ├── result/Outcome.kt        sealed: Success<T> | Failure(DocError)
│   ├── result/DocError.kt       enum/sealed de errores tipados (§8.2)
│   ├── dispatch/Dispatchers.kt  interfaz AppDispatchers (io, default, main, pdf)
│   ├── log/Log.kt               envoltorio; no-op en release; nunca registra nombres/URIs/contenido
│   └── crash/CrashRecorder.kt   guarda el último stacktrace en filesDir/crash/last.txt
├── domain/
│   ├── model/                   DocumentRef, DocumentKind, RecentDocument, ScanResult, PageSize
│   ├── repo/                    DocumentRepository, RecentsRepository, ScanRepository, SettingsRepository
│   └── usecase/                 OpenDocument, RecordRecent, SaveScanAsPdf, BuildPdfFromImages, ShareDocument…
├── data/
│   ├── intake/                  UriIntake (copia a caché), MagicSniffer, ZipInspector, FileNameSanitizer
│   ├── pdf/                     PdfEngine, FrameworkPdfEngine, PdfBitmapCache, JpegPdfWriter
│   ├── office/                  ConverterService (:converter), OfficeConverter, ConversionCache, SecureWebViewFactory, ViewerRequestInterceptor
│   ├── text/                    TextLoader
│   ├── scan/                    MlKitScanner, ImageImport, MediaStoreSaver
│   ├── recents/                 Room: RecentEntity, RecentDao, LectorDatabase
│   └── settings/                DataStore
└── ui/
    ├── theme/                   Material 3, color dinámico, claro/oscuro
    ├── nav/                     NavHost, rutas tipadas
    ├── home/                    HomeScreen, HomeViewModel
    ├── viewer/pdf/              PdfViewerScreen, PdfViewerViewModel, ZoomableLazyPages, PageSlider, SearchBar
    ├── viewer/office/           OfficeViewerScreen, OfficeViewerViewModel
    ├── viewer/text/             TextViewerScreen
    ├── scan/                    ScanCoordinator (launchers), NameScanDialog, FallbackScanScreen
    ├── settings/                SettingsScreen, AboutScreen, LicensesScreen
    └── common/                  ErrorPane, LoadingPane, ConfirmDialog
```

### 3.3 Flujo de apertura (único camino para todos los orígenes)

```
Intent VIEW / SEND / selector SAF / recientes
        │  Uri (solo esquema content://)
        ▼
UriIntake.copyToCache(uri)  ──►  cacheDir/open/<uuid>.bin      (límite de tamaño, espacio libre, cancelable)
        ▼
MagicSniffer.detect(file)   ──►  DocumentKind                  (por bytes, no por extensión ni MIME)
        ▼
ZipInspector.validate(file)      (solo OOXML/ODS: nº entradas, tamaño real descomprimido, rutas)
        ▼
DocumentRef(id, cacheFile, kind, displayName, sourceUri)
        ▼
Navegación a PdfViewer | OfficeViewer | TextViewer | pantalla "no compatible"
```

**Siempre se trabaja sobre la copia en caché**, nunca sobre el `Uri` original. Razones: `PdfRenderer` exige
descriptor *seekable* (los proveedores en la nube entregan pipes), el proveedor puede revocar acceso a
mitad de lectura, y se elimina toda una clase de errores intermitentes.

---

## 4. Seguridad (requisitos verificables)

### 4.1 Manifiesto

```xml
<!-- Sin <uses-permission>. Además, blindaje contra permisos que arrastren las dependencias: -->
<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" tools:node="remove" />

<application
    android:allowBackup="false"
    android:dataExtractionRules="@xml/data_extraction_rules"   <!-- excluye todo: cloud-backup y device-transfer -->
    android:fullBackupContent="false"
    android:usesCleartextTraffic="false"
    android:enableOnBackInvokedCallback="true"
    android:supportsRtl="true" ...>
```

- Único componente exportado: `MainActivity` (`LAUNCHER` + `VIEW`/`SEND` con `content` y los MIME de §1.1).
  Sin `android:scheme="file"`.
- `FileProvider`: `exported="false"`, `grantUriPermissions="true"`, rutas **solo** `cache/share/` y `cache/camera/`.
- Único `Service`: `ConverterService`, no exportado, en proceso `:converter` (§7.3). Sin `BroadcastReceiver` ni `ContentProvider` exportados. Sin `PendingIntent`.
- Tras añadir ML Kit, revisar el manifiesto fusionado y eliminar con `tools:node="remove"` cualquier permiso
  añadido. Si ML Kit registra un error de telemetría por no tener red, es esperado e inocuo.

### 4.2 Ingesta de archivos

| Control | Regla |
|---|---|
| Esquema | Solo `content://`. Cualquier otro → `DocError.UnsupportedSource`. |
| Tamaño | PDF ≤ 500 MB; ofimática ≤ 50 MB; texto ≤ 5 MB. Contar bytes **mientras se copia** (no fiarse de `OpenableColumns.SIZE`) y abortar al superar. |
| Espacio | Exigir `cacheDir.usableSpace ≥ tamaño + 50 MB`; si no, `DocError.NoSpace`. |
| Tipo | `MagicSniffer`: `%PDF-` en los primeros 1024 bytes → PDF; `PK\x03\x04` → ZIP y se decide por entradas (`word/document.xml`, `xl/workbook.xml`, `ppt/presentation.xml`, `mimetype` de ODS); `D0 CF 11 E0` → OLE2: se distingue `.doc`/`.ppt`/`.xls` por los nombres de flujo del contenedor (`WordDocument`, `PowerPoint Document`, `Workbook`/`Book`), leyendo solo el directorio; `{\rtf` → RTF; resto → texto solo si decodifica como UTF-8/Latin-1 sin bytes NUL. |
| ZIP | `ZipInspector` con `ZipInputStream`: ≤ 5 000 entradas; suma **real** descomprimida ≤ 300 MB; ninguna entrada > 100 MB; rechazar nombres con `..`, absolutos o `\`. Nunca se extrae a disco. |
| Nombres | `displayName` solo se muestra. Los archivos propios se nombran con UUID. `FileNameSanitizer` para nombres que escribe el usuario: quitar `/ \ : * ? " < > |`, caracteres de control y puntos iniciales; máx. 100 caracteres; vacío → `Escaneo AAAA-MM-DD HHmm`. |
| Limpieza | (`cacheDir/converted/` se conserva, ver §7.3.) Borrar `cacheDir/open/`, `cache/share/`, `cache/camera/` al iniciar el proceso (salvo el archivo de la sesión restaurada) y borrar el archivo al cerrar el visor. |

### 4.3 WebView endurecido (`SecureWebViewFactory`) — único punto de creación de WebView

```kotlin
settings.javaScriptEnabled = true              // solo ejecuta assets propios; ver CSP
settings.allowFileAccess = false
settings.allowContentAccess = false
settings.blockNetworkLoads = true
settings.domStorageEnabled = false
settings.databaseEnabled = false
settings.setGeolocationEnabled(false)
settings.javaScriptCanOpenWindowsAutomatically = false
settings.setSupportMultipleWindows(false)
settings.mixedContentMode = MIXED_CONTENT_NEVER_ALLOW
settings.cacheMode = LOAD_NO_CACHE
settings.builtInZoomControls = true; settings.displayZoomControls = false
WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
```

- **Prohibido `addJavascriptInterface`.**
- Carga única: `https://appassets.androidplatform.net/viewer/index.html` vía `WebViewAssetLoader`.
- `shouldInterceptRequest`: responde **solo** (a) assets bajo `/viewer/` y (b) `/doc/<nonce>` → stream del archivo
  en caché de la sesión actual (nonce aleatorio de 128 bits, un solo uso de sesión). Todo lo demás → respuesta 403 vacía.
- `shouldOverrideUrlLoading`: devuelve `true` siempre. Si es `http(s)` y fue un gesto del usuario, mostrar diálogo
  con la URL completa y botón "Abrir en navegador" (`ACTION_VIEW`); cualquier otro esquema se ignora.
- CSP en `index.html` (meta) **y** como cabecera en la respuesta interceptada:
  `default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src blob: data:; font-src blob: data:; connect-src 'self'; base-uri 'none'; form-action 'none'`
- Canal JS→Kotlin: `WebViewCompat.addWebMessageListener` restringido al origen `https://appassets.androidplatform.net`
  (comprobar `WebViewFeature.WEB_MESSAGE_LISTENER`; si no existe, usar `document.title` + `onReceivedTitle`).
  Solo se aceptan mensajes de una lista cerrada: `ready`, `rendered`, `error:<código>`, `sheets:<n>`. Longitud máx. 64; el resto se descarta.
- `onRenderProcessGone` → devolver `true`, destruir el WebView, mostrar `ErrorPane` con "Reintentar". Nunca dejar que mate la app.
- `onPermissionRequest` → `deny()`. `onShowFileChooser` → `false`. Descargas: sin `DownloadListener`.
- El análisis de DOCX/XLSX ocurre en el proceso renderer aislado de WebView: un archivo malicioso queda dentro del sandbox.

### 4.4 Build y cadena de suministro

- Release: `isMinifyEnabled = true`, `isShrinkResources = true`, `isDebuggable = false`. Reglas R8 mínimas.
- Firma: keystore **fuera del repo**; se inyecta por variables de entorno/secrets. `.gitignore` incluye `*.jks`, `*.keystore`, `keystore.properties`, `local.properties`.
- `gradle/verification-metadata.xml` con SHA-256 (`./gradlew --write-verification-metadata sha256`).
- `gradle/actions/wrapper-validation` en CI. Dependabot para Gradle y Actions.
- Lint con `abortOnError = true`, `warningsAsErrors = true` (lista de exclusiones justificada en `lint.xml`).
- Sin secretos, URLs ni claves en el código. Sin SDKs de analítica/anuncios/crash remoto.
- Script `scripts/check-apk.sh` (se ejecuta en CI sobre el APK final): falla si el APK declara **cualquier**
  `uses-permission`, si `debuggable=true` en release, o si hay más de una Activity exportada.

### 4.5 Privacidad

- Room guarda solo: URI, nombre visible, tipo, tamaño, fecha, última página. Opción "Borrar recientes" en Ajustes.
- `Log` no escribe nada en release. `CrashRecorder` guarda solo stacktrace (sin extras de intents ni rutas de usuario).
- Ajuste opcional "Ocultar contenido en apps recientes" → `FLAG_SECURE`.

---

## 5. Reglas de robustez (obligatorias en todo el código)

1. Prohibido `!!`, `lateinit` en ViewModels, `runBlocking` en producción, `GlobalScope`, y E/S en el hilo principal.
2. Toda función de `data` que haga E/S es `suspend`, cambia de dispatcher **dentro** y devuelve `Outcome<T>`.
   Captura `IOException`, `SecurityException`, `IllegalStateException`, `IllegalArgumentException`,
   `OutOfMemoryError` (solo alrededor de asignación de bitmaps) y **relanza `CancellationException`**.
3. Cada pantalla modela `Loading | Content | Error(DocError)`; `Error` siempre ofrece una salida (Reintentar / Volver / Abrir con otra app).
4. Muerte de proceso: los ViewModel de visores guardan en `SavedStateHandle` `sourceUri`, `displayName`, página y zoom.
   Al restaurar, si la copia en caché ya no existe, se reingesta desde `sourceUri`; si falla → `Error(AccessLost)`.
5. Cambios de configuración (rotar, modo oscuro, multi-ventana): no reabren el documento ni pierden la página.
6. `collectAsStateWithLifecycle` para todos los flujos. `rememberSaveable` para estado de UI.
7. Acciones que lanzan intents o navegación: protegidas contra doble toque (bandera `busy` en estado).
8. `startActivity` externos siempre dentro de `try/catch (ActivityNotFoundException)` → snackbar.
9. `MainActivity.onNewIntent` procesa el nuevo documento; un mismo intent no se procesa dos veces
   (marcar con un extra booleano propio y comprobar `savedInstanceState`).
10. Insets: `enableEdgeToEdge()`; todas las pantallas aplican `WindowInsets.safeDrawing`. Retroceso predictivo con `BackHandler`.
11. StrictMode en debug: `detectAll()` + `penaltyLog()` (hilo y VM).

---

## 6. Visor de PDF

### 6.1 Motor

```kotlin
interface PdfEngine { suspend fun open(file: File, password: String? = null): Outcome<PdfSession> }

interface PdfSession {
    val pageCount: Int
    val canSearch: Boolean
    suspend fun pageSize(index: Int): Outcome<PageSize>                    // puntos PDF
    suspend fun render(index: Int, widthPx: Int, heightPx: Int,
                       region: RectF? = null): Outcome<Bitmap>             // region = fracción [0..1] de la página
    fun search(query: String): Flow<SearchHit>                             // vacío si !canSearch
    suspend fun close()
}
```

Reglas de `FrameworkPdfEngine` (cada una evita un crash conocido de `PdfRenderer`):

- **Un solo hilo.** `AppDispatchers.pdf = Dispatchers.IO.limitedParallelism(1)`; todo acceso a `PdfRenderer`/`Page` pasa por él.
- **Una página abierta a la vez**: `openPage` → render → `close()` en `try/finally`.
- `close()` de la sesión: marcar `closed`, cancelar **y esperar** (`cancelAndJoin`) los renders, luego cerrar `PdfRenderer` y `ParcelFileDescriptor` en ese orden, en el dispatcher `pdf`. Tras `closed`, todo devuelve `Failure(Closed)`.
- `open`: `IOException` → `Corrupt`; `SecurityException` → `PasswordRequired`. `pageCount == 0` → `Corrupt`.
- Contraseña y búsqueda: en API ≥ 35 usar `PdfRenderer(pfd, LoadParams)` y las APIs de texto de `Page`
  (**verificar las firmas exactas en `android.jar` antes de escribir**; ir tras `Build.VERSION.SDK_INT >= 35`).
  En API < 35: `PasswordRequired` se muestra como "PDF protegido: no compatible con esta versión de Android"
  y `canSearch = false` (el botón de búsqueda no se muestra).
- Bitmap: `ARGB_8888`, fondo blanco (`eraseColor(WHITE)`) antes de `render`, `RENDER_MODE_FOR_DISPLAY`.
- Tope: `widthPx*heightPx*4 ≤ min(maxMemory/6, 64 MB)`; si se excede, reducir proporcionalmente. Dimensiones mínimas 1×1.
- `OutOfMemoryError` al crear el bitmap → vaciar caché, reintentar una vez a mitad de resolución, si no `Failure(OutOfMemory)`.

### 6.2 Caché

`PdfBitmapCache`: `LruCache<Key(page,width,regionHash), Bitmap>` con `sizeOf = allocationByteCount`,
capacidad `maxMemory/5`. No llamar `recycle()` manualmente (un bitmap reciclado que aún se dibuja crashea Compose);
dejarlo al GC. `onTrimMemory(≥ RUNNING_LOW)` → `evictAll()`.

### 6.3 UI

- `LazyColumn` vertical continuo, una página por ítem, `key = índice`. Altura de cada ítem por su relación de aspecto;
  hasta conocerla se usa la de la página 0. Los tamaños se cargan en segundo plano por bloques de 50.
- Render por ítem en `LaunchedEffect(page, anchoPx)` → se cancela solo al salir de pantalla. Prefetch ±1 página.
- **Zoom** (1×–5×): contenido con ancho `viewport × zoom` dentro de scroll horizontal + la lista vertical.
  Durante el pellizco se aplica `graphicsLayer` (barato); al soltar se consolida en `zoom`, se ajustan los
  desplazamientos para mantener el punto focal y, con *debounce* de 150 ms, se re-renderiza **solo la región visible**
  a resolución nativa como capa superpuesta (`region`). Doble toque alterna 1× ↔ 2,5×.
  Si la capa de alta resolución da problemas, se acepta quedarse con el bitmap base escalado (borroso pero estable).
- **Modo presentación** (`presentationMode = true`, documentos convertidos desde diapositivas): `HorizontalPager`, una
  diapositiva por página ajustada a la pantalla, tira de miniaturas inferior, rotar a horizontal ocupa toda la pantalla.
  Mismo motor, caché y reglas de zoom.
- Barra superior e inferior se ocultan con un toque. Indicador "12 / 340", deslizador de páginas (si > 3 páginas),
  diálogo "Ir a página" con validación numérica.
- Acciones: buscar (si `canSearch`), compartir, guardar copia, imprimir, modo noche (invertir con `ColorMatrix`), mantener pantalla encendida.
- Última página guardada en Room con *debounce* de 1 s y al salir.
- Imprimir: `PrintDocumentAdapter` que copia el archivo en caché; errores → `onWriteFailed`.

---

## 7. Visor de ofimática

Requisito del usuario: **las presentaciones se ven tal cual son** (solo lectura). Eso descarta los
renderizadores JS como motor principal: ninguno reproduce de forma fiable temas, SmartArt, gráficos,
degradados y fuentes, y ninguno abre `.ppt` binario.

### 7.1 Dos motores

| Motor | Formatos | Cómo se muestra |
|---|---|---|
| **A. LibreOffice embebido** (LibreOfficeKit, MPL-2.0) | `.pptx .ppt .ppsx .pps .odp .docx .doc .odt .rtf` | Se convierte a PDF en el dispositivo y se abre con el visor de PDF de §6 |
| **B. WebView aislado** (§4.3) | `.xlsx .xls .ods .csv` siempre. `.docx`/`.pptx` solo como respaldo si A no está disponible | HTML generado por JS vendorizado |

Por qué A: es el mismo motor de render que LibreOffice Impress/Writer, la mayor fidelidad disponible sin
conexión y con licencia libre; incluye fuentes métricamente compatibles con las de Microsoft (Carlito por
Calibri, Caladea por Cambria, Liberation por Arial/Times/Courier), así el texto no se descuadra. Y reutiliza
un único visor ya probado (zoom, búsqueda, compartir, imprimir).

**Límites que hay que comunicar en la app y en el README (no son bugs):** las diapositivas son estáticas
(sin animaciones, transiciones, vídeo ni audio); una fuente no incrustada y sin equivalente se sustituye;
no se muestran las notas del orador.

### 7.2 Spike obligatorio (Fase 5, antes de escribir el visor de ofimática)

El integrador no debe fiarse de este documento para los detalles de LibreOfficeKit: **leer las fuentes reales**
(`android/Bootstrap` y `android/README` del repositorio `core` de LibreOffice, y como referencia de cableado
el proyecto `qscr/libre_office_kit_converter_plugin`). Pasos:

1. Obtener la librería nativa (`liblo-native-code.so` y dependencias) y sus assets para `arm64-v8a` y
   `x86_64` (emulador de CI) desde una compilación oficial o reproducible de LibreOffice para Android.
   Versión estable más reciente. Las clases Java puente (`org.libreoffice.kit.*`) se copian de `core` conservando
   su cabecera de licencia.
2. Los binarios **no se suben a git** (superan el límite de GitHub). `scripts/fetch-lokit.sh` los descarga de
   una URL fijada, verifica SHA-256 y los deja en `app/src/main/jniLibs/` y `assets/` (ambos en `.gitignore`);
   la CI los cachea. Origen, versión, hash y licencia en `THIRD_PARTY.md`.
3. Prototipo mínimo: convertir cada presentación de prueba (§11.1) a PDF dentro del emulador de CI.

**Criterios de aceptación (todos):**
- [ ] Cada fixture `.pptx` y `.ppt` produce un PDF con tantas páginas como diapositivas.
- [ ] Comparación visual: primera diapositiva de cada fixture frente a su PNG de referencia (exportado desde
      PowerPoint/LibreOffice de escritorio y versionado), diferencia estructural aceptable (SSIM ≥ 0,90).
- [ ] 30 diapositivas en < 25 s en el emulador; arranque en frío del motor < 8 s.
- [ ] Funciona sin permiso `INTERNET` y `check-apk.sh` sigue pasando.
- [ ] Matar el proceso conversor a mitad de conversión no cierra la app.
- [ ] APK `arm64-v8a` de release ≤ 250 MB (anotar el tamaño real).

**Si no se cumple:** no improvisar. Anotar en `docs/DECISIONS.md` qué falló, implementar el respaldo de §7.6 y
avisar al usuario de que la fidelidad será parcial y de que `.ppt` no estará disponible.

### 7.3 Proceso conversor

LibreOffice es código nativo grande que analiza archivos no confiables: **nunca corre en el proceso de la UI.**

- `ConverterService`: `Service` enlazado, `android:exported="false"`, `android:process=":converter"`.
  Un fallo nativo mata solo ese proceso. La app no tiene permisos ni red, así que el proceso tampoco.
- Interfaz por `Messenger` (sin AIDL público): `CONVERT(inputPath, outputPath, filterHint)` →
  `PROGRESS` / `DONE(pageCount)` / `FAILED(code)`. El servicio valida que ambas rutas, ya canonicalizadas,
  estén dentro de `cacheDir/open/` y `cacheDir/converted/`; cualquier otra → `FAILED`.
- `OfficeConverter` (lado UI, `data/office/`): enlaza bajo demanda, **una conversión a la vez** (`Mutex`),
  tiempo máximo 120 s. Al vencer o cancelar: desenlazar y `Process.killProcess(pid del conversor)`.
  `onServiceDisconnected` / `onBindingDied` / `DeadObjectException` → `Failure(ConverterCrashed)` con "Reintentar"
  (un reintento automático como máximo).
- Inicialización de LibreOffice una vez por vida del proceso; el proceso se libera tras 60 s sin uso
  (desenlazar) para devolver memoria.
- Perfil de LibreOffice en `filesDir/lo_profile/`, creado por la app con configuración fija:
  seguridad de macros en el nivel máximo (no ejecutar nunca), sin actualización automática de vínculos
  externos, sin recuperación de documentos, sin diálogos. Comprobar con el fixture `macro.pptm` y
  `external-link.pptx` que no se ejecuta ni se resuelve nada.
- Exportación: filtro PDF de Impress/Writer, calidad de imagen JPEG 90, sin reducir resolución por debajo de 220 ppp,
  marcadores sí, notas no, diapositivas ocultas no.
- Caché de resultados: `cacheDir/converted/<sha256 del archivo de entrada>.pdf`. Reabrir el mismo archivo es
  instantáneo. Tope 300 MB con expulsión del menos usado; esta carpeta **no** se borra al iniciar (excepción a §4.2).
- Antes de convertir: espacio libre ≥ 3 × tamaño de entrada + 50 MB; si no, `NoSpace`.
- Si el dispositivo no tiene una ABI con librería incluida → `ConverterUnavailable` → respaldo §7.6.

### 7.4 Experiencia de apertura

`OfficeViewerViewModel`: `Converting(progreso indeterminado, nombre)` → `Ready(pdfFile)` → navega al visor de PDF
con `presentationMode = true` para presentaciones (§6.3). Pantalla de conversión según `docs/DESIGN.md` §6.4,
con botón **Cancelar**. Compartir y "Guardar copia" entregan el **archivo original**; se añade la acción
"Exportar como PDF" que entrega el convertido.

### 7.5 Hojas de cálculo y texto (motor B)

Página anfitriona `assets/viewer/` (`index.html` + `viewer.js` + `viewer.css` + `vendor/`), sin `eval`, sin scripts
en línea, sin recursos externos. `viewer.js` lee `?kind=…&nonce=…`, hace `fetch('/doc/<nonce>')`, renderiza y
notifica `rendered` o `error:<código>`; todo dentro de `try/catch` + `window.onerror` + `unhandledrejection`.
Tiempo máximo 30 s → `RenderTimeout`.

`XLSX.read(buffer, { type: 'array', dense: true, cellFormula: false, cellHTML: false, sheetRows: 2001 })`.
Pestañas de hojas abajo. Tabla construida con `textContent` (nunca `innerHTML` con datos del archivo).
Máx. 2 000 filas × 100 columnas por hoja con aviso. Búsqueda con `WebView.findAllAsync`.

Texto plano: `TextLoader` detecta BOM/UTF-8, respaldo ISO-8859-1; `LazyColumn` por líneas con `SelectionContainer`.

### 7.6 Respaldo si el motor A no está disponible

- `.docx`: `docx-preview` en el WebView (`renderAsync` con `breakPages: true`, `useBase64URL: false`).
- `.pptx`: evaluar `@aiden0z/pptx-renderer`, `pptx-preview` y `pptxviewjs`; adoptar la de mejor resultado sobre
  los fixtures solo si tiene licencia permisiva, funciona sin red bajo la CSP de §4.3 y no usa `eval`.
  Mostrar aviso permanente "Vista aproximada".
- `.ppt`, `.doc`, `.odt`, `.odp`, `.rtf`: pantalla "No compatible en este dispositivo" + **Abrir con otra app**.

---

## 8. Escáner

### 8.1 Camino principal: ML Kit Document Scanner

```kotlin
GmsDocumentScannerOptions.Builder()
    .setScannerMode(SCANNER_MODE_FULL)
    .setGalleryImportAllowed(true)
    .setPageLimit(50)
    .setResultFormats(RESULT_FORMAT_PDF)
    .build()
```

- Lanzar con `getStartScanIntent(activity)` → `rememberLauncherForActivityResult(StartIntentSenderForResult)`.
- `addOnFailureListener` → pasar al respaldo (§8.3) con snackbar "Escáner avanzado no disponible".
  Antes de lanzar, comprobar `GoogleApiAvailability.isGooglePlayServicesAvailable`.
- Resultado `RESULT_OK` → `GmsDocumentScanningResult.fromActivityResultIntent(data)?.pdf` → copiar **de inmediato**
  a `filesDir/pending_scan/<uuid>.pdf` (el archivo de ML Kit es temporal). `RESULT_CANCELED` → no hacer nada.
- El `uuid` pendiente se guarda en `SavedStateHandle`: si el proceso muere mientras el escáner está abierto, el flujo continúa.

### 8.2 Guardado

`NameScanDialog` (nombre saneado, prellenado `Escaneo AAAA-MM-DD HHmm`) → `MediaStoreSaver`:

```
insert(MediaStore.Files.getContentUri(VOLUME_EXTERNAL_PRIMARY),
       DISPLAY_NAME=<nombre>.pdf, MIME_TYPE=application/pdf,
       RELATIVE_PATH="Documents/Lector", IS_PENDING=1)
→ copiar bytes → update(IS_PENDING=0) → borrar pendiente → añadir a recientes → abrir en el visor
```

Si cualquier paso falla: `delete()` de la fila creada, conservar el pendiente y mostrar error con "Reintentar" y
"Guardar en otra ubicación…" (`ACTION_CREATE_DOCUMENT`). Al iniciar la app, si existe un pendiente huérfano,
ofrecer guardarlo o descartarlo.

### 8.3 Respaldo sin Google Play Services

`FallbackScanScreen`: añadir páginas con **Cámara** (`ActivityResultContracts.TakePicture` hacia un URI de
`FileProvider` en `cache/camera/`) o **Galería** (`PickMultipleVisualMedia`, máx. 50). Lista reordenable y
eliminable. Sin detección de bordes. Cada imagen: decodificar con `inSampleSize` hasta ≤ 2 480 px de lado mayor,
aplicar rotación EXIF, recomprimir JPEG calidad 85 → `JpegPdfWriter`.

### 8.4 `JpegPdfWriter` (PDF 1.4 mínimo, sin dependencias)

Escribe en streaming a un `OutputStream` contando offsets byte a byte:
cabecera `%PDF-1.4` + línea binaria; objetos `Catalog`, `Pages`; por página `Page` (MediaBox = A4 595×842 o
apaisado según la imagen), `Contents` (`q <w> 0 0 <h> <x> <y> cm /Im0 Do Q`, imagen centrada conservando aspecto)
y `XObject` imagen con `/Filter /DCTDecode /ColorSpace /DeviceRGB /BitsPerComponent 8 /Width /Height /Length`;
tabla `xref` con offsets de 10 dígitos y `trailer`. Solo acepta JPEG producidos por `Bitmap.compress` (garantiza 3 componentes).
`android.graphics.pdf.PdfDocument` **no** se usa: guarda los bitmaps sin pérdida y genera archivos enormes.

---

## 9. Datos

```kotlin
@Entity(tableName = "recent", indices = [Index("uri", unique = true)])
data class RecentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String, val displayName: String, val kind: String,
    val sizeBytes: Long, val lastOpenedAt: Long, val lastPage: Int = 0,
)
```

- Upsert por `uri`. Máximo 100 filas (purgar las más antiguas). `exportSchema = true`, esquemas en `app/schemas/`.
- Persistencia de acceso: tras `ACTION_OPEN_DOCUMENT`, `takePersistableUriPermission(READ)` en `try/catch`.
  Los URIs recibidos por `VIEW` suelen ser temporales: al reabrir desde recientes, si falla → diálogo
  "Ya no hay acceso a este archivo" con **Quitar de la lista** / **Buscar archivo…**.
- Ajustes (DataStore): tema (sistema/claro/oscuro), color dinámico, pantalla encendida, ocultar en recientes.
  Lectura con `catch { emit(emptyPreferences()) }`.

### Errores tipados → mensaje (`DocError`)

`UnsupportedSource`, `UnsupportedFormat`, `LegacyOfficeFormat`, `TooLarge`, `NoSpace`, `AccessLost`, `Corrupt`,
`PasswordRequired`, `WrongPassword`, `ZipBomb`, `RenderTimeout`, `RendererCrashed`, `WebViewUnavailable`,
`OutOfMemory`, `ConverterUnavailable`, `ConverterCrashed`, `ConversionTimeout`, `ScannerUnavailable`, `SaveFailed`, `Closed`, `Unknown`. Cada uno tiene su `@StringRes` en español
claro y sin tecnicismos. Crear el WebView dentro de `try/catch (Throwable)` → `WebViewUnavailable`.

---

## 10. Diseño de interfaz

La especificación visual y de interacción completa está en [`docs/DESIGN.md`](DESIGN.md) y es **obligatoria**:
tokens de color, tipografía, formas, movimiento, anatomía de cada pantalla y todos los textos.
Referencia visual: `docs/design/mockup.html` y `docs/design/mockup.png`.
No usar los colores, formas ni tipografía por defecto de Material 3; el tema se construye desde esos tokens.

---

## 11. Pruebas

### 11.1 Archivos de prueba (`app/src/androidTest/assets/fixtures/` y `app/src/test/resources/`)

Generarlos con un script reproducible en `scripts/make-fixtures.py` y versionar los resultados:
`simple.pdf` (3 pág.), `long.pdf` (500 pág.), `mixed-sizes.pdf`, `encrypted.pdf` (clave `test`),
`truncated.pdf`, `empty.pdf` (0 bytes), `fake.pdf` (un ZIP renombrado), `simple.docx`, `images.docx`,
`simple.xlsx` (3 hojas), `big.xlsx` (10 000 filas), `legacy.xls`,
presentaciones: `simple.pptx`, `theme-gradients.pptx`, `charts-tables.pptx`, `smartart.pptx`, `fonts-calibri.pptx`,
`legacy.ppt`, `macro.pptm`, `external-link.pptx`, `legacy.doc` real, cada una con su PNG de referencia de la diapositiva 1, `zipbomb.docx`
(entrada que descomprime > 300 MB), `traversal.docx` (entrada `../../x`),
`utf8.txt`, `latin1.txt`, `binary.bin`.

### 11.2 Unitarias (JVM / Robolectric) — obligatorias

`MagicSniffer` (todos los fixtures), `ZipInspector` (bomba, traversal, límite de entradas), `FileNameSanitizer`,
`JpegPdfWriter` (estructura: cabecera, nº de objetos, offsets de `xref` apuntan a `N 0 obj`, `%%EOF`),
`UriIntake` (límite de tamaño, cancelación borra el parcial), `TextLoader`, ViewModels (estados, restauración
desde `SavedStateHandle`, doble toque), `RecentsRepository` (upsert, purga a 100), integridad SHA-256 de `vendor/`.

### 11.3 Instrumentadas (emulador en CI)

`FrameworkPdfEngine`: abre/renderiza cada PDF válido; `truncated`/`empty`/`fake` → `Corrupt` sin excepción;
`encrypted` → `PasswordRequired`; 200 renders concurrentes + `close()` a mitad → sin crash;
PDF de `JpegPdfWriter` se abre y tiene N páginas. Conversor: criterios de §7.2; `zipbomb`/`truncated` → `Failure` sin crash; matar `:converter` a mitad → `ConverterCrashed` y la UI sigue viva.
WebView: `simple.xlsx` emite `rendered`;
petición a `https://example.com/` y a `/doc/<nonce-incorrecto>` son bloqueadas; `zipbomb` se rechaza antes del WebView.
UI Compose: inicio vacío, abrir fixture por intent `VIEW`, rotar conserva página, recientes muestra el archivo.

### 11.4 Verificación manual en el teléfono (`docs/QA.md`, la ejecuta el usuario)

Abrir desde WhatsApp/Gmail/Archivos/Drive; PDF de 300+ páginas con zoom y rotación; escanear 3 páginas y encontrar
el PDF en `Documentos/Lector`; modo avión; poca memoria (muchas apps abiertas); cambiar tema; matar la app desde
recientes con un documento abierto y volver.

---

## 12. CI/CD (`.github/workflows/`)

- `ci.yml` (push y PR): validar wrapper → `lint` → `testDebugUnitTest` → `assembleDebug` → `assembleRelease`
  (firmado si hay secrets) → `scripts/check-apk.sh` → subir APK como artefacto.
- `instrumented.yml` (PR y manual): `reactivecircus/android-emulator-runner`, API 34 y la más reciente, `connectedDebugAndroidTest`.
- `release.yml` (tag `v*`): APK release firmado adjunto a un GitHub Release.
- Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Sin ellos, el release se firma con
  la clave debug y se etiqueta `-unsigned-debugkey` (no instalable como actualización de un build anterior).
  `docs/SIGNING.md` explica al usuario cómo generar el keystore con `keytool` y cargar los secrets.
- Acciones de terceros fijadas por SHA de commit, `permissions: contents: read` por defecto.

---

## 13. Fases de ejecución

Cada fase termina con: compila, `lint` y tests en verde, commit descriptivo, push a la rama de trabajo.
No empezar una fase con la anterior en rojo. Si algo de este plan resulta imposible o incorrecto, anotar en
`docs/DECISIONS.md` qué se cambió y por qué.

| # | Fase | Entregable verificable |
|---|---|---|
| 0 | **Entorno y esqueleto.** Instalar Android SDK (cmdline-tools) si falta; si la red lo impide, apoyarse en la CI de GitHub para compilar. Proyecto Gradle, catálogo de versiones, `.gitignore`, tema, `MainActivity` vacía, `AppContainer`, `Outcome`/`DocError`, `Log`, `CrashRecorder`, StrictMode, `ci.yml`, `check-apk.sh`. | APK debug generado en CI; `check-apk.sh` pasa. |
| 1 | **Ingesta.** `UriIntake`, `MagicSniffer`, `ZipInspector`, `FileNameSanitizer`, fixtures, limpieza de caché, filtros de intent, `onNewIntent`. | Tests §11.2 de ingesta en verde. |
| 2 | **Motor PDF.** `FrameworkPdfEngine`, caché, tests instrumentados. | Tests §11.3 de PDF en verde. |
| 3 | **Visor PDF.** Tema y componentes base de `docs/DESIGN.md`. Lista, zoom, navegación, restauración de estado, compartir/guardar/imprimir. | PDF de 500 páginas fluido; rotar conserva página. |
| 4 | **Inicio y recientes.** Room, DataStore, Home, Ajustes, manejo de acceso perdido. | Flujo completo abrir → recientes → reabrir. |
| 5 | **Spike LibreOffice** (§7.2). `fetch-lokit.sh`, prototipo de conversión, medición. | Criterios de §7.2 marcados uno a uno en `docs/DECISIONS.md`: adoptado o respaldo. |
| 6 | **Visor ofimática.** `ConverterService`, `OfficeConverter`, caché de conversiones, modo presentación del visor; `SecureWebViewFactory` + hojas de cálculo + texto. | Fixtures de presentación se abren con fidelidad; tests de bloqueo de red en verde. |
| 7 | **Escáner.** ML Kit, guardado MediaStore, pendientes, respaldo con `JpegPdfWriter`. | Escaneo guardado en `Documents/Lector` y abierto. |
| 8 | **Endurecimiento.** R8, firma, verificación de dependencias, Dependabot, accesibilidad, `values-en`, búsqueda/contraseña PDF en API ≥ 35, modo noche, `docs/QA.md`, `docs/SIGNING.md`, `THIRD_PARTY.md`, README. | Release firmado en CI; revisión de seguridad (§4) punto por punto anotada en `docs/SECURITY_REVIEW.md`. |
| 9 | *(Opcional, solo si el usuario lo pide)* `PdfRendererPreV` para búsqueda/contraseña en Android 11-14. | — |

### Criterio de terminado global

- [ ] `aapt2 dump permissions` del APK release no lista ningún permiso.
- [ ] Ningún fixture (incluidos los maliciosos) provoca un crash; todos terminan en contenido o en `ErrorPane`.
- [ ] Rotar, pasar a segundo plano y "No conservar actividades" activado no pierden el documento ni la página.
- [ ] Cada pantalla coincide con `docs/DESIGN.md` en claro y oscuro (capturas en `docs/design/screens/`).
- [ ] Lint sin avisos, todos los tests en verde en CI, APK release firmado e instalable.
- [ ] `docs/SECURITY_REVIEW.md` con cada control de §4 marcado y la evidencia (test o archivo) que lo demuestra.
