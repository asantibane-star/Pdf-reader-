# Firma del APK

El APK de release se firma con un keystore que **vive fuera del repositorio** (`*.jks`, `*.keystore`, `keystore.properties` y
`local.properties` están en `.gitignore`). La CI lo recibe por *secrets*; nada de esto se sube nunca a git.

## 1. Generar el keystore (una sola vez)

```bash
keytool -genkeypair -v \
  -keystore lector-release.jks -alias lector \
  -keyalg RSA -keysize 4096 -validity 10000
```

Guarda `lector-release.jks` y sus contraseñas en un gestor de contraseñas y haz copia de seguridad: **si lo pierdes, no podrás
publicar actualizaciones** que se instalen sobre la versión anterior.

## 2. Cargar los secrets en GitHub

En el repositorio: *Settings → Secrets and variables → Actions → New repository secret*.

| Secret | Contenido |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 lector-release.jks` |
| `KEYSTORE_PASSWORD` | contraseña del keystore |
| `KEY_ALIAS` | `lector` (el alias de arriba) |
| `KEY_PASSWORD` | contraseña de la clave |

## 3. Qué hace la CI

- `ci.yml` (cada push y PR): compila el APK de release; si hay secrets lo firma con tu clave, si no usa la clave de
  depuración y nombra el archivo `lector-release-unsigned-debugkey.apk`. Sube los APK como artefacto.
- `release.yml` (al empujar una etiqueta `v*`, p. ej. `git tag v1.0.0 && git push origin v1.0.0`): construye el APK firmado y
  lo adjunta a una *GitHub Release*.

Un APK `-unsigned-debugkey` se instala sin problema en un teléfono, pero **no actualiza** una versión instalada que se
firmó con otra clave (hay que desinstalar antes, y se pierden los recientes).

## 4. Firmar en local

```bash
export KEYSTORE_PATH=/ruta/lector-release.jks KEYSTORE_PASSWORD=… KEY_ALIAS=lector KEY_PASSWORD=…
./gradlew assembleRelease
scripts/check-apk.sh app/build/outputs/apk/release/app-release.apk
```

`app/build.gradle.kts` crea la configuración de firma `release` solo si existe `KEYSTORE_PATH`; sin ella firma con la clave de
depuración.
