# liveperf

Prototipo Android (sideload) para actuaciones en directo: jerarquía **grupo → setlist → canción → instrumentos** (catálogo V3Sound Grand Piano Pro, MSB 0), modo edición y modo directo, pantalla horizontal fija, modo avión controlado por la app, y Program Change MIDI por USB (canal 1; p. ej. M-Audio Uno → V3Sound).

No está en Play Store. Build debug firmado con el keystore local del SDK.

## Requisitos

- JDK 17+
- Android SDK (`platforms/android-35`, `build-tools/35.0.1`, `platform-tools`)
- Teléfono de prueba: Motorola **moto g75 5G** (u otro con USB OTG + MIDI)

```bash
export ANDROID_HOME=/home/mjam/Android/Sdk   # ajusta si hace falta
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

## Build

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Conectar el g75 por USB (depuración / instalar)

1. **Ajustes → Acerca del teléfono** → pulsa **Número de compilación** varias veces hasta activar opciones de desarrollador.
2. **Ajustes → Sistema → Opciones para desarrolladores**:
   - Activa **Opciones para desarrolladores**
   - Activa **Depuración USB**
   - Si aparece, activa también instalación/depuración USB de seguridad (Motorola)
3. Conecta el móvil al PC con cable de datos USB-C.
4. En el teléfono acepta **¿Permitir depuración USB?** (marca “siempre desde este ordenador”).
5. En el PC:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Modo avión (permiso especial)

Android no deja a una app normal cambiar el modo avión en silencio. En un prototipo personal:

```bash
adb shell pm grant com.marianoalda.liveperf android.permission.WRITE_SECURE_SETTINGS
```

Comportamiento:

- Al **entrar**, guarda si el avión estaba ON/OFF; si estaba OFF, lo activa.
- Al **salir** (cerrar la Activity), restaura el estado de entrada.
- El botón **Cambiar modo avión** permite conmutarlo a mano mientras la app está abierta.

Sin el `pm grant`, verás el aviso de permiso y el resto de la app (MIDI) sigue funcionando.

## Uso

- **Modo edición** (padre implícito): **Grupos** → setlist (ya ligada al grupo) → canciones (ya ligadas a la setlist) → instrumentos. Al crear setlist/canción se abre el nivel inferior para completarla de una vez. **Canciones** es un atajo posterior para retocar BPM/tonalidad/instrumentos sin redefinir el árbol.
- **Importar / exportar JSON** (filesystem del móvil): en edición → **Importar JSON** (reemplaza la biblioteca) o **Exportar JSON** (estado actual). Los instrumentos van por **nombre** (ver `v3sound-grandpianopro.json`). Ejemplo: [`examples/library-example.json`](examples/library-example.json).
- **Modo directo**: elige grupo → setlist → canción. Instrumentos: un toque (activo más brillante). Siguiente/atrás de canción: **doble toque** (botón grande / pequeño) para evitar fallos en escenario. Al entrar en una canción se envía el primer instrumento.

### Formato JSON de importación

```json
{
  "groups": [
    {
      "name": "Grupo demo",
      "setlists": [
        {
          "name": "Setlist demo",
          "songs": [
            {
              "title": "Ejemplo",
              "bpm": 120,
              "key": "C",
              "instrumentNotes": "Intro piano, estribillo órgano",
              "instruments": [
                "Grand Piano BÖS 270 Classic",
                "Organ 776555678 slow"
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

Catálogo de referencia (nombre ↔ program change): [`v3sound-grandpianopro.json`](v3sound-grandpianopro.json) (también embebido en `app/src/main/assets/`).

## MIDI en directo

1. Conecta el **M-Audio Uno** (u otra interfaz class-compliant) al g75 con **OTG**.
2. Abre **liveperf** (landscape, pantalla encendida).
3. En modo directo, toca un instrumento → Program Change en **canal 1**.

Topología validada con V3: teclado → USB Host del V3; Uno → DIN IN del V3. Sólo se usan dispositivos MIDI `TYPE_USB` (se ignoran puertos virtuales de apps).

## Repo

https://github.com/marianoalda/liveperf
