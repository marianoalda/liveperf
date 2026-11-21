package com.marianoalda.liveperf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, color = Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (onBack != null) {
                OutlinedButton(onClick = onBack) { Text("Atrás") }
            }
        }
        content()
    }
}

@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val vPad = if (compact) 2.dp else 4.dp
    val innerPad = if (compact) 8.dp else 12.dp
    val titleSize = if (compact) 16.sp else 18.sp
    val subtitleSize = if (compact) 12.sp else 13.sp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = vPad)
            .background(Panel, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(innerPad),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Soft, fontSize = titleSize, fontWeight = FontWeight.SemiBold)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = Soft.copy(alpha = 0.7f), fontSize = subtitleSize)
            }
        }
        if (onDelete != null) {
            OutlinedButton(onClick = onDelete) { Text("Borrar", color = Danger) }
        }
    }
}

@Composable
fun DarkField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Soft,
            unfocusedTextColor = Soft,
            focusedBorderColor = Accent,
            unfocusedBorderColor = Soft.copy(alpha = 0.4f),
            focusedLabelColor = Accent,
            unfocusedLabelColor = Soft.copy(alpha = 0.7f),
            cursorColor = Accent,
        ),
    )
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Accent),
    ) { Text(text) }
}

/** Shared V3 catalog picker with case-insensitive name filter. */
@Composable
fun V3InstrumentPicker(
    excludePrograms: Set<Int> = emptySet(),
    listHeight: androidx.compose.ui.unit.Dp = 260.dp,
    onPick: (V3Sound) -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    val q = filter.trim()
    val matches = remember(q, excludePrograms) {
        V3SoundCatalog.sounds.filter { sound ->
            sound.program !in excludePrograms && (
                q.isEmpty() ||
                    sound.name.contains(q, ignoreCase = true) ||
                    sound.program.toString() == q
                )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DarkField(
            filter,
            { filter = it },
            "Filtrar por nombre (sin distinguir mayúsculas)",
        )
        Text(
            if (q.isEmpty()) {
                "${matches.size} sonidos · desplaza la lista"
            } else {
                "${matches.size} coincidencias"
            },
            color = Soft.copy(alpha = 0.65f),
            fontSize = 12.sp,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(listHeight),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(matches, key = { it.program }) { sound ->
                Text(
                    text = "${sound.name}  ·  PC ${sound.program}",
                    color = Soft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PanelDim, RoundedCornerShape(6.dp))
                        .clickable { onPick(sound) }
                        .padding(10.dp),
                )
            }
        }
    }
}
