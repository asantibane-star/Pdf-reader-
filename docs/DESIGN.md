# Lector — Diseño de interfaz

Especificación visual y de interacción. Es obligatoria: quien implemente no elige colores, radios, tipografías
ni textos por su cuenta. Referencia visual: [`design/mockup.png`](design/mockup.png) (fuente: `design/mockup.html`).
Si esta especificación y el mockup difieren, manda este documento.

## 1. Dirección

**Lectura del encargo:** herramienta personal de lectura, de uso diario, para una sola persona. Lenguaje
calmado y preciso, tipo instrumento: superficies neutras frías, un solo acento, tipografía grotesca, controles
flotantes en forma de píldora. El documento es el protagonista; la interfaz se aparta.

Base: Material 3 en Compose (comportamiento, accesibilidad, estados), con **tema propio completo**. No debe
reconocerse el Material por defecto: nada de morado, nada de FAB cuadrado, nada de barras pegadas a los bordes.

Diales: variación de composición 4 · movimiento 4 · densidad 5.

Principios, por orden de prioridad cuando choquen:
1. **Contenido primero.** En los visores todo el cromo flota sobre el documento y desaparece con un toque.
2. **Un solo acento.** El verde pino marca la acción principal y el progreso. Nada más lleva color.
3. **Una mano.** Las acciones frecuentes están en el tercio inferior de la pantalla.
4. **Quieto por defecto.** Solo hay movimiento cuando responde a un gesto o explica un cambio de estado.

## 2. Tokens

### 2.1 Color

Un único juego de tokens semánticos; se mapean a `ColorScheme` de Material 3 (ver columna M3) y los que no
tienen equivalente van en `LectorColors` vía `CompositionLocal`. Color dinámico del sistema: **desactivado por
defecto**, opcional en Ajustes; al activarlo solo sustituye `accent*`.

| Token | Claro | Oscuro | M3 |
|---|---|---|---|
| `bg` | `#F4F5F6` | `#0E1012` | `background`, `surface` |
| `surface` | `#FCFCFD` | `#17191C` | `surfaceContainerLowest`, `surfaceContainer` |
| `sunken` | `#E9EBEE` | `#202327` | `surfaceContainerHigh`, `surfaceVariant` |
| `canvas` (fondo tras las páginas) | `#E3E6E9` | `#08090A` | — |
| `text` | `#15171A` | `#ECEEF0` | `onBackground`, `onSurface` |
| `textMuted` | `#5C636B` | `#9BA3AB` | `onSurfaceVariant` |
| `line` (contornos de 1 dp) | `#15171A` al 8 % | `#ECEEF0` al 9 % | `outlineVariant` |
| `accent` | `#0E6B56` | `#5FD3B3` | `primary` |
| `onAccent` | `#F4FBF8` | `#06281F` | `onPrimary` |
| `accentSoft` | `#DDEEE8` | `#16332C` | `primaryContainer`, `secondaryContainer` |
| `onAccentSoft` | `#0A4A3C` | `#A9E8D6` | `onPrimaryContainer` |
| `danger` | `#B3261E` | `#F2B8B5` | `error` |
| `scrim` | `#0E1012` al 50 % | `#000000` al 60 % | `scrim` |

Reglas:
- Sin negro ni blanco puros en la interfaz. El blanco puro solo existe en las páginas del documento.
- Contraste mínimo AA: texto normal 4,5:1, texto grande e iconos 3:1. Un test unitario calcula el contraste de
  cada par texto/fondo de la tabla y falla si no se cumple.
- Los tipos de archivo **no** se distinguen por color, sino por monograma (§4.3).
- `tertiary` y `secondary` de M3 se igualan a `accent`/`textMuted` para que ningún componente saque un color ajeno.

### 2.2 Tipografía

Fuentes incluidas en `res/font/` (sin fuentes descargables, la app no tiene red):
**Geist** variable (interfaz) y **Geist Mono** (cifras: contador de páginas, tamaños, monogramas). Licencia OFL.

| Estilo | Fuente | Tamaño/alto de línea (sp) | Peso | Tracking |
|---|---|---|---|---|
| `display` (título "Lector", estados vacíos) | Geist | 34 / 38 | 600 | -0,035 em |
| `title` (títulos de pantalla, diálogos) | Geist | 22 / 28 | 600 | -0,02 em |
| `itemPrimary` (nombre en tarjeta destacada) | Geist | 16 / 20 | 500 | -0,01 em |
| `item` (nombre en lista, barra del visor) | Geist | 15 / 20 | 500 | 0 |
| `body` | Geist | 15 / 22 | 400 | 0 |
| `meta` (fecha, tamaño, etiquetas de grupo) | Geist | 13 / 18 | 400 (grupos 500) | 0 |
| `button` | Geist | 15 / 20 | 500 | 0 |
| `mono` (contador, monograma 11 sp) | Geist Mono | 13 / 18 | 500 | 0,04 em en monograma |

Todo en `sp`; la interfaz debe aguantar escala de fuente del sistema 1,3× sin cortes (los nombres se truncan
con elipsis a 2 líneas en la tarjeta destacada y a 1 en lista). Sin mayúsculas sostenidas, sin cursivas.

### 2.3 Forma, espaciado y elevación

Regla de radios (única, sin excepciones):
- **Píldora (radio total):** todo lo interactivo flotante: botones, barras del visor, dock, chips, botones de icono.
- **28 dp exterior / 22 dp interior:** tarjeta destacada con doble marco. **28 dp:** diálogos y hojas inferiores.
- **12 dp:** mosaicos de monograma y campos de texto. **8 dp:** miniaturas. **4 dp:** páginas del documento.

Espaciado en múltiplos de 4 dp. Margen lateral de pantalla 20 dp. Entre grupos 22 dp. Fila de lista: alto mínimo 64 dp.
Objetivo táctil mínimo 48 × 48 dp (los botones de icono dibujan 44 dp y amplían su área táctil).

Elevación: **sin `tonalElevation`** (no teñir superficies). Dos recursos:
- Contorno interior de 1 dp con `line`.
- Sombra única "flotante" para lo que flota (dock, barras, tarjeta destacada): desplazamiento Y 18 dp, desenfoque 40 dp,
  extensión -22 dp, color `#142832` al 35 % (claro) / negro al 80 % (oscuro). En Compose: `Modifier.shadow` con
  `ambientColor`/`spotColor` teñidos, elevación 12 dp; ajustar a ojo contra el mockup.

Sin desenfoque de fondo (coste de GPU al desplazar): las barras flotantes usan `surface` al 94 % de opacidad.

### 2.4 Iconos

Phosphor **Light** (MIT), convertidos a `VectorDrawable` de 24 dp desde `docs/design/icons/*.svg`. Tamaño en
pantalla 22 dp (20 dp dentro del círculo del botón principal). Una sola familia; prohibidos los iconos de Material
y los dibujados a mano. Si falta un glifo, se toma del mismo paquete `@phosphor-icons/core` variante `light`.

## 3. Movimiento

| Uso | Especificación |
|---|---|
| Pulsación de cualquier control | escala a 0,97 con muelle (`stiffness 600`, `damping 0.7`); vuelve al soltar |
| Navegación entre pantallas | fundido cruzado + desplazamiento Y de 12 dp, 240 ms, curva `(0.32, 0.72, 0, 1)` |
| Aparición/ocultación de barras del visor | fundido + desplazamiento Y de 16 dp hacia su borde, 220 ms, misma curva |
| Entrada de la lista de recientes | solo en el primer dibujo: cada fila fundido + 8 dp, escalonado 30 ms, máx. 8 filas |
| Reordenación / eliminación en listas | `animateItem()` con muelle por defecto |
| Marcadores de carga | brillo que recorre el esqueleto, 1 200 ms, lineal |
| Zoom por doble toque | muelle `stiffness 300`, `damping 0.9` |

- Animar solo `alpha`, `scale` y `translation` (vía `graphicsLayer`).
- Si el sistema tiene las animaciones desactivadas (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`), todo es instantáneo
  y el brillo de carga queda estático.
- Háptica: `LongPress` al entrar en reordenar; `TextHandleMove` al cruzar cada página con el deslizador (máx. 1 cada 60 ms);
  `Confirm` al guardar un escaneo.

## 4. Componentes

Todos en `ui/common/`. Las pantallas no construyen variantes propias.

### 4.1 `FloatingDock` (Inicio)
Píldora flotante centrada, 22 dp sobre el borde inferior seguro. Relleno interior 6 dp, separación 6 dp. Contiene:
- **Abrir**: botón de texto con icono `folder-open` a la izquierda, alto 52 dp, sin fondo.
- **Escanear**: botón principal, fondo `accent`, texto `onAccent`, alto 52 dp, con el icono `scan` a la derecha dentro de
  un círculo de 36 dp (`onAccent` al 16 %) pegado al borde interior derecho.

La lista añade 96 dp de relleno inferior para que el dock no tape la última fila.

### 4.2 `ContinueCard` (doble marco)
Marco exterior: `sunken`, radio 28 dp, relleno 6 dp, contorno `line`. Núcleo: `surface`, radio 22 dp, relleno 14 dp,
sombra flotante. Contenido: miniatura 66 × 88 dp (primera página; para presentaciones 88 × 50 dp), nombre (`itemPrimary`,
máx. 2 líneas), "Página 12 de 48" (`meta`), y una línea de progreso de 3 dp en `accent` **sin pista de fondo**, ancho
proporcional a página/total. Toda la tarjeta es pulsable.

Miniaturas: se generan al cerrar el documento, JPEG calidad 80, lado mayor 264 px, en `filesDir/thumbs/<id>.jpg`.
Se borran con "Borrar recientes" y al quitar la entrada. Si no hay miniatura se muestra el mosaico de monograma.

### 4.3 `RecentRow` y `KindTile`
Fila: mosaico 44 dp (`sunken`, radio 12 dp) con monograma en `mono` 11 sp `textMuted`: `PDF`, `PPT`, `DOC`, `XLS`, `TXT`.
A la derecha nombre (`item`, 1 línea, sin extensión) y debajo `meta`: fecha relativa + punto medio + tamaño
("Hace 2 h · 8,3 MB"). Sin separadores entre filas; los grupos se separan por espacio y una etiqueta (`Hoy`, `Esta semana`,
`Este mes`, `Antes`). Deslizar a la izquierda quita la entrada con opción "Deshacer" durante 4 s. Pulsación larga abre
una hoja con: Compartir, Quitar de la lista.

### 4.4 `FloatingBar` (visores)
- **Superior:** a 6 dp bajo la barra de estado, márgenes 12 dp, alto 52 dp. Atrás (`arrow-left`), título (`item`, elipsis),
  hasta dos acciones y menú (`dots-three-vertical`).
- **Inferior:** a 22 dp del borde inferior seguro, alto 52 dp. Contador en `mono` ("12 / 48") y deslizador: pista de 2 dp
  en `line`, tramo recorrido y pulgar de 16 dp en `accent`. Oculto si el documento tiene menos de 4 páginas.

Ambas: `surface` al 94 %, contorno `line`, sombra flotante.

### 4.5 Botones, diálogos, hojas
- Principal: píldora `accent`, alto 52 dp. Secundario: píldora con contorno `line` y texto `text`. Terciario: solo texto `accent`.
  Destructivo: texto `danger`. Etiquetas de una o dos palabras, nunca en dos líneas.
- Diálogo: `surface`, radio 28 dp, relleno 24 dp, título `title`, cuerpo `body`, acciones a la derecha (terciario + principal).
- Hoja inferior: `surface`, radio superior 28 dp, asa de 32 × 4 dp, filas de 56 dp con icono a la izquierda.
- Campo de texto: `sunken`, radio 12 dp, etiqueta encima (nunca como marcador), mensaje de error debajo en `danger`.
- Avisos temporales: píldora `text` sobre `bg` invertido, sobre el dock, 4 s.

### 4.6 `StatePane` (vacío, carga, error)
Composición alineada a la izquierda, centrada en vertical, margen 28 dp: ilustración opcional, titular `display` reducido
a 26/30, cuerpo `body` en `textMuted` (máx. 28 caracteres por línea aprox.), y hasta dos botones. Nunca un indicador
circular a pantalla completa: la carga usa esqueletos con la forma del contenido final.

## 5. Arquitectura de navegación

```
Inicio ──► Visor PDF / presentación ──► (menú: buscar, compartir, guardar copia, imprimir, modo noche)
   │  └──► Visor de hoja de cálculo · Visor de texto
   ├──► Escanear ──► Nombrar ──► Visor PDF
   └──► Ajustes ──► Licencias · Último error
```

Tres niveles como máximo. Sin barra de navegación inferior ni cajón lateral: la app tiene una sola sección.
Abrir un archivo desde otra app entra directo al visor; "atrás" vuelve a esa app, no a Inicio.

## 6. Pantallas

### 6.1 Inicio
De arriba abajo: barra de estado · fila con "Lector" (`display`) y botón de ajustes circular (`surface` + contorno) ·
`ContinueCard` del último documento (si lo hay) · grupos de recientes · `FloatingDock`.
Al desplazar, el título se contrae a `title` en una barra superior con fondo `bg`.

**Vacío:** como en el mockup: dos hojas superpuestas dibujadas con formas de Compose (una en `sunken` girada 8°, otra
en `surface` girada -4° con una raya `accent`), titular "Tus documentos aparecerán aquí", cuerpo
"Abre un archivo del teléfono o escanea un papel para guardarlo como PDF." El dock permanece.

**Carga inicial:** esqueleto de la tarjeta y de 4 filas; se muestra solo si la lectura tarda más de 150 ms.

Ancho ≥ 600 dp: contenido centrado con ancho máximo 560 dp. Horizontal en teléfono: misma composición con desplazamiento.

### 6.2 Visor de PDF
Fondo `canvas`. Páginas blancas con radio 4 dp, separación 12 dp, margen lateral 14 dp a zoom 1×, sombra mínima
(1 dp). Barras flotantes superior e inferior visibles al abrir; se ocultan con un toque en la página, al empezar a
desplazar hacia abajo y a los 3 s de inactividad; reaparecen con un toque. Con las barras ocultas, las barras del sistema
también se ocultan (modo inmersivo), y vuelven con un deslizamiento desde el borde.

Acciones de la barra superior: buscar (si está disponible) y menú. Menú en hoja inferior: Ir a página, Compartir,
Guardar copia, Imprimir, Modo noche, Mantener pantalla encendida.
**Búsqueda:** la barra superior se transforma en campo con contador "3 de 17" y flechas anterior/siguiente; resultados
resaltados con `accent` al 30 %, el actual al 60 %.
**Carga de página:** rectángulo blanco con brillo; nunca un hueco vacío.
**Modo noche:** invierte luminancia de las páginas; `canvas` pasa al valor oscuro aunque el tema sea claro.

### 6.3 Visor de presentación
Tema **oscuro forzado** en esta pantalla (las diapositivas destacan sobre negro), independientemente del ajuste.
Paginador horizontal, diapositiva centrada con radio 6 dp y márgenes 14 dp. Tira de miniaturas sobre la barra inferior:
5 visibles, relación 16:9, radio 5 dp, las no activas al 45 % de opacidad, la activa con anillo `accent` de 2 dp; se
desplaza y centra la activa. Barra superior con acción "pantalla completa" (`arrows-out-simple`): gira a horizontal,
oculta todo el cromo y avanza con toque en el tercio derecho, retrocede en el izquierdo.

### 6.4 Preparando documento (conversión)
Mismo marco que el visor de destino, ya con su barra superior y el título. En el centro, un esqueleto con la proporción
esperada (16:9 para presentaciones, A4 para documentos) con brillo, y debajo: "Preparando presentación" (`title`) y
"La primera vez puede tardar unos segundos." (`body`, `textMuted`). Botón secundario **Cancelar**.
Si tarda más de 20 s el cuerpo cambia a "Es un archivo grande. Sigue en ello."

### 6.5 Hoja de cálculo y texto
Barra superior flotante igual que el PDF. Hoja: tabla a sangre sobre `surface`, cabeceras fijas en `sunken`, texto `body`
13 sp, cifras alineadas a la derecha; selector de hojas como chips en píldora en la barra inferior. Los colores del
WebView se inyectan como variables CSS desde los tokens del tema activo. Texto: `body` 16/26, ancho máximo 68 caracteres,
control de tamaño (`text-aa`) en la barra superior.

### 6.6 Escáner
El escáner de Google trae su propia interfaz; Lector diseña lo de antes y lo de después.
**Nombrar:** hoja inferior con miniatura de la primera página, "3 páginas", campo "Nombre" prellenado y seleccionado,
botones **Guardar** (principal) y **Descartar** (destructivo, pide confirmación).
**Guardado:** aviso "Guardado en Documentos/Lector" con acción "Compartir", y se abre el visor.
**Modo simple** (sin servicios de Google): cuadrícula de 2 columnas de páginas con número, arrastrar para reordenar,
botón de quitar en cada una; dock inferior con **Cámara**, **Galería** y **Crear PDF** (principal, desactivado sin páginas).

### 6.7 Ajustes
Lista en una columna, grupos con etiqueta `meta`: **Apariencia** (Tema: Sistema / Claro / Oscuro en control segmentado
de píldoras; Usar colores del sistema), **Lectura** (Mantener pantalla encendida; Ocultar contenido en apps recientes),
**Datos** (Borrar recientes; Vaciar caché de conversiones, con el tamaño ocupado), **Acerca de** (versión; Licencias;
Ver último error, solo si existe). Interruptores con pista `sunken` y `accent` al activarse.

## 7. Textos (español; equivalentes en `values-en/`)

Tono: frases cortas, segunda persona, sin tecnicismos, sin signos de exclamación, sin raya larga. Cada error dice qué pasó
y qué se puede hacer.

| Clave | Texto |
|---|---|
| `home_open` / `home_scan` | Abrir / Escanear |
| `home_empty_title` | Tus documentos aparecerán aquí |
| `home_empty_body` | Abre un archivo del teléfono o escanea un papel para guardarlo como PDF. |
| `continue_page` | Página %1$d de %2$d |
| `err_unsupported` | Lector no puede abrir este tipo de archivo. |
| `err_too_large` | El archivo es demasiado grande para abrirlo aquí. |
| `err_no_space` | No queda espacio suficiente en el teléfono. Libera espacio e inténtalo de nuevo. |
| `err_access_lost` | Ya no hay acceso a este archivo. Búscalo de nuevo para abrirlo. |
| `err_corrupt` | El archivo está dañado o incompleto. |
| `err_password_unsupported` | Este PDF tiene contraseña y esta versión de Android no permite abrirlo aquí. |
| `err_password_wrong` | Contraseña incorrecta. |
| `err_converter_crashed` | No se pudo preparar el documento. |
| `err_conversion_timeout` | El documento tardó demasiado en prepararse. |
| `err_converter_unavailable` | Este teléfono no puede mostrar este formato. |
| `err_webview` | Falta un componente del sistema (WebView) para mostrar este archivo. |
| `err_scanner` | El escáner avanzado no está disponible. Usa el modo simple. |
| `err_save` | No se pudo guardar el PDF. |
| `err_unknown` | Algo salió mal al abrir el archivo. |
| `action_retry` / `action_back` / `action_open_with` / `action_find_file` | Reintentar / Volver / Abrir con otra app / Buscar archivo |
| `convert_title_slides` / `convert_title_doc` | Preparando presentación / Preparando documento |
| `scan_saved` | Guardado en Documentos/Lector |
| `recent_removed` / `action_undo` | Quitado de la lista / Deshacer |
| `slides_static_note` (Acerca de) | Las presentaciones se muestran sin animaciones ni vídeo. |

## 8. Accesibilidad

- `contentDescription` en todo botón de icono; las miniaturas decorativas llevan `null`.
- Orden de foco de TalkBack: barra superior, contenido, barra inferior. Con las barras ocultas, una acción de
  accesibilidad "Mostrar controles" las recupera.
- El deslizador de páginas expone `stateDescription` "Página 12 de 48" y pasos de una página.
- Nada depende solo del color: el progreso tiene texto, los errores tienen texto, la miniatura activa tiene anillo.
- Probar con escala de fuente 1,3× y con tamaño de pantalla "grande".

## 9. Lista de comprobación antes de dar una pantalla por terminada

- [ ] Coincide con el mockup en claro y oscuro (captura guardada en `docs/design/screens/`).
- [ ] Solo tokens de §2; ningún color, radio o tamaño de texto literal en la pantalla.
- [ ] Un solo color de acento; ningún color por tipo de archivo.
- [ ] Estados vacío, carga y error implementados con `StatePane` o esqueletos.
- [ ] Ninguna etiqueta de botón se parte en dos líneas a 320 dp de ancho ni con fuente 1,3×.
- [ ] Objetivos táctiles ≥ 48 dp; contraste AA.
- [ ] Sin raya larga en ningún texto visible.
- [ ] Con animaciones del sistema desactivadas no hay movimiento.
