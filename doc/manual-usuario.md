# Manual de usuario — liveperf

Guía en español para usar **liveperf** en el teléfono (actuación) y el **editor web** en el PC (preparación del repertorio).

Repositorio: https://github.com/marianoalda/liveperf

---

## 1. Qué es liveperf

liveperf ayuda a llevar un repertorio en directo con un módulo **V3Sound Grand Piano Pro**:

- Organiza **grupos → setlists → canciones → instrumentos**.
- En escenario muestra notas (BPM, tonalidad, cambios) y botones para cambiar de sonido por MIDI USB.
- Las etiquetas cortas de los botones (p. ej. «Piano», «Órgano») se mantienen aparte del nombre largo del catálogo V3.

No está en Play Store: se instala por USB (APK debug).

---

## 2. Conceptos

| Concepto | Significado |
|----------|-------------|
| **Grupo** | Nivel superior (banda, gira, proyecto…). Contiene setlists. |
| **Setlist** | Orden de canciones de un bolo o ensayo. |
| **Canción** | Título, BPM, tonalidad, notas libres e instrumentos. |
| **Instrumento V3** | Sonido del catálogo Grand Piano Pro (nombre + program change). |
| **Etiqueta propia** | Texto corto del botón en directo. Sin ella se muestra `(?)` + nombre V3. |
| **Canal MIDI** | Siempre **canal 1** para Program Change. |

Jerarquía al crear desde la app o el editor web: el hijo se crea **ya ligado** al padre (no hace falta asignar el padre a mano).

---

## 3. Flujo recomendado

1. Preparar el repertorio en el **PC** con el editor web (`webedit`).
2. Guardar el JSON en `libraries/` y subirlo a GitHub.
3. En el **móvil**: **Descargar desde GitHub** (o importar el fichero).
4. En ensayo/bolo: **Modo directo** + interfaz MIDI USB.

También puedes editar todo en el móvil; el PC suele ser más cómodo para setlists largas y etiquetas.

---

## 4. App Android — primer uso

### 4.1 Instalar

Necesitas depuración USB y el APK compilado (ver [README](../README.md)).

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4.2 Permiso de modo avión (opcional)

```bash
adb shell pm grant com.marianoalda.liveperf android.permission.WRITE_SECURE_SETTINGS
```

Sin este permiso, MIDI y setlists funcionan; solo falla el control automático del modo avión.

### 4.3 Conexión MIDI

Topología validada:

- Teclado → **USB Host** del V3.
- Interfaz USB-MIDI (p. ej. M-Audio Uno) → **DIN IN** del V3.
- Teléfono → OTG → misma interfaz (o otra class-compliant).

La app solo usa dispositivos MIDI **USB** (ignora puertos virtuales de otras apps).

Ejemplo de montaje completo (Keystation + V3 + interfaz + teléfono) y nota sobre botones del teclado / MIDI Thru: [ejemplo de configuración](ejemplo-configuracion.md).

Pantalla en **horizontal**, permanece encendida mientras la app está abierta.

---

## 5. Pantalla de inicio

- **Modo edición** — preparar o retocar repertorio, descargar JSON, etiquetas.
- **Modo directo** — actuar.
- Indicadores de **MIDI USB** y **modo avión**.
- **Cambiar modo avión** (si hay permiso).

---

## 6. Modo edición (móvil)

### 6.1 Hub de edición

| Entrada | Para qué |
|---------|----------|
| **Grupos** | Árbol completo: grupo → setlist → canción → instrumentos. |
| **Canciones** | Atajo para retocar canciones ya creadas. |
| **Etiquetas** | Crear / renombrar / remapear / eliminar etiquetas de botón. |
| **Descargar desde GitHub** | Traer un JSON de `libraries/` del repo. |
| **Importar JSON** | Desde un fichero del teléfono. |
| **Exportar JSON** | Guardar el estado actual a un fichero. |

Antes de **sobrescribir** la biblioteca local, la app pide confirmación. La primera vez que guardes cambios tras tener un `library.json` local también avisa.

### 6.2 Grupos, setlists y canciones

1. **Grupos** → crea un grupo → ábrelo.
2. Crea una **setlist** dentro del grupo (se abre sola para completarla).
3. Crea **canciones** en la setlist (al crear se abre el detalle).
4. En la canción: título, BPM, tonalidad, notas de cambios de instrumento.
5. Añade instrumentos con el **buscador** (filtro por nombre, sin distinguir mayúsculas).

Orden de canciones = orden en directo. Usa ↑ ↓ para reordenar; **Quitar** las saca de la setlist (no borra la canción del todo si solo la quitas de ahí).

### 6.3 Etiquetas en una canción

- En la lista de instrumentos de la canción: **mantener pulsado** → diálogo de etiqueta propia.
- El botón en directo mostrará esa etiqueta + `PC n`.
- Sin etiqueta: `(?)` + nombre V3 + `PC n`.

### 6.4 Modo Etiquetas (aparte de la jerarquía)

**Edición → Etiquetas**:

- Lista todas las etiquetas existentes (literal, PC, nombre V3).
- **Crear etiqueta**: escribe el literal → elige instrumento V3 (mismo buscador). Un toque basta.
- **Guardar literal**: cambia solo el texto del botón.
- **Cambiar instrumento V3**: remapea esa etiqueta a otro sonido y **actualiza todas las canciones** que usaban el programa anterior.
- **Eliminar (2×)**: quita solo la etiqueta; el instrumento **sigue** en las canciones. Hay que pulsar dos veces (~1,2 s). Si pulsas **fuera** del botón Eliminar tras el primer toque, se cancela y hay que empezar de nuevo.

Crear/editar aquí usa el mismo mapa `instrumentLabels` que al asignar etiqueta en una canción.

### 6.5 Descargar desde GitHub

1. Teléfono con Internet.
2. **Descargar desde GitHub** → lista de JSON en `libraries/`.
3. Elige uno (p. ej. un repertorio publicado).
4. Confirma sobrescritura.
5. Queda como biblioteca activa (`library.json` interno) para **Modo directo**.

---

## 7. Modo directo (móvil)

1. Elige **grupo** → **setlist**.
2. Entras en la primera canción; se envía el **primer instrumento** por MIDI.

### 7.1 Pantalla de canción

- Notas: BPM, tonalidad, texto de cambios de instrumento.
- Botones de instrumento: **un toque** = Program Change (canal 1). El activo se ve más brillante. Muestran **etiqueta** (o `(?)` + V3) y **PC**.
- **Mantener** un instrumento = editar etiqueta (también en directo).

### 7.2 Navegación y MIDI reset (doble toque)

| Botón | Tamaño | Acción |
|-------|--------|--------|
| **SIGUIENTE** | Grande | Siguiente canción |
| **Atrás** | Pequeño | Canción anterior |
| **MIDI RESET** | Pequeño | Silencia notas colgadas |

Los tres requieren **doble toque** en menos de ~1,2 s (el primero arma; el segundo ejecuta). Así se evitan pulsaciones accidentales.

Si, tras el primer toque, pulsas **en cualquier otra zona** de la pantalla (u otro control), se **cancela** la confirmación y hay que volver a armar el botón. Pulsar otro botón de doble toque arma ese otro (y cancela el anterior).

**MIDI RESET** envía *All Sound Off* (CC 120) y *All Notes Off* (CC 123) en todos los canales MIDI.

---

## 8. Editor web (PC)

### 8.1 Arranque

```bash
python3 webedit/server.py
```

Abre http://127.0.0.1:8765/

Los JSON se guardan en [`libraries/`](../libraries/) del repositorio.

### 8.2 Pestaña Jerarquía

Igual que en el móvil: grupos → setlists → canciones → instrumentos, con filtro de catálogo V3 y campo de etiqueta propia por instrumento.

**Guardar** o **Ctrl+S**.

### 8.3 Pestaña Etiquetas

- **+ Crear etiqueta**: literal + elegir V3 (buscador).
- Editar literal, cambiar instrumento V3 (remap global), eliminar (con confirmación).
- Misma lógica y datos que en el teléfono.

### 8.4 Publicar para el móvil

```bash
git add libraries/
git commit -m "…"
git push
```

Luego, en el móvil: **Descargar desde GitHub**.

---

## 9. Formato del fichero JSON

Estructura habitual:

```json
{
  "groups": [
    {
      "name": "Mi grupo",
      "setlists": [
        {
          "name": "Bolo",
          "songs": [
            {
              "title": "Canción",
              "bpm": 120,
              "key": "C",
              "instrumentNotes": "Intro piano…",
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

- Instrumentos por **nombre** del catálogo (también se acepta número de programa).
- `instrumentLabels` / `label`: texto de los botones.
- Catálogo de referencia: [`v3sound-grandpianopro.json`](../v3sound-grandpianopro.json).
- Ejemplo mínimo: [`examples/library-example.json`](../examples/library-example.json).

URL raw de un repertorio en GitHub:

`https://raw.githubusercontent.com/marianoalda/liveperf/main/libraries/<nombre>.json`

---

## 10. Consejos de escenario

- Comprueba MIDI en la pantalla de inicio **antes** de empezar.
- Define etiquetas cortas y legibles a distancia.
- Usa **MIDI RESET** si queda una nota colgada tras un cambio brusco.
- Doble toque en navegación / MIDI RESET / Eliminar: si solo pulsas una vez, espera el aviso «Pulsa otra vez…». Un toque fuera cancela.
- Tras editar en el PC, vuelve a **descargar** en el móvil para no actuar con una copia vieja.

---

## 11. Problemas frecuentes

| Síntoma | Qué revisar |
|---------|-------------|
| «MIDI USB: no conectado» | OTG, cable de datos, interfaz class-compliant, LED de la interfaz. |
| No cambia el sonido | Canal 1, DIN IN del V3, que el teclado no pise el mismo puerto. |
| `(?)` en el botón | Falta etiqueta propia: créala en Etiquetas o mantén pulsado el instrumento. |
| Descarga GitHub falla | Internet en el teléfono; repo público; nombre del JSON en `libraries/`. |
| Modo avión no cambia | Falta `pm grant … WRITE_SECURE_SETTINGS`. |

---

## 12. Más documentación técnica

Instalación, build, SDK y estructura del repo: [README del proyecto](../README.md).
