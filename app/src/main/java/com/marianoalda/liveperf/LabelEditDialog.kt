package com.marianoalda.liveperf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LabelEditDialog(
    program: Int,
    currentLabel: String,
    v3Name: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(program) { mutableStateOf(currentLabel) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Etiqueta propia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DarkField(
                    value = text,
                    onValueChange = { text = it },
                    label = "Texto del botón (vacío = sin etiqueta)",
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "V3: $v3Name · PC $program",
                    color = Soft.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
