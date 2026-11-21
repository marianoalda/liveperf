# liveperf

Aplicación Android (instalación manual, **no** Play Store) para actuaciones en directo con **V3Sound Grand Piano Pro**: repertorio jerárquico, etiquetas de botón, Program Change MIDI por USB (canal 1) y un **editor web local** en Python para mantener los JSON en el PC.

**Repositorio público:** https://github.com/marianoalda/liveperf

**Manual de usuario (español):** [doc/manual-usuario.md](doc/manual-usuario.md)

---

## Documentación

| Documento | Contenido |
|-----------|-----------|
| **[doc/manual-usuario.md](doc/manual-usuario.md)** | Cómo usar la app y el editor web en escenario y en casa |
| Este README | Visión del proyecto, build, estructura y formato JSON |
| [`v3sound-grandpianopro.json`](v3sound-grandpianopro.json) | Catálogo V3 (nombre ↔ program change, bank MSB 0) |

---

## Qué incluye

- **App Android** (`app/`): modos edición y directo, landscape, keep-awake, modo avión opcional, MIDI USB.
- **Editor web** (`webedit/`): misma jerarquía y gestión de etiquetas en el PC.
- **Bibliotecas JSON** (`libraries/`): repertorios versionados; el móvil puede **descargarlos desde GitHub**.
- **Etiquetas propias**: texto corto del botón; sin ellas se muestra `(?)` + nombre V3.
- **MIDI RESET** en directo (doble toque): silencia notas colgadas (CC 120/123). Un toque fuera del botón cancela la confirmación (igual que Atrás / SIGUIENTE / Eliminar etiqueta).

---

## Estructura del repositorio

```
liveperf/
├── README.md                 ← este fichero
├── doc/
│   └── manual-usuario.md     ← manual de uso en español
├── app/                      ← código Android (Kotlin / Compose)
├── webedit/                  ← servidor Python + UI web
│   ├── server.py
│   └── static/
├── libraries/                ← JSON de repertorio (PC ↔ móvil vía GitHub)
├── examples/                 ← ejemplo mínimo
└── v3sound-grandpianopro.json
```

---

## Requisitos

### App Android

- JDK 17+
- Android SDK (`platforms/android-35`, build-tools, platform-tools)
- Teléfono con USB OTG + MIDI (probado: Motorola **moto g75 5G**)

```bash
export ANDROID_HOME=/ruta/a/Android/Sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

### Editor web

- Python 3 (stdlib; sin dependencias extra)

---

## Compilar e instalar la app

```bash
./gradlew assembleDebug
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

### Depuración USB (resumen)

1. Activar opciones de desarrollador y **Depuración USB**.
2. Conectar cable de datos y aceptar el diálogo en el teléfono.
3. `adb devices` debe listar el aparato como `device`.

Detalle de uso diario: [manual de usuario](doc/manual-usuario.md).

### Modo avión (opcional)

```bash
adb shell pm grant com.marianoalda.liveperf android.permission.WRITE_SECURE_SETTINGS
```

La app recuerda el estado al entrar, puede activar el avión y lo restaura al salir. Sin el permiso, MIDI y repertorio siguen funcionando.

---

## Editor web local

```bash
python3 webedit/server.py
# → http://127.0.0.1:8765/
```

- Pestaña **Jerarquía**: grupos → setlists → canciones → instrumentos.
- Pestaña **Etiquetas**: crear, renombrar, remapear V3, eliminar.
- Los ficheros se escriben en [`libraries/`](libraries/). Guarda con el botón o **Ctrl+S**, luego `git push` para publicarlos.

Uso paso a paso: [manual §8](doc/manual-usuario.md#8-editor-web-pc).

---

## Flujo de trabajo típico

1. Editar en el PC con `webedit` → guardar en `libraries/`.
2. Commit y push a GitHub.
3. En el móvil: **Edición → Descargar desde GitHub** (confirma sobrescritura).
4. **Modo directo** con OTG + interfaz USB-MIDI.

Topología MIDI validada: teclado → USB Host del V3; Uno → DIN IN del V3. La app solo usa dispositivos MIDI `TYPE_USB`.

---

## Formato JSON (resumen)

```json
{
  "groups": [
    {
      "name": "Grupo",
      "setlists": [
        {
          "name": "Setlist",
          "songs": [
            {
              "title": "Canción",
              "bpm": 120,
              "key": "C",
              "instrumentNotes": "…",
              "instruments": [
                { "name": "Grand Piano BÖS 270 Classic", "label": "Piano" }
              ]
            }
          ]
        }
      ]
    }
  ],
  "instrumentLabels": {
    "Grand Piano BÖS 270 Classic": "Piano"
  }
}
```

- Instrumentos por **nombre** del catálogo V3 (también se acepta número de programa).
- `label` / `instrumentLabels`: texto de los botones en directo.
- Ejemplo: [`examples/library-example.json`](examples/library-example.json)
- Raw en GitHub: `https://raw.githubusercontent.com/marianoalda/liveperf/main/libraries/<nombre>.json`

Explicación de campos y escenarios: [manual §9](doc/manual-usuario.md#9-formato-del-fichero-json).

---

## Funciones principales (móvil)

| Área | Comportamiento |
|------|----------------|
| Edición · jerarquía | Crear grupo → setlist → canción → instrumentos (buscador V3) |
| Edición · etiquetas | Crear / editar literal / remapear V3 / eliminar (2×; toque fuera cancela) |
| Directo · instrumentos | 1 toque = PC; mantener = editar etiqueta |
| Directo · navegación | SIGUIENTE / Atrás / MIDI RESET = **doble toque** (~1,2 s; toque fuera cancela) |
| GitHub / import / export | Con aviso al sobrescribir la biblioteca local |

Guía completa: **[doc/manual-usuario.md](doc/manual-usuario.md)**.
