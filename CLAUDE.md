# Lector — lector de documentos y escáner para Android

La especificación completa está en [`docs/PLAN.md`](docs/PLAN.md). Léela entera antes de escribir código y
ejecuta sus fases (§13) en orden. El diseño de interfaz está en [`docs/DESIGN.md`](docs/DESIGN.md) y es igual de obligatorio. Las decisiones marcadas **[FIJO]** no se cambian sin preguntar al usuario.

## Reglas que nunca se rompen

- La app no declara `INTERNET` ni ningún otro permiso. No añadir SDKs de anuncios, analítica ni crash remoto.
- Todo archivo de entrada es no confiable: pasa por `UriIntake` → `MagicSniffer` → `ZipInspector` antes de llegar a un motor.
- Los WebView se crean solo en `SecureWebViewFactory`. Prohibido `addJavascriptInterface`.
- `PdfRenderer` se usa solo dentro de `FrameworkPdfEngine`, en su dispatcher de un hilo.
- Prohibido `!!`, `runBlocking`, `GlobalScope` y E/S en el hilo principal. Las funciones de `data` devuelven `Outcome<T>`.
- LibreOffice corre solo en el proceso `:converter`, nunca en el de la UI. Sus binarios no se suben a git.
- La interfaz usa solo los tokens y componentes de `docs/DESIGN.md`: sin colores, radios ni tamaños literales en las pantallas.
- Sin dependencias nuevas fuera de `docs/PLAN.md` §2 sin anotarlo en `docs/DECISIONS.md`.
- Nunca subir keystores, `local.properties` ni secretos.

## Flujo de trabajo

- Versiones: últimas estables al momento de ejecutar, fijadas en `gradle/libs.versions.toml`.
- Antes de cada commit: `./gradlew lint testDebugUnitTest assembleDebug`.
- Una fase = uno o más commits pequeños; push al terminar la fase con todo en verde.
- Si el plan es incorrecto o inviable en algún punto, registrar el cambio y el motivo en `docs/DECISIONS.md`.
- Texto visible al usuario en español (`values/`) e inglés (`values-en/`); código e identificadores en inglés.
