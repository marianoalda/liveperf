package com.marianoalda.liveperf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Panel, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Soft, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = Soft.copy(alpha = 0.7f), fontSize = 13.sp)
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
