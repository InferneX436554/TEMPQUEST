# TempQuest Android + NEXUS NOC

Aplicación Android para consultar dispositivos y mediciones desde la API PHP de NEXUS NOC y recibir notificaciones push mediante Firebase Cloud Messaging (FCM).

## Conexión

La dirección inicial del emulador es `http://10.0.2.2/nexus_mysql`. En un teléfono físico se debe cambiar desde el indicador de conexión:

- XAMPP: `http://IP-DE-LA-PC/nexus_mysql` (misma red Wi-Fi).
- Servidor online: `https://TU-DOMINIO/nexus_mysql`.

La app consulta `/api/status`, `/api/history` y `/api/alerts`. Al obtener el token FCM lo registra en `/api/register-device`.

## Firebase y notificaciones

1. Creá un proyecto Firebase y registrá la app Android con el ID `com.tempquest.monitor`.
2. Descargá `google-services.json` y guardalo en `app/google-services.json`.
3. Configurá el servidor de notificaciones siguiendo `nexus_mysql/INSTALACION_NOTIFICACIONES.txt`.
4. Sincronizá Gradle y recompilá el APK.
5. Aceptá el permiso de notificaciones en Android.

TempQuest muestra notificaciones generales cuando cambia la base de datos. El tipo de mensaje `high_temperature` conserva el comportamiento de alarma de temperatura alta existente.

## Compilar

Abrí `tempquest-android` en Android Studio, sincronizá Gradle y elegí **Build > Build APK(s)**. El APK de depuración queda en `app/build/outputs/apk/debug/`.

La app requiere que el servidor PHP esté accesible. Para XAMPP, habilitá Apache, MySQL, `mod_rewrite` y el acceso de Apache en el firewall. Para uso online, publicá la API mediante HTTPS.
