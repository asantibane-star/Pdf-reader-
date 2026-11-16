# Verificación manual en el teléfono

La ejecuta quien prueba la app (PLAN §11.4). Marca cada punto; anota el modelo y la versión de Android.
Las pruebas automáticas (`./gradlew lint testDebugUnitTest`, `connectedDebugAndroidTest` en la CI) no sustituyen esta lista:
no hubo un dispositivo ni emulador disponible mientras se escribió el código, así que **esta es la primera ejecución real**.

## Instalación

- [ ] Instalar `lector-release-….apk` (o el de depuración). Android pide permitir «instalar apps desconocidas» para tu
      navegador o gestor de archivos.
- [ ] Ajustes del sistema → Apps → Lector → Permisos: **no debe pedir ni mostrar ningún permiso**.
- [ ] El icono se ve bien (también en modo de iconos temáticos de Android 13+).

## Abrir documentos

- [ ] Desde **Archivos**, **Drive**, **WhatsApp** y **Gmail**: abrir un PDF y elegir *Lector* → entra directo al visor; «atrás»
      vuelve a la app de origen, no a Inicio.
- [ ] Compartir un PDF a Lector (menú *Compartir*) funciona igual.
- [ ] Botón **Abrir** de Inicio: elegir un PDF, un `.docx`, un `.xlsx`, un `.pptx`, un `.csv` y un `.txt`.
- [ ] Formatos no soportados (`.doc`, `.ppt`, `.odt`, `.rtf`): aparece «No compatible…» y **Abrir con otra app** funciona.
- [ ] Un archivo dañado (renombra un `.zip` a `.pdf`) muestra un error claro, no se cierra la app.

## Visor de PDF

- [ ] PDF de 300+ páginas: el desplazamiento es fluido; el deslizador de páginas salta al instante.
- [ ] Zoom con dos dedos y doble toque (1× ↔ 2,5×); a zoom alto el texto se ve nítido tras ~0,2 s.
- [ ] Rotar el teléfono con el documento abierto: **no** vuelve a la página 1 ni reabre el archivo.
- [ ] Un toque oculta/muestra las barras; a los 3 s se ocultan solas; con ellas ocultas también las barras del sistema.
- [ ] Menú: *Ir a página*, *Compartir*, *Guardar copia*, *Imprimir*, *Modo noche*, *Mantener pantalla encendida*.
- [ ] Android 15+: buscar un texto (contador «3 de 17», flechas, resaltado). PDF con contraseña: pide la clave.
- [ ] Android 10–14: un PDF con contraseña explica que no se puede abrir aquí; no aparece el botón de buscar.

## Otros visores

- [ ] `.xlsx`/`.xls`/`.csv`: tabla con cabeceras fijas, cifras a la derecha, chips de hoja abajo, búsqueda.
- [ ] `.docx`: texto, tablas e imágenes; aviso «Vista aproximada».
- [ ] `.pptx`: diapositivas apiladas, tema oscuro, aviso «Vista aproximada». **Comparar con PowerPoint: no es idéntico** (ver
      `docs/DECISIONS.md`, §3).
- [ ] `.txt`: control de tamaño (`Aa`), selección de texto.

## Escáner

- [ ] **Escanear** abre el escáner de Google; escanear 3 páginas → hoja «Nombrar» → Guardar.
- [ ] El PDF aparece en *Archivos → Documentos → Lector* y se abre en el visor; el aviso ofrece *Compartir*.
- [ ] Cerrar la app a la fuerza con el escáner abierto y volver: el flujo continúa o se ofrece guardar el escaneo pendiente.
- [ ] Sin Google Play Services (o simulándolo): modo simple con **Cámara**, **Galería**, reordenar arrastrando, quitar y **Crear PDF**.

## Robustez

- [ ] **Modo avión**: todo funciona igual (la app no usa red).
- [ ] Poca memoria (muchas apps abiertas): un PDF grande no cierra la app.
- [ ] Cambiar tema claro/oscuro/sistema en Ajustes y con el sistema: todas las pantallas se ven bien.
- [ ] Opciones de desarrollador → «No conservar actividades»: abrir un PDF, ir a otra app y volver mantiene página y zoom.
- [ ] Matar la app desde Recientes con un documento abierto y volver a abrirla: se puede reabrir desde Inicio.
- [ ] Escala de fuente 1,3× y «tamaño de pantalla grande»: nada se corta; los botones no se parten en dos líneas.
- [ ] TalkBack: los botones de icono tienen nombre; el deslizador anuncia «Página 12 de 48».
- [ ] Ajustes → **Ocultar contenido en apps recientes**: la miniatura de Recientes sale en blanco.
- [ ] Ajustes → *Borrar recientes* vacía la lista.
