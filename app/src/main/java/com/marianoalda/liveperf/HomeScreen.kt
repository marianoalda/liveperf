package com.marianoalda.liveperf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF0E1A22)
private val Panel = Color(0xFF1B3A4B)
private val Accent = Color(0xFFE8C547)
private val Soft = Color(0xFFD7E6EF)

@Composable
fun LiveperfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Navy,
            surface = Panel,
            primary = Accent,
            onPrimary = Navy,
            onBackground = Soft,
            onSurface = Soft,
        ),
        content = content,
    )
}

@Composable
fun HomeScreen(
    airplaneOn: Boolean,
    midiConnected: Boolean,
    midiName: String?,
    message: String,
    buildLabel: String,
    onOrgan: () -> Unit,
    onPiano: () -> Unit,
    onToggleAirplane: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = stringResource(R.string.title),
                color = Accent,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (midiConnected) {
                    stringResource(R.string.midi_ready, midiName ?: "USB")
                } else {
                    stringResource(R.string.midi_missing)
                },
                color = Soft,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = if (airplaneOn) {
                    stringResource(R.string.airplane_on)
                } else {
                    stringResource(R.string.airplane_off)
                },
                color = Soft,
                fontSize = 16.sp,
            )
            if (message.isNotBlank()) {
                Text(
                    text = message,
                    color = Accent,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BigActionButton(
                label = stringResource(R.string.btn_organ),
                buttonModifier = Modifier.weight(1f),
                onClick = onOrgan,
            )
            BigActionButton(
                label = stringResource(R.string.btn_piano),
                buttonModifier = Modifier.weight(1f),
                onClick = onPiano,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onToggleAirplane) {
                Text(stringResource(R.string.airplane_toggle))
            }
            Text(
                text = buildLabel,
                color = Soft.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun BigActionButton(
    label: String,
    buttonModifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = buttonModifier.height(120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Panel,
            contentColor = Accent,
        ),
    ) {
        Text(text = label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}
