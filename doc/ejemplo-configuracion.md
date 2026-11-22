# Ejemplo de configuración — liveperf

Ejemplo real de montaje usado con **liveperf** y un módulo **V3Sound Grand Piano Pro**. Sirve de referencia; otros teclados e interfaces class-compliant pueden funcionar con la misma idea.

---

## Equipo

| Pieza | Uso |
|-------|-----|
| **V3Sound Grand Piano Pro** | Expansor de sonido (receptor MIDI). |
| **Keystation** (u otro teclado USB-MIDI) | Toca el V3 por el puerto **USB Host**. |
| **Interfaz USB-MIDI** (p. ej. M-Audio Uno) | Lleva Program Change y otros mensajes desde el teléfono al **MIDI IN** (DIN) del V3. |
| **Teléfono Android** (p. ej. moto g75 5G) | Ejecuta liveperf; OTG + cable de datos hacia la interfaz. |

---

## Topología cableada

```
Keystation ──USB──► V3Sound (USB Host)
                         ▲
Teléfono ──OTG──► USB-MIDI ──DIN Out──► V3Sound (MIDI IN)
```

- El teclado alimenta y controla el módulo por **USB Host** (hasta 500 mA según V3; sin hub USB).
- liveperf solo usa dispositivos MIDI **tipo USB**: abre la interfaz y envía **Program Change** (canal 1), **MIDI RESET** (CC 120/123), etc., hacia el **MIDI IN** del V3.
- USB Host y MIDI IN del V3 **pueden usarse a la vez** (teclado + teléfono).

Pantalla del teléfono en **horizontal**; la app mantiene la pantalla encendida.

Detalle de uso de la app: [manual de usuario](manual-usuario.md).

---

## Qué hace liveperf en este montaje

En **modo directo**:

1. Eliges grupo → setlist → canción.
2. Al entrar en una canción se envía el primer instrumento (PC).
3. Un toque en un botón de instrumento = otro Program Change.
4. **SIGUIENTE** / **Atrás** / **MIDI RESET** = doble toque en la pantalla del teléfono.

La navegación entre canciones y el reset MIDI se controlan **desde la app**, no desde el teclado.

---

## Botones del teclado MIDI (avance / retroceso, etc.)

Algunos teclados permiten asignar botones o pads a mensajes MIDI (p. ej. CC o notas) que podrían usarse para **canción anterior / siguiente** u otras acciones de liveperf.

Para que el **teléfono** recibiera esos mensajes en *esta* topología (teclado solo en USB Host del V3), haría falta que el V3 **reenviara** al **MIDI Thru** (DIN) lo entrado por USB Host, y cablear:

`V3 MIDI Thru → MIDI In de la interfaz USB-MIDI → teléfono`

**Hoy el firmware del Grand Piano Pro no hace ese reenvío.** El conector Thru se comporta como Thru clásico: copia del **MIDI IN** DIN, no del USB Host. Se comprobó en práctica: con Keystation en USB Host y Thru → In del Uno hacia un PC, al tocar el teclado no llega ningún evento MIDI al ordenador.

Ese reenvío USB Host → Thru **no está prohibido por la norma MIDI**. La especificación MIDI 1.0 define Thru como copia del jack MIDI In (DIN); la clase USB-MIDI permite además *merge* de varias fuentes hacia un OUT. Una implementación de firmware que mezclara el USB Host hacia Thru/Out sería **compatible con la norma**; simplemente no es lo que el producto hace ahora. Sin ese cambio (u otra ruta de cableado, p. ej. teclado también hacia la interfaz o un merge externo), liveperf no puede usar los botones del teclado para avanzar/retroceder canciones en este montaje.

---

## Resumen

| Objetivo | Estado en este ejemplo |
|----------|-------------------------|
| Tocar el V3 con el Keystation (USB Host) | Sí |
| Cambiar sonidos / reset desde liveperf (teléfono → USB-MIDI → DIN IN) | Sí |
| Usar botones del teclado para anterior/siguiente canción vía Thru del V3 | No, falta firmware (o cableado distinto); un cambio de firmware sería compatible con MIDI |

---

## Véase también

- [Manual de usuario](manual-usuario.md)
- [README del proyecto](../README.md)
- Catálogo V3: [`v3sound-grandpianopro.json`](../v3sound-grandpianopro.json)
