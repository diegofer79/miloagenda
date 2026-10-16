# Agenda Milo — traspaso para Claude Code

App Android para F-Droid: **reloj, calendario, clima y notas** en un inicio de widgets combinables.

## Datos fijos
- Paquete: `org.milo.agenda` · Licencia: GPL-3.0 · Repo: github.com/diegofer79/miloagenda
- Dispositivo objetivo: Lenovo Tab 2 A10 (1920×1200, 1280×800 dp, horizontal, Android 5/6) → `minSdk 21`
- Kotlin + Jetpack Compose + Material 3, tema claro
- Sin cuentas, sin rastreo, sin Google Play Services. Datos guardados localmente
- Clima: Open-Meteo (sin clave). En Android < 7.1.1 hay que incluir el certificado ISRG Root X1 (`res/raw/isrgrootx1.pem`)

## Pantallas (ver `demo/design/`)
1. `01-inicio-bento.png` — **Inicio elegido**. Bento de 4 columnas: reloj grande, clima en círculo, siguiente evento, nota fijada con casillas, agenda de hoy, temporizador, aviso de lluvia. Barra flotante: Nuevo, Agenda, Clima, Notas, Widgets, Ajustes. Cada widget se puede activar o desactivar en Ajustes.
2. `02-agenda.png` — Mini-mes a la izquierda, día a la derecha con el clima de cada hora y una línea de "ahora". La campana crea o quita la alarma del evento.
3. `03-clima.png` — Actual, 10 horas, viento, humedad, UV, presión, sol y 7 días con barras de rango.
4. Notas (sin PNG final): diseño lista-detalle con buscador, filtros (Todas / Listas / De eventos), cuadrícula escalonada a la izquierda y editor a la derecha. El editor tiene título, texto, casillas, color, fijar y vincular a un evento. Se guarda solo.
5. Ajustes (sin PNG): widgets del inicio, ciudad (con buscador de Open-Meteo), alarma por defecto (15 min) y una nota de privacidad.

`alt-*.png` son alternativas descartadas, solo como referencia.

## Conexiones entre secciones
- Los eventos muestran el clima previsto a su hora.
- Las alarmas se crean desde los eventos con `AlarmManager.setAlarmClock`, y se reprograman al reiniciar el dispositivo.

## Colores (Material 3, tonos de verde salvia)
| Rol | Hex |
|---|---|
| primary | #4C6A3D |
| primaryContainer / on | #CDEDB7 / #0B2000 |
| secondaryContainer / on | #D9E7CB / #131F0D |
| tertiaryContainer / on / ink | #BCEBEB / #002020 / #386666 |
| melocotón / on / ink | #FFDCC4 / #2F1500 / #8B5000 |
| surface / container / high / highest | #FBF9F3 / #F0EEE6 / #EAE8E0 / #E3E3DA |
| ink / inkVar / outline / outlineVar | #1B1C18 / #44483F / #75796E / #C5C8BB |
| error / línea "ahora" | #BA1A1A |

Radios: tiles 28–40 dp, tarjetas 20 dp, chips 8–12 dp. Tipografía Roboto, con el reloj en ExtraLight a 150 sp.

## Estado del código
Ya hay un primer borrador en `app/`: Gradle, manifest, icono "M", almacenamiento JSON, alarmas, Open-Meteo, Inicio, Agenda y Clima.
**Falta:** `ui/Notes.kt` (`NotesScreen(onBack, initialId)`), `ui/Settings.kt` (`SettingsScreen(onBack)`), el Gradle wrapper, LICENSE (GPL-3.0), `.github/workflows/build.yml` (que compile `assembleDebug` y suba el APK como artifact) y `fastlane/metadata/android/es-ES/`. Después hay que compilar y corregir lo que falle.

## Pedido sugerido para Claude Code
> Lee demo/HANDOFF.md y los PNG de demo/design. Completa las pantallas de Notas y Ajustes, añade el wrapper de Gradle, LICENSE GPL-3.0, un workflow de GitHub Actions que genere el APK debug y la metadata fastlane para F-Droid. Compila con `./gradlew assembleDebug` hasta que funcione.
