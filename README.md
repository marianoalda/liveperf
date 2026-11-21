# liveperf

Aplicación Android (instalación manual, no Play Store) para actuaciones en directo con módulo **V3Sound Grand Piano Pro**: setlists, notas de canción y cambios de programa MIDI por USB (canal 1).

Incluye también un **editor web local** en Python para mantener la biblioteca JSON en el PC y publicarla en este repositorio (carpeta `libraries/`), de donde el móvil puede descargarla.

**Repositorio:** https://github.com/marianoalda/liveperf (público)

---

## Contenido del proyecto

| Ruta | Descripción |
|------|-------------|
| `app/` | App Android (Kotlin + Jetpack Compose) |
| `webedit/` | Servidor web local de edición |
| `libraries/` | Ficheros JSON de repertorio (editables en PC; descargables en el móvil) |
| `examples/` | Ejemplo mínimo de biblioteca |
| `v3sound-grandpianopro.json` | Catálogo V3 (nombre ↔ program change, bank MSB 0) |

---

## App Android

### Qué hace

- **Jerarquía:** grupo → setlist → canción → instrumentos V3.
- **Modo edición:** define el árbol; las setlists/canciones se crean ya ligadas al padre.
- **Modo directo:** elige grupo y setlist; avanza/retrocede canciones; cambia instrumentos con un toque.
- **MIDI USB:** Program Change en canal 1 hacia la interfaz (p. ej. M-Audio Uno → DIN IN del V3).
- **Etiqueta propia:** texto corto del botón en directo. Sin ella se muestra `(?)` + nombre V3. Mantén pulsado el instrumento para editarla.
- **Descarga GitHub / importar / exportar JSON** con aviso si se va a sobrescribir la biblioteca local (`library.json` interno de la app).

### Requisitos

- JDK 17+
- Android SDK (`platforms/android-35`, `build-tools`, `platform-tools`)
- Teléfono con USB OTG + MIDI (probado: Motorola **moto g75 5G**)

```bash
export ANDROID_HOME=/home/mjam/Android/Sdk   # ajusta si hace falta
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

### Compilar e instalar

```bash
./gradlew assembleDebug
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

#### Depuración USB (g75)

1. **Ajustes → Acerca del teléfono** → pulsa **Número de compilación** varias veces.
2. **Opciones para desarrolladores** → **Depuración USB**.
3. Conecta el cable de datos y acepta el diálogo en el teléfono.

### Modo avión (permiso especial)

```bash
adb shell pm grant com.marianoalda.liveperf android.permission.WRITE_SECURE_SETTINGS
```

- Al entrar: recuerda el estado; si el avión estaba OFF, lo activa.
- Al salir: restaura el estado de entrada.
- Botón en pantalla para conmutarlo a mano.

Sin el permiso, el resto de la app (MIDI, setlists) sigue funcionando.

### Uso en directo

1. Conecta la interfaz MIDI class-compliant al teléfono con **OTG**.
2. Abre **liveperf** (landscape, pantalla encendida).
3. **Modo directo** → grupo → setlist → canción.
4. Instrumentos: **un toque** = Program Change. **Mantener** = editar etiqueta.
5. **SIGUIENTE** (grande) / **Atrás** (pequeño): **doble toque** (~1,2 s) para evitar pulsaciones accidentales.
6. Al entrar en una canción se envía el primer instrumento.

Topología validada: teclado → USB Host del V3; Uno → DIN IN del V3. Solo se usan dispositivos MIDI `TYPE_USB` (se ignoran puertos virtuales de apps).

### Descargar repertorio desde GitHub

En el móvil (con Internet):

1. **Modo edición → Descargar desde GitHub**
2. Elige un JSON de `libraries/` (p. ej. `Repertorio-Blandengues-20261121-a.json`)
3. Confirma la sobrescritura de la biblioteca local
4. Queda guardado como `library.json` interno y listo para **Modo directo**

También puedes **Importar JSON** desde el filesystem del teléfono o **Exportar JSON**.

---

## Editor web local (PC)

Sirve para editar la misma jerarquía con más comodidad en el ordenador. Los ficheros quedan en `libraries/` para poder subirlos a GitHub y que el móvil los descargue.

### Arranque

```bash
python3 webedit/server.py
# → http://127.0.0.1:8765/
```

Opciones: `--host 127.0.0.1` (por defecto), `--port 8765`.

### Uso

1. Crea o abre un JSON en la columna **Ficheros** (se guardan en `libraries/`).
2. Edita **Grupos → setlists → canciones → instrumentos**.
3. Asigna la **etiqueta propia** (texto del botón en el móvil).
4. **Guardar** o **Ctrl+S**.
5. Haz commit/push del JSON en `libraries/` para publicarlo.

Catálogo V3 usado por el editor: `v3sound-grandpianopro.json` en la raíz del repo.

---

## Formato JSON de la biblioteca

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
                { "name": "Grand Piano BÖS 270 Classic", "label": "Piano" },
                { "name": "Organ 776555678 slow", "label": "Órgano" }
              ]
            }
          ]
        }
      ]
    }
  ],
  "instrumentLabels": {
    "Grand Piano BÖS 270 Classic": "Piano",
    "Organ 776555678 slow": "Órgano"
  }
}
```

- Los instrumentos se identifican por **nombre** del catálogo V3 (o por número de programa).
- `label` / `instrumentLabels`: texto del botón en directo.
- Ejemplo: [`examples/library-example.json`](examples/library-example.json)
- Catálogo: [`v3sound-grandpianopro.json`](v3sound-grandpianopro.json) (también en `app/src/main/assets/`)

Descarga directa (raw) de un repertorio publicado:

`https://raw.githubusercontent.com/marianoalda/liveperf/main/libraries/<nombre>.json`

---

## Flujo recomendado

1. Editar en el **PC** con `webedit` → guardar en `libraries/`.
2. **Commit y push** a GitHub.
3. En el **móvil**: Descargar desde GitHub (o importar el fichero).
4. Actuar en **Modo directo** con MIDI USB.
