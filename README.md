<div align="center">
<img src="app/src/main/assets/logo.png" width="128" alt="MiniLauncher">
<h1>MiniLauncher</h1>
<p>Launcher para pantallas Android de carro · Android 4.1 o superior</p>
<a href="https://github.com/diogo24m/MiniLauncher/releases/latest"><img src="https://img.shields.io/github/v/release/diogo24m/MiniLauncher?color=FE3211&label=versi%C3%B3n" alt="Última versión"></a>
<a href="LICENSE"><img src="https://img.shields.io/github/license/diogo24m/MiniLauncher?label=licencia" alt="Licencia"></a>
<a href="https://github.com/diogo24m/MiniLauncher/actions/workflows/android.yml"><img src="https://github.com/diogo24m/MiniLauncher/actions/workflows/android.yml/badge.svg" alt="Compilación"></a>
<p><a href="https://github.com/diogo24m/MiniLauncher/releases/latest"><b>Descargar APK</b></a></p>
</div>

**MiniLauncher 0.1.0** es la primera versión de este fork de [StradaOS 2.20.1](https://github.com/lordnordic-debug/StradaOS), de lordnordic-debug, bajo la misma licencia GPL-3.0. Es un launcher gratuito y de código abierto para pantallas Android de carro. Se instala como una app, sin flashear ni modificar el sistema.

Paquete: **`com.mini.launcher`** (pruebas: **`com.mini.launcher.debug`**). Versión: **0.1.0**, código de versión **1**.

Los datos CAN/SPD del BMW MINI, la prioridad SPD con GPS de reserva en el velocímetro, el selector Bluetooth/Reproductor, los controles ADB, la identidad MINI y el portugués de Portugal son cambios propios de este fork respecto a la base. La documentación técnica está en [docs/MINILAUNCHER.md](docs/MINILAUNCHER.md).

## Funciones

- **Vehículo:** foto personalizable, que se usa tal cual (corrige la orientación de las fotos del celular) o con el fondo quitado, y recordatorio de cambio de aceite. En centrales SPD con perfil CAN «BMW MINI», lectura de combustible, autonomía, consumo, voltaje de la batería, puertas, kilometraje, neumáticos y mantenimientos. Los servicios SPD se gestionan en Mantenimientos, cada uno con su interruptor y sus datos debajo, con avisos por fecha/distancia, aplazamiento y confirmación local. [Compatibilidad y documentación técnica](docs/carro/README.md).
- **Velocímetro y mantenimiento:** conserva los modos digital, analógico y mixto de StradaOS 2.20.1, el velocímetro sobre el mapa y los recordatorios por distancia, días, semanas o meses. Los datos SPD conviven con estos modos; el velocímetro prioriza la velocidad SPD y usa el GPS cuando la lectura no está disponible o deja de actualizarse.
- **Mapas:** posición y rumbo por GPS, orientación al norte o según la marcha, caché y mapas sin conexión en formato `.map`.
- **Navegación en el mapa (opcional):** «Ir a…» busca una dirección o un lugar, ofrece rutas alternativas y guía giro a giro sobre el mapa, con voz, hora de llegada o tiempo restante, recálculo, paradas intermedias, Casa, Trabajo, favoritos y recientes. Sin internet, con el mapa `.map` descargado, calcula una ruta aproximada. Viene apagada: el botón del mapa sigue abriendo Waze, Maps o la app elegida hasta que se elige «En el mapa de MiniLauncher».
- **Música:** selector Bluetooth/Reproductor, canción, carátula y controles para la entrada elegida, incluidas las sesiones de Android Auto. Solicita acceso a notificaciones para leer las sesiones y carga las carátulas que la app facilita mediante URI.
- **Android Auto y CarPlay:** accesos a la app de conexión de la pantalla (ZLink, CarLink, TLink, AutoKit…). Android Auto también puede usarse con Open Headunit por USB. Se pueden mostrar los dos botones, solo uno o ninguno; el espacio libre lo ocupa el clima.
- **Personalización:** accesos a apps y funciones, disposición de las tarjetas, barra normal, compacta, lateral fija a izquierda/derecha u oculta (el botón Apps pasa arriba), de 4 a 10 botones con nombres opcionales, tres tamaños de ícono, íconos de las apps y packs de íconos (formato Nova/ADW) para la barra y el cajón. La velocidad, el aceite y el reproductor se pueden ocultar, y el botón del navegador del mapa admite varios tamaños, lado u ocultarlo. Orden del cajón de apps (A-Z, Z-A, usadas o instaladas recientemente, o a mano). Temas claro, oscuro, automático por hora fija o por amanecer y atardecer (calculados con el GPS, sin internet) o «Sistema», que sigue al instante el modo oscuro de la pantalla, además de colores y tamaño de letra.
- **Conectividad:** íconos de Wi-Fi y Bluetooth junto al clima, ocultables en Apariencia. Un toque enciende o apaga (con confirmación si hay conexión); mantener presionado abre sus ajustes. En equipos que no permiten el control directo se abren los ajustes; el Bluetooth propietario de Clinkway se gestiona desde su app de fábrica.
- **Depuración:** interruptores de ADB por USB y Wi-Fi en Ajustes → Sistema. Requieren `WRITE_SECURE_SETTINGS` o root; en Android 4–10, Wi-Fi necesita root y depuración USB activa (puerto 5555); al apagar cualquiera se desactivan ambas. Desde Android 11 se usa la depuración inalámbrica nativa, con emparejamiento en las opciones de desarrollador. La ayuda de los interruptores muestra el comando para conceder el permiso a la edición instalada. Si Android no permite conocer el estado, el interruptor queda centrado y lo indica; no muestra un apagado ficticio.
- **Información:** clima arriba o en el panel del mapa, con detalle al tocarlo (sensación térmica, humedad, viento y previsión por horas y para cinco días), reloj y velocidad en km/h o mph.
- **Diagnóstico SPD:** lecturas brutas de Check Control, ACC, frenos, marcha, régimen del motor, velocidad CAN, luces y nivel de aceite, con sus IDs y la edad de la última consulta. Se muestran como datos pendientes de validar, separados de las mediciones confirmadas y del mantenimiento.
- **Diseño vertical y ventanas:** el inicio se adapta a pantallas horizontales o verticales, con la opción de forzar el diseño horizontal o vertical en pantallas casi cuadradas; los diálogos siguen el tema y conservan los encabezados de mantenimiento MINI. Tamaños de letra S, M, L y XL.
- **Copia de seguridad:** guarda o restaura las preferencias, mantenimientos, kilometraje y foto del vehículo en un archivo local o USB, con copia automática opcional. Incluye la entrada de música, el tamaño de texto y el estado local de los recordatorios SPD.
- **Pantalla en reposo (opcional):** tras unos minutos sin tocar la pantalla, se pone negra con la hora, la fecha y la velocidad (SPD o GPS), con el tamaño y el color elegidos. Un toque la quita.
- **Sistema y velocidad:** abrir una app al encender y volver al inicio, ocultar la barra del sistema en Android 4.4+ y corrección de la velocidad GPS sin alterar el dato SPD.
- **Idiomas:** español, inglés, portugués de Portugal, francés, italiano, alemán, malayo, árabe, turco y chino. Los textos propios del fork que aún no están traducidos usan el español de respaldo.

## Capturas

<p align="center">
<img src="fastlane/metadata/android/es-ES/images/phoneScreenshots/1.png" width="49%" alt="Captura de MiniLauncher 1">
<img src="fastlane/metadata/android/es-ES/images/phoneScreenshots/2.png" width="49%" alt="Captura de MiniLauncher 2">
<img src="fastlane/metadata/android/es-ES/images/phoneScreenshots/3.png" width="49%" alt="Captura de MiniLauncher 3">
<img src="fastlane/metadata/android/es-ES/images/phoneScreenshots/4.png" width="49%" alt="Captura de MiniLauncher 4">
</p>

## 📥 Instalación

1. Descarga `MiniLauncher-x.y.z.apk` desde [Releases](https://github.com/diogo24m/MiniLauncher/releases/latest).
2. Cópialo a una memoria USB y después a la **memoria interna** de la pantalla. Algunos instaladores no pueden leerlo directamente desde USB.
3. Permite la instalación desde orígenes desconocidos para el explorador que uses y abre el APK.
4. Sigue el asistente y elige **MiniLauncher → Siempre** al pulsar Inicio.

Para probarlo en un celular o emulador, consulta la [guía de pruebas](docs/PRUEBAS.md). La edición «MiniLauncher Prueba» se instala junto a la normal.

### Android Auto con Open Headunit

1. Instala [Open Headunit](https://github.com/andreknieriem/headunit-revived/releases).
2. En Open Headunit activa **«Pista actual de AA en la sesión multimedia»**.
3. En MiniLauncher activa **Ajustes → Música → Carátula de Android Auto** si quieres mostrar la carátula.
4. Conecta el celular por USB y toca **Android Auto**.

Con Android Auto solo por cable, sin Bluetooth, la notificación de Open Headunit también puede aportar canción, artista, carátula, tiempo y estado de reproducción. Activa el servicio opcional **Carátula de Android Auto** para leer esa notificación.

## Compatibilidad

Requiere **Android 4.1 (API 16)** o superior y admite pantallas horizontales y verticales. Las funciones de Bluetooth, radio, cámara y datos CAN dependen del equipo. Consulta **Ajustes → Sistema → Compatibilidad** para ver qué está disponible.

Comprueba la versión real de Android en **Ajustes → Acerca de**: algunas centrales anuncian una versión distinta de su nivel de API. Para reportar otra pantalla, abre un [issue](https://github.com/diogo24m/MiniLauncher/issues) con el modelo, API y diagnóstico.

## Preguntas frecuentes

<details><summary><b>Al encender aparece el menú de fábrica</b></summary>

Usa **Ajustes → Sistema → Pantalla de inicio** para elegir MiniLauncher como predeterminado. En Android 4–9 también puedes activar **«Abrir MiniLauncher al encender»**.
</details>

<details><summary><b>La música Bluetooth se corta o no suena</b></summary>

Sal de la app Bluetooth con **Inicio**, ya que Atrás puede cerrarla. Revisa **Ajustes → Música → Audio Bluetooth**. En Android 5+, activa **«Música de cualquier app»** para leer la canción. Si sigue fallando, exporta el diagnóstico con la música sonando y adjúntalo a un issue.
</details>

<details><summary><b>MiniLauncher o Android Auto van lentos</b></summary>

Prueba a apagar el mapa en vivo en **Ajustes → Mapa y clima → Rendimiento**. Para Open Headunit, prueba 480p, 30 FPS y H.264.
</details>

<details><summary><b>El mapa no carga</b></summary>

Comprueba el permiso de ubicación, el GPS y la conexión a internet. Para usarlo sin conexión, descarga o selecciona un archivo de mapa en **Ajustes → Mapa y clima → Mapa sin conexión**. Mantén presionado el mapa para abrir su diagnóstico.
</details>

<details><summary><b>El celular dice que ninguna app funciona con el accesorio USB</b></summary>

La app de espejo de fábrica (EasyConnection/CarbitLink) puede estar tomando el USB. Revisa **Ajustes → Conexión** y elige la app que quieres usar para Android Auto.
</details>

<details><summary><b>¿Puedo activar CarPlay solo con MiniLauncher?</b></summary>

El botón abre la app de CarPlay de la pantalla o de un adaptador compatible. MiniLauncher no incorpora CarPlay.
</details>

<details><summary><b>Quiero volver al launcher de fábrica o la pantalla no responde</b></summary>

Abre **Ajustes → Sistema → Salir de MiniLauncher** para volver al launcher de fábrica, desactivar MiniLauncher o desinstalarlo. Si la pantalla no responde, reiníciala. Para reportar el fallo, incluye el modelo, la versión de Android y el **Registro de fallos** de **Ajustes → Acerca de**.
</details>

## Documentación y contribuciones

- [Compilar, contribuir y publicar versiones](CONTRIBUTING.md).
- [Probar en celular, emulador y central](docs/PRUEBAS.md).
- [Datos del carro y evidencias CAN/SPD](docs/carro/README.md).
- [Historial de cambios](CHANGELOG.md) y [notas de mantenimiento](docs/ESTADO_PROYECTO.md).
- [Privacidad](PRIVACY.md): datos utilizados, conexiones de red y permisos.

Los reportes e ideas se reciben en [Issues](https://github.com/diogo24m/MiniLauncher/issues). Para apoyar el proyecto: [Ko-fi](https://ko-fi.com/stradaos).

## Aviso legal

MiniLauncher es un proyecto independiente, gratuito y sin fines de lucro. No está afiliado a Google, Apple, BMW/MINI ni a fabricantes de pantallas. Android Auto, CarPlay y MINI son marcas de sus respectivos propietarios. Los nombres de vehículos se usan como referencia de compatibilidad.

Úsalo bajo tu responsabilidad y no lo manipules mientras conduces.

## Licencia y créditos

Publicado bajo la [GNU GPL v3.0](LICENSE), a partir de [StradaOS](https://github.com/lordnordic-debug/StradaOS). Usa o se apoya en:

- [Open Headunit](https://github.com/andreknieriem/headunit-revived) (GPL-3.0), app aparte para Android Auto.
- Mapas © [colaboradores de OpenStreetMap](https://www.openstreetmap.org/copyright), OpenTopoMap y Esri.
- Lector de mapas sin conexión adaptado de [mapsforge](https://github.com/mapsforge/mapsforge) (LGPL-3.0); mapas de [download.mapsforge.org](https://download.mapsforge.org/).
- Clima: [Open-Meteo](https://open-meteo.com) (CC BY 4.0).
- [QR Code generator](https://github.com/nayuki/QR-Code-generator), de Project Nayuki (MIT).
- Íconos [Material Symbols](https://fonts.google.com/icons) (Apache 2.0) y certificados raíz de Mozilla (MPL-2.0).

<details><summary>English · Português</summary>

**English:** MiniLauncher is a free, open-source launcher for Android car head units, based on StradaOS. Requires Android 4.1 or later. Download the APK from Releases; the interface supports English.

**Português:** MiniLauncher é um launcher gratuito e de código aberto para centrais Android de carro, baseado no StradaOS. Requer Android 4.1 ou superior. Descarrega o APK em Releases; a interface inclui português de Portugal.
</details>
