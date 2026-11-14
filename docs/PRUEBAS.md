# Cómo probar MiniLauncher sin el carro

Hay tres niveles de prueba, del más rápido al más real.

MiniLauncher incluye pruebas automáticas: con un emulador Android 11 sin SPD conectado,
ejecuta `./gradlew connectedDebugAndroidTest`. Comprueban el contrato Binder, telemetría, música, kilometraje,
categorías de ajustes y los velocímetros con letra grande. Ver [MINILAUNCHER.md](MINILAUNCHER.md).
En el APK debug sin servicio SPD, el vehículo muestra datos fijos identificados como simulados.

## 1. Pruebas automáticas en emuladores (en cada cambio)

Cada push a `main` que toca `app/` corre el workflow **«Pruebas en emuladores»** (`.github/workflows/pruebas.yml`):

- Instala la versión de prueba en emuladores de **Android 4.1 (API 16), 4.4 (19), 5.0 (21), 7.0 (24), 9 (28), 11 (30) y 14 (34)**, con pantalla de carro de **1024x600**.
- Abre cada pantalla (asistente, inicio, menú de un botón, lista de apps, Ajustes → Accesos, Música, Mapa, Acerca de, lista de música, todas las apps, barra cambiada) y toma una captura.
- **Falla si MiniLauncher se cierra o se traba** en cualquiera de esas versiones (`CIERRES.txt`).
- Las capturas, el logcat y el registro de fallos quedan en la rama **[`pruebas`](https://github.com/diogo24m/MiniLauncher/tree/pruebas)**, una carpeta por versión de Android, con un resumen en su `README.md`.

El script es `.github/scripts/smoke.sh`. La versión de prueba acepta `--es prueba VALOR` para abrir cada pantalla sin tocar coordenadas (ver `testHook` en `Main.java`; solo funciona en el paquete `.debug`).

## 2. Versión de prueba en el celular (minutos)

Cada push a `main` publica **«MiniLauncher Prueba»** en el Release [`prueba`](https://github.com/diogo24m/MiniLauncher/releases/tag/prueba):

- Enlace fijo: https://github.com/diogo24m/MiniLauncher/releases/download/prueba/MiniLauncher-prueba.apk
- Se instala **junto a la normal** (paquete `com.mini.launcher.debug`), con el nombre «MiniLauncher Prueba». No reemplaza ni toca la versión instalada.
- Está firmada con la misma llave, así que cada prueba nueva **se instala encima** de la anterior sin desinstalar.
- No avisa actualizaciones: para la siguiente prueba se vuelve a abrir el enlace.
- No es una versión oficial: no aparece como «última versión» ni en la página ni en el aviso de la app.

**En el celular:** descargar el APK, instalar (permitir «Instalar apps desconocidas» para el navegador), abrir «MiniLauncher Prueba» y **girar el celular en horizontal** (activar la rotación automática). Si Android pregunta qué launcher usar al tocar Inicio, elegir el de siempre con «Siempre»: MiniLauncher Prueba se abre desde su ícono.

Sirve para todo lo que no depende del carro: barra inferior, apps preferidas (Spotify, Waze, Maps del celular), ajustes, asistente, mapa y clima. No sirve para radio, cámara de reversa, Bluetooth de la pantalla ni Android Auto por USB.

## 3. En la pantalla del carro (lo que depende del equipo)

Lo que toca radio, cámara, Bluetooth de fábrica o Android Auto se prueba en la pantalla. Se puede instalar «MiniLauncher Prueba» también ahí (convive con la normal) o esperar la versión oficial.

## Publicar una versión oficial

La primera versión es `0.1.0`, con `versionCode 1`. Para las siguientes, sube `versionCode`/`versionName` y agrega los changelogs (ver [CONTRIBUTING.md](../CONTRIBUTING.md)). `release.yml` publica el Release `vX.Y.Z` firmado y la app avisa la actualización.
