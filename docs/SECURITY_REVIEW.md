# Revisión de seguridad (PLAN §4)

Cada control de `docs/PLAN.md` §4 con su estado y la evidencia que lo demuestra. «Prueba» = prueba automática del repositorio
(`app/src/test`, se ejecuta con `./gradlew testDebugUnitTest`); «Instrumentada» = `app/src/androidTest` (se ejecuta en la CI con
emuladores API 34 y 36; en verde, ver `docs/DECISIONS.md` §9); «CI» = paso de `.github/workflows/ci.yml`.

Leyenda: ✅ cumplido y verificado · ⚠️ cumplido con una salvedad · ❌ pendiente · ➖ no aplica.

## 4.1 Manifiesto

| Control | Estado | Evidencia |
|---|---|---|
| Sin permisos propios; `INTERNET` y `ACCESS_NETWORK_STATE` eliminados | ✅ | `AndroidManifest.xml` (`tools:node="remove"`); prueba `ProjectRulesTest.manifestDeclaresNoPermissionsAndOneExportedActivity`; `scripts/check-apk.sh` sobre el APK final (CI): «el APK no declara ningún permiso». También se elimina `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` de AndroidX. |
| `allowBackup=false`, `dataExtractionRules` que excluyen todo, `fullBackupContent=false`, `usesCleartextTraffic=false`, `enableOnBackInvokedCallback` | ✅ | `AndroidManifest.xml`, `res/xml/data_extraction_rules.xml`; misma prueba. |
| Único componente exportado: `MainActivity` (VIEW/SEND con `content` y los MIME de §1.1), sin `scheme="file"` | ✅ | `check-apk.sh` («Activities exportadas: 1», sin receiver/provider/service exportados); prueba del manifiesto; `IntentUris` solo acepta `content`. |
| `FileProvider`: no exportado, `grantUriPermissions`, rutas solo `cache/share/` y `cache/camera/` | ✅ | `AndroidManifest.xml`, `res/xml/file_paths.xml`. |
| Único `Service`: `ConverterService` en `:converter` | ➖ | No existe: LibreOffice no se integró (`docs/DECISIONS.md` §3). La app no declara ningún servicio propio; los de ML Kit/Room no están exportados. |
| Sin `PendingIntent` | ✅ | `ProjectRulesTest.noPendingIntents`. |
| Revisar el manifiesto fusionado tras añadir ML Kit | ✅ | Comprobado quitando temporalmente las líneas de eliminación: las dependencias (ML Kit / Play Services / AndroidX) añaden `INTERNET`, `ACCESS_NETWORK_STATE` y `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; las tres se eliminan. Los componentes de ML Kit/Room que quedan no están exportados. Lo vigila `check-apk.sh` en cada build. |

## 4.2 Ingesta de archivos

| Control | Estado | Evidencia |
|---|---|---|
| Solo `content://`; otros esquemas → `UnsupportedSource` | ✅ | `UriIntakeTest.nonContentSchemesAreRejected`; `MainViewModelTest.nonContentSchemesAreRejected`; `IntentUris`. |
| Tamaños (PDF ≤ 500 MB, ofimática ≤ 50 MB, texto ≤ 5 MB) contando los bytes **al copiar** | ✅ | `UriIntakeTest.actualBytesAreCountedNotTheDeclaredSize`, `declaredSizeOverLimitFailsBeforeCopying`, `officeOverFiftyMegabytesIsTooLarge`. |
| Espacio libre ≥ tamaño + 50 MB | ✅ | `UriIntakeTest.notEnoughFreeSpaceFails`. |
| Tipo por bytes mágicos (PDF, ZIP→OOXML/ODF, OLE2 por nombres de flujo, RTF, texto) | ✅ | `MagicSnifferTest` sobre todos los fixtures (incluye `legacy.doc/.xls/.ppt`, la extensión se ignora). |
| `ZipInspector`: ≤ 5 000 entradas, ≤ 300 MB reales, entrada ≤ 100 MB, sin `..`, absolutas ni `\`; nunca extrae a disco | ✅ | `ZipInspectorTest` (bomba, traversal, exceso de entradas); `UriIntakeTest.zipBombIsRejectedAndNothingRemains`; instrumentada `ViewerWebViewTest.zipBombIsRejectedBeforeReachingTheWebView`. |
| Nombres: archivos propios con UUID; `FileNameSanitizer` en lo que escribe el usuario | ✅ | `UriIntake` (`<uuid>.bin`), `FileNameSanitizerTest`, `ScanViewModelTest.savingSanitizesTheName…`. |
| Limpieza: `open/`, `share/`, `camera/` al iniciar; borrar la copia al cerrar el visor; `converted/` se conserva | ✅ | `CacheCleanerTest`; `AppContainer.cacheReady` (la ingesta espera a que termine); `PdfViewerViewModelTest.closingTheViewerClosesTheSessionAndDeletesTheCacheCopy`. |
| Cancelar borra el parcial | ✅ | `UriIntakeTest.cancellationDeletesThePartialCopy`. |

## 4.3 WebView endurecido

| Control | Estado | Evidencia |
|---|---|---|
| `SecureWebViewFactory` es el único punto de creación | ✅ | `ProjectRulesTest.webViewsAreCreatedOnlyInTheSecureFactory`. |
| Ajustes: JS solo para assets propios, sin acceso a archivos/contenido, `blockNetworkLoads`, sin DOM storage, sin geolocalización, sin ventanas, mixed content bloqueado, sin caché, depuración solo en debug | ✅ | `SecureWebViewFactory.create` (revisión de código). |
| Prohibido `addJavascriptInterface` | ✅ | `ProjectRulesTest.noJavascriptInterface`. El canal JS→Kotlin es `WebViewCompat.addWebMessageListener` restringido a `https://appassets.androidplatform.net` y al frame principal. |
| Carga única de `appassets.androidplatform.net/viewer/index.html`; `shouldInterceptRequest` solo sirve `/viewer/` y `/doc/<nonce>`; el resto 403 vacío | ✅ | `ViewerWebTest` (assets 200 con cabeceras, nonce incorrecto/ausente/mayúsculas, traversal, otros hosts, `http`, `POST`, ruta nula → 403 vacío); `scripts/check-viewer.js` en Chromium real (`example.com`, nonce incorrecto, traversal → bloqueado); instrumentada `ViewerWebViewTest.networkAndWrongNonceRequestsAreBlocked`. |
| Nonce aleatorio de 128 bits, ligado a la sesión del visor | ✅ | `WebViewerViewModel.newNonce` (`SecureRandom`, 16 bytes); comparación en tiempo constante (`ViewerRequestInterceptor.nonceMatches`). |
| `shouldOverrideUrlLoading` devuelve `true` siempre; `http(s)` con gesto → diálogo con la URL completa | ✅ | `SecureWebViewFactory.SecureClient`; diálogo en `WebViewerScreen`. |
| CSP en `index.html` **y** como cabecera | ✅ | `ProjectRulesTest.cspInIndexHtmlMatchesTheHeaderTheInterceptorSends`; `ViewerWebTest.servesViewerAssetsWithCspHeader`. |
| Mensajes JS→Kotlin: lista cerrada (`ready`, `rendered`, `error:<código>`, `sheets:<n>`), máx. 64 | ✅ | `ViewerMessagesTest`. |
| `onRenderProcessGone` → destruir y mostrar «Reintentar» | ⚠️ | Implementado (`SecureClient` → `WebViewerViewModel.onRenderProcessGone` → `ErrorPane`); sin prueba automática (requiere matar el proceso de render en un dispositivo). |
| `onPermissionRequest` → `deny()`; `onShowFileChooser` → `false`; sin `DownloadListener` | ✅ | `SecureWebViewFactory.SecureChromeClient` (revisión de código). |
| Sin `eval`, sin scripts en línea, sin recursos externos en el visor propio | ✅ | `ProjectRulesTest.viewerScriptsHaveNoEvalNorInlineScriptsNorExternalUrls`. Las bibliotecas vendorizadas se ejecutaron bajo la CSP real en Chromium (`scripts/check-viewer.js`); `@aiden0z/pptx-renderer` provoca un `EvalError` no fatal bajo la CSP con un fixture. |
| El análisis de DOCX/XLSX ocurre en el proceso de render aislado de WebView | ✅ | Por diseño (WebView); un fallo del renderer no mata la app. |
| Archivos de terceros con hash verificado | ✅ | `ThirdPartyIntegrityTest` recalcula los SHA-256 de `THIRD_PARTY.md`. Salvedad: SheetJS viene de un espejo de npm que no se pudo contrastar (`docs/DECISIONS.md` §5). |

## 4.4 Build y cadena de suministro

| Control | Estado | Evidencia |
|---|---|---|
| Release con R8 (`isMinifyEnabled`, `isShrinkResources`), `isDebuggable=false` | ✅ | `app/build.gradle.kts`; `ProjectRulesTest.releaseIsMinifiedAndKeysStayOutOfGit`; `check-apk.sh` («no es debuggable»). |
| Keystore fuera del repo; `.gitignore` con `*.jks`, `*.keystore`, `keystore.properties`, `local.properties` | ✅ | `.gitignore` y la misma prueba; `docs/SIGNING.md`. |
| `gradle/verification-metadata.xml` con SHA-256 | ✅ | Generado y **verificado con un build limpio** (`clean lint testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest`). |
| Validación del wrapper en CI y Dependabot (Gradle y Actions) | ✅ | `.github/workflows/ci.yml` (`gradle/actions/wrapper-validation`), `.github/dependabot.yml`. |
| Lint con `abortOnError` y `warningsAsErrors`; exclusiones justificadas | ✅ | `app/build.gradle.kts`, `app/lint.xml` (cada exclusión comentada). |
| Sin secretos, URLs ni claves en el código; sin SDKs de analítica/anuncios/crash remoto | ✅ | Revisión de dependencias (`gradle/libs.versions.toml`); la única URL es el host virtual `appassets.androidplatform.net` (`ViewerUrl`), que no sale a la red. |
| `scripts/check-apk.sh` sobre el APK final | ✅ | Pasa en release y debug (`--debug`); se ejecuta en `ci.yml` y `release.yml`. |
| Acciones de terceros fijadas por SHA de commit | ❌ | Las acciones usan etiquetas de versión mayor (`@v4`): no se pudieron resolver los SHA desde el entorno de construcción. Hay que ejecutar `scripts/pin-actions.sh` con red y commitear el resultado. `permissions: contents: read` por defecto sí está. |

## 4.5 Privacidad

| Control | Estado | Evidencia |
|---|---|---|
| Room guarda solo URI, nombre visible, tipo, tamaño, fecha, última página (+ total de páginas); «Borrar recientes» | ✅ | `RecentEntity`; `RecentsRepositoryTest`; Ajustes → *Borrar recientes* (borra también las miniaturas). |
| `Log` no escribe en release; `CrashRecorder` solo guarda clases y marcos de pila | ✅ | `core/log/Log.kt` (`BuildConfig.DEBUG`); `CrashRecorderTest.recordsStackFramesButNeverTheMessage`. |
| «Ocultar contenido en apps recientes» → `FLAG_SECURE` | ✅ | `MainActivity` (observa el ajuste). Verificación manual en `docs/QA.md`. |

## Resumen

Pendiente: fijar las acciones de CI por SHA (❌) y las salvedades ⚠️ anotadas arriba. Las pruebas instrumentadas ya corrieron en
emuladores; lo que depende de gestos, de la cámara o de Google Play Services se comprueba con `docs/QA.md` en un teléfono.
