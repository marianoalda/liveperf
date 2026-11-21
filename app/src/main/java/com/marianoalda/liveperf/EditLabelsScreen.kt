package com.marianoalda.liveperf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EditLabelsScreen(
    library: LiveperfLibrary,
    onBack: () -> Unit,
    onSave: (LiveperfLibrary) -> Unit,
) {
    val entries = library.labelEntries()
    var selectedV3 by remember { mutableStateOf<String?>(null) }
    var labelDraft by remember { mutableStateOf("") }
    var pickingFor by remember { mutableStateOf<String?>(null) }
    var deleteArmed by remember { mutableStateOf(false) }
    var deleteArmedAt by remember { mutableLongStateOf(0L) }
    var deleteHint by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var createLabel by remember { mutableStateOf("") }
    var createPicking by remember { mutableStateOf(false) }

    fun clearDeleteArm() {
        deleteArmed = false
        deleteHint = ""
    }

    fun select(entry: LiveperfLibrary.LabelEntry) {
        selectedV3 = entry.v3Name
        labelDraft = entry.label
        pickingFor = null
        clearDeleteArm()
        creating = false
        createPicking = false
    }

    fun armOrDelete(v3Name: String) {
        val now = System.currentTimeMillis()
        if (deleteArmed && now - deleteArmedAt < 1200) {
            onSave(library.withInstrumentLabel(v3Name, ""))
            selectedV3 = null
            labelDraft = ""
            pickingFor = null
            clearDeleteArm()
        } else {
            deleteArmed = true
            deleteArmedAt = now
            deleteHint = "Pulsa otra vez ELIMINAR"
        }
    }

    val selected = entries.firstOrNull { it.v3Name == selectedV3 }
    val clearOutsideInteraction = remember { MutableInteractionSource() }

    ScreenScaffold(title = "Edición de etiquetas", onBack = onBack, content = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = clearOutsideInteraction,
                    indication = null,
                    enabled = deleteArmed,
                ) { clearDeleteArm() }
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Crea etiquetas de botón o edítalas / remapéalas a otro sonido V3. " +
                    "Compatible con asignar etiqueta al añadir instrumento en una canción.",
                color = Soft.copy(alpha = 0.75f),
                fontSize = 13.sp,
            )

            PrimaryButton(if (creating) "Cancelar creación" else "Crear etiqueta") {
                creating = !creating
                createPicking = false
                createLabel = ""
                if (creating) {
                    selectedV3 = null
                    pickingFor = null
                    clearDeleteArm()
                }
            }

            if (creating) {
                Text("Nueva etiqueta", color = Accent, fontWeight = FontWeight.SemiBold)
                DarkField(createLabel, { createLabel = it }, "Literal de la etiqueta (botón)")
                Text(
                    "Elige el instrumento V3 asociado. Si ya tenía etiqueta, se sustituye.",
                    color = Soft.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                )
                if (!createPicking) {
                    PrimaryButton("Elegir instrumento V3") {
                        if (createLabel.trim().isEmpty()) return@PrimaryButton
                        createPicking = true
                    }
                } else {
                    PrimaryButton("Ocultar catálogo") { createPicking = false }
                    V3InstrumentPicker(
                        excludePrograms = emptySet(),
                        listHeight = 220.dp,
                        onPick = { sound ->
                            val text = createLabel.trim()
                            if (text.isEmpty()) return@V3InstrumentPicker
                            onSave(library.withInstrumentLabel(sound.name, text))
                            selectedV3 = sound.name
                            labelDraft = text
                            creating = false
                            createPicking = false
                            createLabel = ""
                        },
                    )
                }
            }

            if (entries.isEmpty() && !creating) {
                Text(
                    "No hay etiquetas todavía. Usa «Crear etiqueta» o asígnalas en una canción.",
                    color = Soft,
                )
            }

            if (entries.isNotEmpty()) {
            Text("Etiquetas", color = Accent, fontWeight = FontWeight.SemiBold)
            entries.forEach { entry ->
                val active = entry.v3Name == selectedV3
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (active) Panel else PanelDim, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.label, color = Soft, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                            Text(
                                "PC ${entry.program} · ${entry.v3Name}",
                                color = Soft.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                            )
                        }
                        PrimaryButton(if (active) "Editando" else "Editar") {
                            select(entry)
                        }
                    }
                }
            }

            selected?.let { entry ->
                Text("Editar «${entry.label}»", color = Accent, fontWeight = FontWeight.SemiBold)
                DarkField(labelDraft, { labelDraft = it }, "Literal de la etiqueta (botón)")
                Text(
                    "Programa actual: PC ${entry.program}",
                    color = Soft,
                    fontSize = 14.sp,
                )
                Text(
                    "Instrumento V3: ${entry.v3Name}",
                    color = Soft.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PrimaryButton("Guardar literal") {
                        val text = labelDraft.trim()
                        if (text.isEmpty()) return@PrimaryButton
                        onSave(library.withInstrumentLabel(entry.v3Name, text))
                        selectedV3 = entry.v3Name
                        labelDraft = text
                        clearDeleteArm()
                    }
                    PrimaryButton(
                        if (pickingFor == entry.v3Name) "Ocultar catálogo" else "Cambiar instrumento V3",
                    ) {
                        pickingFor = if (pickingFor == entry.v3Name) null else entry.v3Name
                        clearDeleteArm()
                    }
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .height(48.dp)
                            .background(
                                if (deleteArmed) Danger else Panel,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { armOrDelete(entry.v3Name) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Eliminar\n(2×)",
                            color = Soft,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                    }
                }
                if (deleteHint.isNotBlank()) {
                    Text(deleteHint, color = Danger, fontSize = 13.sp)
                }
                Text(
                    "Eliminar quita solo la etiqueta propia; el instrumento sigue en las canciones.",
                    color = Soft.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                )

                if (pickingFor == entry.v3Name) {
                    Text(
                        "Elige el nuevo sonido. Todas las canciones con PC ${entry.program} pasarán al nuevo programa.",
                        color = Soft.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                    )
                    V3InstrumentPicker(
                        excludePrograms = emptySet(),
                        listHeight = 220.dp,
                        onPick = { sound ->
                            val lib = library.remapLabeledInstrument(
                                oldV3Name = entry.v3Name,
                                newProgram = sound.program,
                                newLabel = labelDraft.trim().ifEmpty { entry.label },
                            )
                            onSave(lib)
                            selectedV3 = sound.name
                            labelDraft = lib.customLabel(sound.program) ?: labelDraft
                            pickingFor = null
                        },
                    )
                }
            }
            } // entries.isNotEmpty
        }
    })
}
