# Privacidad

MiniLauncher **no tiene cuentas, anuncios, rastreadores ni analíticas**, y no envía tus datos a servidores propios.

## Qué usa y para qué

| Dato | Para qué | ¿Sale del equipo? |
|---|---|---|
| Ubicación (GPS) | Velocímetro, mapa, clima y, si lo activas, el recordatorio de cambio de aceite (solo se guarda en el equipo la distancia recorrida, no los lugares) | Para el mapa, los servidores de mapas reciben qué zona descargar, como en cualquier mapa web. Para el clima, la latitud y longitud aproximadas se envían a [Open-Meteo](https://open-meteo.com) y, si el equipo no sabe el nombre de la ciudad, a [BigDataCloud](https://www.bigdatacloud.com). |
| Pista en reproducción | Mostrar la canción de la entrada Bluetooth o Reproductor | Los metadatos se leen en el equipo. Si la app de música proporciona una URL de carátula, se solicita esa imagen a su servidor. |
| Datos CAN/SPD del vehículo y tensión de la central | Combustible, autonomía, consumo, puertas, temperatura, neumáticos, kilometraje, mantenimiento y diagnóstico | No se envían automáticamente. El kilometraje real se guarda localmente para los recordatorios; una exportación manual del diagnóstico incluye las lecturas disponibles. |
| Foto del vehículo | Mostrarla en la pantalla de inicio | No. Se procesa y se guarda en el equipo. |
| Lista de apps instaladas | Accesos directos y cajón de apps | No. |
| Copia de seguridad | Restaurar preferencias, mantenimientos, kilometraje y foto del vehículo | Se guarda en un archivo local o USB elegido por el usuario; la copia automática también es local. No se sube a internet. |

## Conexiones de red

- **Mapas:** OpenStreetMap (servidores comunitarios), OpenTopoMap y Esri.
- **Clima:** Open-Meteo (y BigDataCloud para el nombre de la ciudad).
- **Mapa sin conexión:** solo si eliges *Descargar un mapa*, el archivo del país se baja de `download.mapsforge.org`. Después el mapa se dibuja en el equipo, sin conexión.
- **Actualizaciones:** GitHub (`api.github.com`, `raw.githubusercontent.com`) y jsDelivr, para saber si hay una versión nueva. Se puede desactivar en *Ajustes → Acerca de*.

## Permisos

- **Accesibilidad** («Carátula de Android Auto»): solo escucha las notificaciones de Open Headunit para leer canción, artista, carátula, tiempo y estado de reproducción, también con Android Auto solo por cable. Es opcional.
- **Acceso a notificaciones** (Android 5+): solo para leer la canción que suena. Es opcional.
- **WRITE_SECURE_SETTINGS**: permiso especial para los controles de depuración ADB. La app no se lo concede a sí misma; muestra el comando para el paquete instalado. Los cambios que necesitan root solo se solicitan al usar esos controles.
- **Almacenamiento:** para buscar o guardar el mapa sin conexión (`Download/MiniLauncher`) y las copias de seguridad locales o USB. Cuando Android lo permite, la carpeta o el archivo se eligen con el selector del sistema. Es opcional.

El diagnóstico de *Ajustes → Sistema → Exportar diagnóstico* se guarda en la memoria del equipo y solo lo compartes tú, si quieres.
